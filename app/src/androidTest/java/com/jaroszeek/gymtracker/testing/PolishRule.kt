package com.jaroszeek.gymtracker.testing

import android.app.LocaleManager
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Runs a test in Polish, the language its texts are written in, whatever the device's language (D-105). */
class PolishRule : ExternalResource() {

    override fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("pl-PL")
        // The process hears of the new language a moment later
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (context.resources.configuration.locales[0].language != "pl") {
            check(SystemClock.uptimeMillis() < deadline) { "The app did not switch to Polish" }
            SystemClock.sleep(POLL_MILLIS)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
    }
}
