package com.jaroszeek.gymtracker.data.backup

import android.content.Context
import android.util.Log
import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.data.exercise.ExerciseSeeder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What a backup holds, shown before it replaces the data (A5.10). Dates are epoch millis UTC. */
data class BackupSummary(
    val routines: Int,
    val workouts: Int,
    val measurements: Int,
    val customExercises: Int,
    val firstWorkout: Long?,
    val lastWorkout: Long?,
    /** A GymTracker v1 backup, converted to this app's data. */
    val fromV1: Boolean = false,
    /** What a v1 backup holds that did not come along, or came changed. */
    val skipped: List<Skipped> = emptyList(),
    /** From the age a v1 backup stores, for the profile. */
    val birthYear: Int? = null,
)

/** Why a picked file is not imported. */
enum class Rejection {
    /** Not a GymTracker backup, or too big to be one. */
    NotABackup,

    /** A damaged file, or one whose data does not open. */
    Damaged,

    /** Made by a newer version of the app. */
    Newer,

    /** A GymTracker v1 backup of a version this app cannot read. */
    UnsupportedV1,
}

sealed interface Inspection {
    /** The file is checked and converted; [BackupManager.confirm] makes it the data at the next start. */
    data class Ready(val summary: BackupSummary) : Inspection

    data class Rejected(val reason: Rejection) : Inspection
}

/** Where the backup works: [database] is the live file, [work] a folder outside the Android backup and the cache. */
class BackupFiles(val database: File, val work: File) {
    constructor(context: Context) : this(context.getDatabasePath(GtDatabase.NAME), File(context.noBackupFilesDir, "backup"))

    internal val candidate get() = File(work, "candidate.db")
    internal val trial get() = File(work, "trial.db")
    internal val ready get() = File(work, "ready.db")
    internal val pending get() = File(work, "pending.db")
    internal val previous get() = File(work, "previous")
}

/**
 * Backup of the whole database to one file and its import (A5.10). The export is one consistent
 * snapshot (`VACUUM INTO`, which also folds in the WAL). An import is checked first (SQLite header,
 * integrity, schema version), opened once by Room on a copy, so its migrations and schema check run
 * before anything changes (dry-run), and only after confirmation waits as a file swapped in at the
 * next start of the process ([applyPending]); the replaced database stays as a copy with a timestamp.
 * Health Connect data is in its own database and never in the backup (D-060).
 */
