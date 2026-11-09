package com.notesapp.domain.notifications

import com.notesapp.data.prefs.NotificationSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

object NotificationSchedule {
    fun next(now: ZonedDateTime, settings: NotificationSettings): Instant? {
        if (!settings.enabled || settings.days.isEmpty()) return null
        val time = LocalTime.of(settings.minuteOfDay / 60, settings.minuteOfDay % 60)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val candidate = date.atTime(time).atZone(now.zone)
            if (date.dayOfWeek.value in settings.days && candidate.isAfter(now)) return candidate.toInstant()
        }
        return null
    }

    /** WorkManager may be delayed; do not deliver an old morning notification later in the evening. */
    fun isDue(now: ZonedDateTime, settings: NotificationSettings): Boolean {
        if (!settings.enabled || now.dayOfWeek.value !in settings.days) return false
        val start = now.toLocalDate().atTime(settings.minuteOfDay / 60, settings.minuteOfDay % 60).atZone(now.zone)
        val elapsed = Duration.between(start, now)
        return !elapsed.isNegative && elapsed <= Duration.ofHours(2)
    }
}
