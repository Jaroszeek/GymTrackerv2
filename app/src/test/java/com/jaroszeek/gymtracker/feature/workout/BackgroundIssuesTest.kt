package com.jaroszeek.gymtracker.feature.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundIssuesTest {

    private val battery = BackgroundIssues(batteryLimited = true)
    private val all = BackgroundIssues(notificationsOff = true, exactAlarmsOff = true, batteryLimited = true)

    @Test
    fun noIssuesShowNothing() {
        assertFalse(BackgroundIssues().shown(emptySet()))
        assertEquals(emptySet<String>(), BackgroundIssues().keys)
    }

    @Test
    fun issuesShowUntilHidden() {
        assertTrue(all.shown(emptySet()))
        assertFalse(all.shown(all.keys))
    }

    @Test
    fun aNewIssueBringsTheCardBack() {
        val hidden = battery.keys
        assertFalse(battery.shown(hidden))
        assertTrue(battery.copy(notificationsOff = true).shown(hidden))
        assertTrue(BackgroundIssues(exactAlarmsOff = true).shown(hidden))
    }

    @Test
    fun aFixedHiddenIssueKeepsTheRestHidden() {
        assertFalse(battery.shown(all.keys))
    }

    @Test
    fun everyIssueHasItsOwnKey() {
        assertEquals(3, all.keys.size)
    }
}
