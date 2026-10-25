package com.jaroszeek.gymtracker.domain.update

/** A release version as tagged, `vX.Y.Z` (A5.11, A8); [minor] and [patch] stay below 100, so [versionCode] orders like the version. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    /** The versionCode the release build gets from its tag. */
    val versionCode: Int get() = major * 10_000 + minor * 100 + patch

    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val TAG_PATTERN = Regex("""v?(\d{1,6})\.(\d{1,2})\.(\d{1,2})""")

        /** The largest major whose versionCode Android still takes (at most 2 100 000 000). */
        private const val MAX_MAJOR = 209_999

        /** `v1.0.1` or `1.0.1`; null for anything else, e.g. a pre-release suffix or a minor of 100. */
        fun parse(text: String): AppVersion? {
            val (major, minor, patch) = TAG_PATTERN.matchEntire(text)?.destructured ?: return null
            return AppVersion(major.toInt(), minor.toInt(), patch.toInt()).takeIf { it.major <= MAX_MAJOR }
        }
    }
}

/** A published release with its APK; [notes] are plain text ([plainNotes]). */
data class AppRelease(val version: AppVersion, val notes: String, val apkUrl: String, val apkSize: Long, val pageUrl: String)

/** Automatic checks run at most once a day. */
const val UPDATE_CHECK_INTERVAL_MILLIS = 24 * 60 * 60_000L

/** Whether an automatic check is due: never checked, a day has passed, or the clock went back before the last check. */
fun isUpdateCheckDue(lastCheck: Long?, now: Long): Boolean =
    lastCheck == null || now < lastCheck || now - lastCheck >= UPDATE_CHECK_INTERVAL_MILLIS

/**
 * Release notes written in Markdown, as plain text for a dialog: no heading marks, bold or code marks,
 * "- " and "* " bullets as "• ", and at most one blank line in a row.
 */
fun plainNotes(markdown: String): String = markdown.lineSequence()
    .map { line ->
        val text = line.trim().replace("**", "").replace("`", "")
        when {
            HEADING.containsMatchIn(text) -> text.replaceFirst(HEADING, "")
            text.startsWith("- ") || text.startsWith("* ") -> "• " + text.substring(2).trim()
            else -> text
        }
    }
    .joinToString("\n")
    .replace(BLANK_LINES, "\n\n")
    .trim()

private val HEADING = Regex("""^#{1,6}\s+""")
private val BLANK_LINES = Regex("""\n{3,}""")
