package com.moadrive.pleos.care.child

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ChildScheduledReminder(val dueDate: LocalDate, val triggerAt: Instant, val zoneId: String)

/** One notification per due date; new reminders entered after the due-day morning remain in-app. */
fun childReminderNotificationTime(
    reminder: ChildVaccinationReminder,
    now: Instant,
    zone: ZoneId,
    deliveredFor: LocalDate? = null,
    scheduled: ChildScheduledReminder? = null,
): Instant? {
    if (!reminder.notificationsEnabled || reminder.completedOn != null || deliveredFor == reminder.date) return null
    if (now.atZone(zone).toLocalDate().isAfter(reminder.date)) return null
    val candidates = listOf(reminder.date.minusDays(1), reminder.date)
        .map { it.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant() }
    // Inexact alarms may still be queued after 09:00. Preserve that registered delivery;
    // the future-only rule applies to newly registered or changed schedules.
    if (scheduled != null && scheduled.dueDate == reminder.date && scheduled.zoneId == zone.id &&
        scheduled.triggerAt in candidates) return scheduled.triggerAt
    return candidates.firstOrNull { !it.isBefore(now) }
}

fun childReminderCanNotify(
    reminder: ChildVaccinationReminder,
    scheduledDueDate: LocalDate,
    deliveredFor: LocalDate?,
    today: LocalDate,
): Boolean = reminder.notificationsEnabled && reminder.completedOn == null &&
    reminder.date == scheduledDueDate && deliveredFor != reminder.date &&
    today >= reminder.date.minusDays(1) && today <= reminder.date
