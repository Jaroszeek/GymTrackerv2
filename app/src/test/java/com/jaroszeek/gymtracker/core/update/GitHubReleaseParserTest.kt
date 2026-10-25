package com.jaroszeek.gymtracker.core.update

import com.jaroszeek.gymtracker.domain.update.AppRelease
import com.jaroszeek.gymtracker.domain.update.AppVersion
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseParserTest {

    @Test
    fun aFullAnswerBecomesTheReleaseWithItsApk() {
        assertEquals(
            AppRelease(
                version = AppVersion(1, 0, 1),
                notes = "1.0.1\n• Rest timer fix\n• Faster start",
                apkUrl = "https://github.com/Jaroszeek/GymTrackerv2/releases/download/v1.0.1/GymTracker-1.0.1.apk",
                apkSize = 5_123_456,
                pageUrl = "https://github.com/Jaroszeek/GymTrackerv2/releases/tag/v1.0.1",
            ),
            GitHubReleaseParser.parse(release()),
        )
    }

    @Test
    fun aReleaseWithoutApkIsNotOffered() {
        assertNull(GitHubReleaseParser.parse(release(assets = "[$CHECKSUMS]")))
        assertNull(GitHubReleaseParser.parse(release(assets = "[]")))
    }

    @Test
    fun aDraftPreReleaseOrOddTagIsNotOffered() {
        assertNull(GitHubReleaseParser.parse(release(prerelease = true)))
        assertNull(GitHubReleaseParser.parse(release(draft = true)))
        assertNull(GitHubReleaseParser.parse(release(tag = "v1.0.1-rc1")))
        assertNull(GitHubReleaseParser.parse(release(tag = "nightly")))
    }

    @Test
    fun missingNotesAreEmpty() {
        assertEquals("", GitHubReleaseParser.parse(release(body = "null"))?.notes)
    }

    @Test(expected = SerializationException::class)
    fun malformedJsonFails() {
        GitHubReleaseParser.parse("""{"tag_name":"v1.0.1",""")
    }

    @Test(expected = SerializationException::class)
    fun anAnswerThatIsNotAReleaseFails() {
        GitHubReleaseParser.parse("""{"message":"Not Found","documentation_url":"https://docs.github.com/rest"}""")
    }

    /** Shaped like GitHub's `releases/latest`, with fields the parser does not read. */
    private fun release(
        tag: String = "v1.0.1",
        body: String = "\"## 1.0.1\\r\\n- Rest timer fix\\r\\n- **Faster** start\"",
        prerelease: Boolean = false,
        draft: Boolean = false,
        assets: String = "[$CHECKSUMS, $APK]",
    ) = """
        {
          "url": "https://api.github.com/repos/Jaroszeek/GymTrackerv2/releases/1",
          "id": 1,
          "author": {"login": "Jaroszeek", "id": 2},
          "tag_name": "$tag",
          "target_commitish": "main",
          "name": "GymTracker 1.0.1",
          "body": $body,
          "html_url": "https://github.com/Jaroszeek/GymTrackerv2/releases/tag/$tag",
          "prerelease": $prerelease,
          "draft": $draft,
          "immutable": false,
          "created_at": "2026-10-25T09:58:00Z",
          "published_at": "2026-10-25T10:00:00Z",
          "assets": $assets
        }
    """.trimIndent()

    private companion object {
        const val CHECKSUMS = """{"name":"checksums.txt","size":120,"content_type":"text/plain",""" +
            """"browser_download_url":"https://github.com/Jaroszeek/GymTrackerv2/releases/download/v1.0.1/checksums.txt"}"""
        const val APK = """{"id":3,"name":"GymTracker-1.0.1.apk","label":"","size":5123456,"download_count":0,""" +
            """"content_type":"application/vnd.android.package-archive","state":"uploaded",""" +
            """"browser_download_url":"https://github.com/Jaroszeek/GymTrackerv2/releases/download/v1.0.1/GymTracker-1.0.1.apk",""" +
            """"digest":"sha256:0f1e2d3c","uploader":{"login":"github-actions[bot]"}}"""
    }
}
