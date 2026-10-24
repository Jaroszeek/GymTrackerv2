package com.jaroszeek.gymtracker.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup and the main tabs, compiled ahead of time on install (A4 "Baseline Profile", D-079).
 *
 * `.\gradlew.bat :app:generateBaselineProfile` writes it to `app/src/release/generated/baselineProfiles`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    // The `benchmark` build has no profile of its own and is obfuscated: release builds only
    @Before
    fun releaseBuildOnly() = assumeFalse(targetAppId.endsWith(".benchmark"))

    /** What runs before the first frame goes to the startup profile, which R8 puts in the first dex file. */
    @Test
    fun startup() = rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        dismissFirstRunPrompt()
    }

    @Test
    fun tabs() = rule.collect(packageName = targetAppId) {
        pressHome()
        startActivityAndWait()
        dismissFirstRunPrompt()
        visitTabs()
    }
}