@Singleton
class BackupManager internal constructor(
    private val database: GtDatabase,
    private val files: BackupFiles,
    private val driver: SQLiteDriver,
    private val openDatabase: (File) -> GtDatabase,
    private val seedExercises: suspend (GtDatabase) -> Unit,
    /** The name of a v1 workout saved without one. */
    private val emptyWorkoutName: String,
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(
        database: GtDatabase,
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(
        database = database,
        files = BackupFiles(context),
        driver = AndroidSQLiteDriver(),
        openDatabase = { file -> Room.databaseBuilder<GtDatabase>(context, file.path).setDriver(AndroidSQLiteDriver()).build() },
        seedExercises = { ExerciseSeeder(it, io, context).ensureSeeded() },
        emptyWorkoutName = context.getString(R.string.backup_v1_workout_name),
        io = io,
    )

    /** The whole database, as of now, into [out]. */
    suspend fun export(out: OutputStream) = withContext(io) {
        files.work.mkdirs()
        val snapshot = File(files.work, "export.db").apply { delete() }
        try {
            database.useWriterConnection { connection ->
                connection.usePrepared("VACUUM INTO ?") {
                    it.bindText(1, snapshot.path)
                    it.step()
                }
            }
            snapshot.inputStream().use { it.copyTo(out) }
        } finally {
            snapshot.delete()
        }
    }

    /** Reads [input] into the app, checks it and prepares it; nothing changes until [confirm]. */
    suspend fun inspect(input: InputStream): Inspection = withContext(io) {
        discard()
        deleteWithJournal(files.trial)
        files.work.mkdirs()
        try {
            if (!copyLimited(input, files.candidate)) return@withContext Inspection.Rejected(Rejection.NotABackup)
            if (!hasSqliteHeader(files.candidate)) return@withContext Inspection.Rejected(Rejection.NotABackup)
            val kind = driver.open(files.candidate.path).use { connection -> kindOf(connection) }
            when (kind) {
                Kind.Damaged -> Inspection.Rejected(Rejection.Damaged)
                Kind.Unknown -> Inspection.Rejected(Rejection.NotABackup)
                Kind.Newer -> Inspection.Rejected(Rejection.Newer)
                Kind.OldV1 -> Inspection.Rejected(Rejection.UnsupportedV1)
                Kind.Current -> prepare()
                Kind.V1 -> prepareV1()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backup rejected", e)
            Inspection.Rejected(Rejection.Damaged)
        } finally {
            deleteWithJournal(files.candidate)
            deleteWithJournal(files.trial)
        }
    }

    /** The inspected backup becomes the data at the next start of the process. */
    suspend fun confirm() = withContext(io) {
        if (!files.ready.renameTo(files.pending)) throw IOException("Could not stage the backup")
    }

    /** Drops an inspected backup that was not confirmed. */
    fun discard() = deleteWithJournal(files.ready)

    /** The file as one of this app's databases: Room opens and migrates a copy, then it is saved whole. */
    private suspend fun prepare(): Inspection {
        files.candidate.copyTo(files.trial, overwrite = true)
        return openDatabase(files.trial).useAndClose { Inspection.Ready(saveReady(BackupSummary(0, 0, 0, 0, null, null))) }
    }

    /** A v1 backup converted into a new database with the bundled exercises. */
    private suspend fun prepareV1(): Inspection = openDatabase(files.trial).useAndClose {
        seedExercises(this)
        val result = driver.open(files.candidate.path).use { v1 -> V1Import(v1, this, emptyWorkoutName).run() }
        Inspection.Ready(saveReady(BackupSummary(0, 0, 0, 0, null, null, fromV1 = true, skipped = result.skipped, birthYear = result.birthYear)))
    }

    /** Counts what the (migrated) database holds into [base] and writes it whole to [BackupFiles.ready]. */
    private suspend fun GtDatabase.saveReady(base: BackupSummary): BackupSummary {
        val summary = useReaderConnection { connection ->
            suspend fun count(sql: String) = connection.usePrepared(sql) { it.step(); it.getLong(0).toInt() }
            val (first, last) = connection.usePrepared("SELECT MIN(started_at), MAX(started_at) FROM workouts") {
                it.step()
                (if (it.isNull(0)) null else it.getLong(0)) to (if (it.isNull(1)) null else it.getLong(1))
            }
            base.copy(
                routines = count("SELECT COUNT(*) FROM routines"),
                workouts = count("SELECT COUNT(*) FROM workouts"),
                measurements = count("SELECT COUNT(*) FROM body_measurements"),
                customExercises = count("SELECT COUNT(*) FROM exercises WHERE source = 'custom'"),
                firstWorkout = first,
                lastWorkout = last,
            )
        }
        useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO ?") {
                it.bindText(1, files.ready.path)
                it.step()
            }
        }
        return summary
    }

    private enum class Kind { Current, Newer, V1, OldV1, Unknown, Damaged }

    private fun kindOf(connection: SQLiteConnection): Kind {
        val integrity = connection.prepare("PRAGMA integrity_check").use { if (it.step()) it.getText(0) else null }
        if (integrity != "ok") return Kind.Damaged
        if (connection.prepare("PRAGMA foreign_key_check").use { it.step() }) return Kind.Damaged
        val version = connection.prepare("PRAGMA user_version").use { it.step(); it.getLong(0).toInt() }
        val tables = connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            buildSet { while (it.step()) add(it.getText(0)) }
        }
        return when {
            tables.containsAll(V2_TABLES) -> when {
                version > GtDatabase.VERSION -> Kind.Newer
                version >= 1 -> Kind.Current
                else -> Kind.Unknown
            }
            tables.containsAll(V1Import.TABLES) -> if (version == V1Import.VERSION) Kind.V1 else Kind.OldV1
            else -> Kind.Unknown
        }
    }

    /** Copies at most [MAX_BYTES]; false when the input is longer, which no backup is. */
    private fun copyLimited(input: InputStream, target: File): Boolean {
        target.outputStream().use { out ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return true
                total += read
                if (total > MAX_BYTES) return false
                out.write(buffer, 0, read)
            }
        }
    }

    private fun hasSqliteHeader(file: File): Boolean = file.inputStream().use { input ->
        val header = ByteArray(SQLITE_HEADER.size)
        input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private inline fun <R> GtDatabase.useAndClose(block: GtDatabase.() -> R): R = try {
        block()
    } finally {
        close()
    }

    companion object {
        private const val TAG = "Backup"
        private const val MAX_BYTES = 256L * 1024 * 1024
        private const val KEPT_PREVIOUS = 3
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        /** Tables every version of this app's database has, besides Room's own. */
        private val V2_TABLES = setOf("exercises", "exercise_muscles", "room_master_table")
        private val SUFFIXES = listOf("", "-wal", "-shm", "-journal")
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

        /**
         * Swaps in a confirmed backup; runs at the start of the process, before the database opens.
         * The replaced database (with its WAL) moves to a timestamped copy; the last [KEPT_PREVIOUS] stay.
         */
        fun applyPending(files: BackupFiles, now: Instant): Boolean {
            if (!files.pending.exists()) return false
            files.previous.mkdirs()
            val base = "gymtracker-${STAMP.format(now)}.db"
            SUFFIXES.forEach { suffix ->
                val file = File(files.database.path + suffix)
                if (file.exists() && !file.renameTo(File(files.previous, base + suffix))) Log.w(TAG, "Could not keep ${file.name}")
            }
            files.database.parentFile?.mkdirs()
            val swapped = files.pending.renameTo(files.database)
            if (!swapped) Log.e(TAG, "Could not swap in the backup")
            files.previous.listFiles { file -> file.name.endsWith(".db") }
                ?.sortedByDescending { it.name }
                ?.drop(KEPT_PREVIOUS)
                ?.forEach { old -> SUFFIXES.forEach { File(old.path + it).delete() } }
            return swapped
        }

        private fun deleteWithJournal(file: File) = SUFFIXES.forEach { File(file.path + it).delete() }
    }
}
