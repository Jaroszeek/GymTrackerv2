package com.jaroszeek.gymtracker.core.update

import com.jaroszeek.gymtracker.domain.update.AppRelease
import com.jaroszeek.gymtracker.domain.update.AppVersion
import com.jaroszeek.gymtracker.domain.update.plainNotes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** GitHub's `releases/latest` answer, only the fields the updater reads. */
@Serializable
private data class ReleaseJson(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetJson> = emptyList(),
)

@Serializable
private data class AssetJson(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val downloadUrl: String,
)

internal object GitHubReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The release in [text], or null when it offers nothing to install: a draft or pre-release, a tag
     * that is not `vX.Y.Z`, or no APK among the assets (the first `.apk` is taken).
     *
     * @throws SerializationException when [text] is not a release at all
     */
    fun parse(text: String): AppRelease? {
        val release = json.decodeFromString<ReleaseJson>(text)
        if (release.draft || release.prerelease) return null
        val version = AppVersion.parse(release.tagName) ?: return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return AppRelease(version, plainNotes(release.body.orEmpty()), apk.downloadUrl, apk.size, release.htmlUrl)
    }
}
