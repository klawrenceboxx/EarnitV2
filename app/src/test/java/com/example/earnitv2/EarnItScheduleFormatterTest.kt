package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarnItScheduleFormatterTest {
    @Test
    fun scheduleSummary_formatsEveryDayAllDay() {
        assertEquals(
            "Every day · All day",
            EarnItRuleStore.scheduleSummary(
                EarnItRuleStore.allDays.toSet(),
                listOf(EarnItRuleStore.TimeWindow(0, 1_440))
            )
        )
    }

    @Test
    fun scheduleSummary_formatsWeekdaysOneWindow() {
        assertEquals(
            "Weekdays · 9:00 AM-5:00 PM",
            EarnItRuleStore.scheduleSummary(
                setOf(1, 2, 3, 4, 5),
                listOf(EarnItRuleStore.TimeWindow(9 * 60, 17 * 60))
            )
        )
    }

    @Test
    fun scheduleSummary_formatsMultipleWindowsCompactly() {
        assertEquals(
            "Weekdays · 2 time windows",
            EarnItRuleStore.scheduleSummary(
                setOf(1, 2, 3, 4, 5),
                listOf(EarnItRuleStore.TimeWindow(8 * 60, 12 * 60), EarnItRuleStore.TimeWindow(13 * 60, 16 * 60))
            )
        )
    }

    @Test
    fun scheduleDetailLines_includeEachWindow() {
        assertEquals(
            listOf("Mon, Wed, Fri", "8:00 AM-12:00 PM", "1:00 PM-4:00 PM"),
            EarnItRuleStore.scheduleDetailLines(
                setOf(1, 3, 5),
                listOf(EarnItRuleStore.TimeWindow(8 * 60, 12 * 60), EarnItRuleStore.TimeWindow(13 * 60, 16 * 60))
            )
        )
    }

    @Test
    fun overnightWindow_usesStartDayOwnership() {
        val rule = rule(
            activeDays = setOf(1),
            windows = listOf(EarnItRuleStore.TimeWindow(21 * 60, 8 * 60))
        )

        assertEquals("Mon · 9:00 PM-8:00 AM", rule.scheduleLabel)
        assertEquals(true, rule.isActiveAt(day = 1, minuteOfDay = 22 * 60))
        assertEquals(true, rule.isActiveAt(day = 2, minuteOfDay = 7 * 60))
        assertEquals(false, rule.isActiveAt(day = 2, minuteOfDay = 9 * 60))
    }

    @Test
    fun scheduleWindowValidation_rejectsZeroDuplicateAndOverlap() {
        val existing = listOf(EarnItRuleStore.TimeWindow(8 * 60, 12 * 60))

        assertEquals(
            "Start and end must be different.",
            scheduleWindowValidationMessage(existing, null, EarnItRuleStore.TimeWindow(8 * 60, 8 * 60))
        )
        assertEquals(
            "This time window already exists.",
            scheduleWindowValidationMessage(existing, null, EarnItRuleStore.TimeWindow(8 * 60, 12 * 60))
        )
        assertEquals(
            "Time windows cannot overlap.",
            scheduleWindowValidationMessage(existing, null, EarnItRuleStore.TimeWindow(11 * 60, 13 * 60))
        )
        assertNull(scheduleWindowValidationMessage(existing, null, EarnItRuleStore.TimeWindow(12 * 60, 16 * 60)))
    }

    private fun rule(
        activeDays: Set<Int>,
        windows: List<EarnItRuleStore.TimeWindow>
    ): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "rule_schedule",
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(EarnItRuleStore.RuleApp("ig", "Instagram")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = activeDays,
            startMinute = windows.first().startMinute,
            endMinute = windows.first().endMinute,
            type = EarnItRuleStore.RuleType.ScheduledBlock,
            timeWindows = windows
        )
    }
}
