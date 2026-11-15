package com.moadrive.pleos.care.child

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ChildReminderScheduleTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val reminder = ChildVaccinationReminder("id", "접종 확인", LocalDate.of(2026, 11, 15))

    @Test fun schedulesAtNineOnThePreviousLocalDay() {
        assertEquals(Instant.parse("2026-11-14T00:00:00Z"), childReminderNotificationTime(
            reminder, Instant.parse("2026-11-13T12:00:00Z"), seoul))
    }

    @Test fun afterPreviousDayNineFallsBackToDueDayNine() {
        assertEquals(Instant.parse("2026-11-15T00:00:00Z"), childReminderNotificationTime(
            reminder, Instant.parse("2026-11-14T00:01:00Z"), seoul))
    }

    @Test fun afterDueDayNineDoesNotSendCatchUpNotifications() {
        assertNull(childReminderNotificationTime(reminder, Instant.parse("2026-11-15T00:01:00Z"), seoul))
        assertNull(childReminderNotificationTime(reminder, Instant.parse("2026-11-16T00:00:00Z"), seoul))
    }

    @Test fun resumingAfterNinePreservesAnUndeliveredInexactAlarm() {
        val original = Instant.parse("2026-11-15T00:00:00Z")
        val registered = ChildScheduledReminder(reminder.date, original, seoul.id)
        val resumed = Instant.parse("2026-11-15T00:01:00Z")
        assertEquals(original, childReminderNotificationTime(reminder, resumed, seoul, scheduled = registered))
        assertNull(childReminderNotificationTime(reminder, resumed, seoul))
    }

    @Test fun delayedPreviousDayAlarmDoesNotMoveToTomorrowOnResume() {
        val original = Instant.parse("2026-11-14T00:00:00Z")
        val registered = ChildScheduledReminder(reminder.date, original, seoul.id)
        assertEquals(original, childReminderNotificationTime(reminder, Instant.parse("2026-11-14T00:01:00Z"), seoul, scheduled = registered))
    }

    @Test fun aSavedScheduleCannotReviveCompletedDeliveredOrExpiredReminders() {
        val registered = ChildScheduledReminder(reminder.date, Instant.parse("2026-11-15T00:00:00Z"), seoul.id)
        val now = Instant.parse("2026-11-15T00:01:00Z")
        assertNull(childReminderNotificationTime(reminder.copy(completedOn = reminder.date), now, seoul, scheduled = registered))
        assertNull(childReminderNotificationTime(reminder.copy(notificationsEnabled = false), now, seoul, scheduled = registered))
        assertNull(childReminderNotificationTime(reminder, now, seoul, deliveredFor = reminder.date, scheduled = registered))
        assertNull(childReminderNotificationTime(reminder, Instant.parse("2026-11-16T00:00:00Z"), seoul, scheduled = registered))
    }

    @Test fun changedDueDateDoesNotReuseAnOldRegisteredTrigger() {
        val registered = ChildScheduledReminder(reminder.date, Instant.parse("2026-11-15T00:00:00Z"), seoul.id)
        val changed = reminder.copy(date = LocalDate.of(2026, 11, 16))
        assertEquals(Instant.parse("2026-11-16T00:00:00Z"), childReminderNotificationTime(
            changed, Instant.parse("2026-11-15T00:01:00Z"), seoul, scheduled = registered))
    }

    @Test fun completedDisabledAndAlreadyDeliveredRemindersAreNotScheduled() {
        val now = Instant.parse("2026-11-13T12:00:00Z")
        assertNull(childReminderNotificationTime(reminder.copy(completedOn = LocalDate.of(2026, 11, 13)), now, seoul))
        assertNull(childReminderNotificationTime(reminder.copy(notificationsEnabled = false), now, seoul))
        assertNull(childReminderNotificationTime(reminder, now, seoul, deliveredFor = reminder.date))
    }

    @Test fun aChangedDueDateCanReceiveItsOwnNotification() {
        val changed = reminder.copy(date = LocalDate.of(2026, 11, 17))
        assertNotNull(childReminderNotificationTime(changed, Instant.parse("2026-11-13T12:00:00Z"), seoul, deliveredFor = reminder.date))
    }

    @Test fun daylightSavingChangeKeepsNineAmLocalTime() {
        val zone = ZoneId.of("America/New_York")
        val fall = reminder.copy(date = LocalDate.of(2026, 11, 2))
        val result = childReminderNotificationTime(fall, Instant.parse("2026-10-31T12:00:00Z"), zone)!!
        assertEquals(Instant.parse("2026-11-01T14:00:00Z"), result)
        assertEquals(9, result.atZone(zone).hour)
    }

    @Test fun receiverRejectsReplacedDatesAndDuplicateOrOverdueDeliveries() {
        val today = LocalDate.of(2026, 11, 14)
        assertTrue(childReminderCanNotify(reminder, reminder.date, null, today))
        assertFalse(childReminderCanNotify(reminder, reminder.date.minusDays(1), null, today))
        assertFalse(childReminderCanNotify(reminder, reminder.date, reminder.date, today))
        assertFalse(childReminderCanNotify(reminder, reminder.date, null, reminder.date.plusDays(1)))
        assertFalse(childReminderCanNotify(reminder.copy(notificationsEnabled = false), reminder.date, null, today))
    }
}
