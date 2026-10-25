package com.jaroszeek.gymtracker.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.jaroszeek.gymtracker.BuildConfig
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.ApplicationScope
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.domain.update.AppRelease
import com.jaroszeek.gymtracker.domain.update.isUpdateCheckDue
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val checkedAt: Long) : UpdateState

    /** [prompt]: the dialog is open; false after "Później". */
    data class Available(val release: AppRelease, val prompt: Boolean = true) : UpdateState

    data class NeedsInstallPermission(val release: AppRelease) : UpdateState

    /** [progress] is 0..1. */
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState

    /** Verifying the APK and waiting for the system installer. */
    data class Installing(val release: AppRelease) : UpdateState

    data class Failed(val release: AppRelease?, val error: UpdateError) : UpdateState
}

enum class UpdateError {
    Network,

    /** The release's APK is gone from GitHub. */
    NoApk,

    /** The APK is not a newer build of this app signed with its certificate. */
    SignatureMismatch,
    InstallFailed,

    /** The user declined the system's install confirmation. */
    Cancelled,
}

/**
 * The in-app updater (A5.11): GitHub's latest release, checked when the app opens (at most once a day)
 * and on request, compared by versionCode; its APK goes to the cache, must be a newer build of this app
 * signed with the installed certificate, and is installed through PackageInstaller. Off in builds
 * without [BuildConfig.UPDATE_URL] (debug). One job at a time: a call meanwhile does nothing.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val enabled: Boolean = BuildConfig.UPDATE_URL.isNotBlank()

    val installedVersion: String = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val confirmations = Channel<Intent>(Channel.BUFFERED)

    /** The system's install confirmation; the activity starts each one (buffered until collected). */
    val userActions: Flow<Intent> = confirmations.receiveAsFlow()

    val autoCheck: Flow<Boolean> = preferences.updateAutoCheck

    suspend fun setAutoCheck(enabled: Boolean) = preferences.setUpdateAutoCheck(enabled)

    /** Epoch millis of the last successful check. */
    val lastCheck: Flow<Long?> = preferences.updateLastCheck

    @Volatile
    private var job: Job? = null

    /** From the activity's start: with automatic checks on, once a day; a failure is only logged. */
    fun checkIfDue() {
        start {
            // An offered or running update stays as it is
            val current = state.value
            val noRelease = current is UpdateState.Idle || current is UpdateState.UpToDate || (current is UpdateState.Failed && current.release == null)
            if (!noRelease) return@start
            if (preferences.updateAutoCheck.first() && isUpdateCheckDue(preferences.updateLastCheck.first(), clock.millis())) check(manual = false)
        }
    }

    /** From Settings; a failure shows as [UpdateError.Network]. */
    fun checkNow() {
        start { check(manual = true) }
    }

    /** From [UpdateState.Available], [UpdateState.NeedsInstallPermission] or a [UpdateState.Failed] with its release. */
    fun install() {
        val release = when (val current = state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.NeedsInstallPermission -> current.release
            is UpdateState.Failed -> current.release
            else -> null
        } ?: return
        start { installRelease(release) }
    }

    /** "Później": the offer stays, without the dialog. */
    fun postpone() {
        _state.update { current ->
            when (current) {
                is UpdateState.Available -> current.copy(prompt = false)
                is UpdateState.NeedsInstallPermission -> UpdateState.Available(current.release, prompt = false)
                is UpdateState.Failed -> current.release?.let { UpdateState.Available(it, prompt = false) } ?: UpdateState.Idle
                else -> current
            }
        }
    }

    /** The installer's answer for the committed session, through [UpdateInstallReceiver]. */
    internal fun onInstallResult(intent: Intent) {
        val release = (state.value as? UpdateState.Installing)?.release
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation == null) {
                    Log.e(TAG, "Installer asked for confirmation without an intent")
                    _state.value = UpdateState.Failed(release, UpdateError.InstallFailed)
                    return
                }
                // Only the system installer reaches the receiver (not exported, our own PendingIntent), so its nested intent is trusted
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) confirmation.removeLaunchSecurityProtection()
                confirmations.trySend(confirmation)
            }
            // The new version replaces this process; Installing stays until then
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "Update installed")
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = UpdateState.Failed(release, UpdateError.Cancelled)
            else -> {
                Log.e(TAG, "Install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                _state.value = UpdateState.Failed(release, UpdateError.InstallFailed)
            }
        }
    }

    private suspend fun check(manual: Boolean) {
        _state.value = UpdateState.Checking
        _state.value = try {
            val release = latestRelease()
            val now = clock.millis()
            preferences.setUpdateLastCheck(now)
            if (release != null && release.version.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(release) else UpdateState.UpToDate(now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // IOException from the network, SerializationException from an answer that is not a release
            Log.w(TAG, "Update check failed", e)
            if (manual) UpdateState.Failed(null, UpdateError.Network) else UpdateState.Idle
        }
    }

    /** Null when there is nothing to offer: no full release yet (404 while there are only pre-releases) or one without an APK. */
    private fun latestRelease(): AppRelease? {
        val connection = open(BuildConfig.UPDATE_URL).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
        }
        try {
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> GitHubReleaseParser.parse(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub answered $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun installRelease(release: AppRelease) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release)
            return
        }
        val error = try {
            val apk = download(release)
            _state.value = UpdateState.Installing(release)
            if (isNewerBuildOfThisApp(apk)) commit(apk) else UpdateError.SignatureMismatch
        } catch (e: MissingApkException) {
            Log.w(TAG, "The release's APK is gone", e)
            UpdateError.NoApk
        } catch (e: IOException) {
            Log.w(TAG, "Download failed", e)
            UpdateError.Network
        } finally {
            // The installer session keeps its own copy
            File(context.cacheDir, UPDATES_DIR).deleteRecursively()
        }
        if (error != null) _state.value = UpdateState.Failed(release, error)
    }

    /** Into a fresh `cacheDir/updates/`, with progress. */
    private fun download(release: AppRelease): File {
        _state.value = UpdateState.Downloading(release, 0f)
        val dir = File(context.cacheDir, UPDATES_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        val apk = File(dir, "GymTracker-${release.version}.apk")
        val connection = open(release.apkUrl)
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> throw MissingApkException(release.apkUrl)
                else -> throw IOException("Download answered $code")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return apk
    }

    /**
     * Our package, a higher versionCode and the same signing certificates as the installed app.
     * ponytail: no check of the asset's sha256 digest; the APK signature verified here (and again by the installer) covers its content.
     */
    private fun isNewerBuildOfThisApp(apk: File): Boolean {
        val packageManager = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val downloaded = packageManager.getPackageArchiveInfo(apk.path, flags)
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val signers = installed.signingInfo?.apkContentsSigners?.toSet()
        val matches = downloaded != null &&
            downloaded.packageName == context.packageName &&
            downloaded.longVersionCode > installed.longVersionCode &&
            !signers.isNullOrEmpty() &&
            downloaded.signingInfo?.apkContentsSigners?.toSet() == signers
        if (!matches) Log.e(TAG, "Rejected the APK: package ${downloaded?.packageName}, versionCode ${downloaded?.longVersionCode}")
        return matches
    }

    /** Null once the installer has the APK; [onInstallResult] follows. */
    private fun commit(apk: File): UpdateError? {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Silent once this app is the installer of record; the system asks the user otherwise
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            setSize(apk.length())
        }
        var sessionId = NO_SESSION
        return try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(apk.name, 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(resultIntent(sessionId).intentSender)
            }
            null
        } catch (e: Exception) {
            // IOException or SecurityException from the installer service
            Log.e(TAG, "Handing the APK to the installer failed", e)
            if (sessionId != NO_SESSION) installer.abandonSession(sessionId)
            UpdateError.InstallFailed
        }
    }

    /** Explicit and mutable: the installer adds its result to it. */
    private fun resultIntent(sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, UpdateInstallReceiver::class.java),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Synchronized
    private fun start(block: suspend () -> Unit) {
        if (!enabled || job?.isActive == true) return
        job = scope.launch(io) { block() }
    }

    /** Throws [IOException] for a URL that is not HTTP(S), like for any network failure. */
    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as? HttpURLConnection ?: throw IOException("Not an HTTP URL: $url")
        return connection.apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("User-Agent", "GymTracker/${BuildConfig.VERSION_NAME}")
        }
    }

    /** The APK's download answered 404. */
    private class MissingApkException(url: String) : IOException(url)

    private companion object {
        const val TAG = "Updater"
        const val NO_SESSION = -1
        const val UPDATES_DIR = "updates"
        const val GITHUB_API_VERSION = "2022-11-28"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val BUFFER_BYTES = 64 * 1024
    }
}

/** The installer's answers for [UpdateManager]'s sessions; not exported, so only its PendingIntent reaches it. */
class UpdateInstallReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun updateManager(): UpdateManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        EntryPointAccessors.fromApplication(context, Dependencies::class.java).updateManager().onInstallResult(intent)
    }
}
