package com.jaroszeek.gymtracker.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {

    @Test
    fun versionParsesWithAndWithoutThePrefix() {
        assertEquals(AppVersion(1, 0, 1), AppVersion.parse("v1.0.1"))
        assertEquals(AppVersion(1, 0, 1), AppVersion.parse("1.0.1"))
        assertEquals(AppVersion(12, 34, 56), AppVersion.parse("v12.34.56"))
        assertEquals("1.0.1", AppVersion(1, 0, 1).toString())
    }

    @Test
    fun anythingButXyzIsNoVersion() {
        listOf("", "v", "1.0", "v1.0.1.2", "v1.0.1-rc1", "V1.0.1", "1.0.x", " 1.0.1", "vv1.0.1", "-1.0.1").forEach {
            assertNull(it, AppVersion.parse(it))
        }
    }

    @Test
    fun minorOrPatchOf100IsNoVersion() {
        assertNull(AppVersion.parse("1.100.0"))
        assertNull(AppVersion.parse("1.0.100"))
        assertEquals(AppVersion(1, 99, 99), AppVersion.parse("1.99.99"))
        // The versionCode would pass Android's limit
        assertNull(AppVersion.parse("210000.0.0"))
        assertEquals(2_099_999_999, AppVersion.parse("209999.99.99")?.versionCode)
    }

    @Test
    fun versionCodeFollowsTheTag() {
        assertEquals(10_000, AppVersion(1, 0, 0).versionCode)
        assertEquals(10_001, AppVersion(1, 0, 1).versionCode)
        assertEquals(21_203, AppVersion(2, 12, 3).versionCode)
    }

    @Test
    fun versionsCompareFieldByField() {
        assertTrue(AppVersion(1, 0, 1) > AppVersion(1, 0, 0))
        assertTrue(AppVersion(1, 1, 0) > AppVersion(1, 0, 99))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertEquals(0, AppVersion(1, 2, 3).compareTo(AppVersion(1, 2, 3)))
        assertEquals(AppVersion(1, 10, 0), listOf(AppVersion(1, 9, 9), AppVersion(1, 10, 0), AppVersion(1, 2, 0)).max())
    }

    @Test
    fun notesBecomePlainText() {
        val markdown = "## 1.0.1\r\n\r\n\r\n### **Fixes**\r\n- The `rest` timer\r\n* **Bold** note\r\n  - nested\r\n\r\n\r\n\r\nThanks #12"
        assertEquals("1.0.1\n\nFixes\n• The rest timer\n• Bold note\n• nested\n\nThanks #12", plainNotes(markdown))
    }

    @Test
    fun emptyNotesStayEmpty() {
        assertEquals("", plainNotes(""))
        assertEquals("", plainNotes("\n\n  \n"))
    }

    @Test
    fun aCheckIsDueOncePerDay() {
        val last = 1_000_000_000L
        assertTrue(isUpdateCheckDue(lastCheck = null, now = last))
        assertFalse(isUpdateCheckDue(last, last))
        assertFalse(isUpdateCheckDue(last, last + UPDATE_CHECK_INTERVAL_MILLIS - 1))
        assertTrue(isUpdateCheckDue(last, last + UPDATE_CHECK_INTERVAL_MILLIS))
        assertTrue(isUpdateCheckDue(last, last + 3 * UPDATE_CHECK_INTERVAL_MILLIS))
    }

    @Test
    fun aClockMovedBackMakesACheckDue() {
        assertTrue(isUpdateCheckDue(lastCheck = 1_000_000_000L, now = 999_999_999L))
    }
}
