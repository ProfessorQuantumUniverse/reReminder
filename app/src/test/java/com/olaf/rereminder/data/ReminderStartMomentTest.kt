package com.olaf.rereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Covers the start moment, which is the one place where a reminder's first trigger is not simply
 * "now plus one interval".
 */
class ReminderStartMomentTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `without a start moment the first trigger is one interval away`() {
        val reminder = Reminder(id = 1, intervalMinutes = 30)
        val now = at(2026, 9, 22, 14, 0)

        assertEquals(at(2026, 9, 22, 14, 30), reminder.nextTriggerAfter(now, zone))
        assertFalse(reminder.isPending(now))
    }

    @Test
    fun `a future start moment is the first trigger, whatever the interval`() {
        val start = at(2026, 10, 6, 13, 47)
        val reminder = Reminder(id = 1, intervalMinutes = 5, startAtMillis = start)
        val now = at(2026, 9, 22, 14, 0)

        assertTrue(reminder.isPending(now))
        assertEquals(start, reminder.nextTriggerAfter(now, zone))
    }

    @Test
    fun `nothing is active before the start moment`() {
        val start = at(2026, 10, 6, 13, 47)
        val reminder = Reminder(id = 1, startAtMillis = start)

        assertFalse(reminder.isActiveAt(start - 1, zone))
        assertTrue(reminder.isActiveAt(start, zone))
    }

    @Test
    fun `once the start moment has passed the interval takes over`() {
        val start = at(2026, 9, 22, 9, 0)
        val reminder = Reminder(id = 1, intervalMinutes = 60, startAtMillis = start)
        val now = at(2026, 9, 22, 9, 0)

        assertFalse(reminder.isPending(now))
        assertEquals(at(2026, 9, 22, 10, 0), reminder.nextTriggerAfter(now, zone))
    }

    @Test
    fun `a start moment outside the daily window snaps to the next open window`() {
        // Starts at 03:00 on a Tuesday, but the timer only runs 09:00–17:00 on weekdays.
        val start = at(2026, 9, 22, 3, 0)
        val reminder = Reminder(
            id = 1,
            intervalMinutes = 30,
            days = Reminder.WEEKDAYS,
            startMinute = 9 * 60,
            endMinute = 17 * 60,
            startAtMillis = start,
        )
        val now = at(2026, 9, 21, 20, 0)

        assertEquals(at(2026, 9, 22, 9, 0), reminder.nextTriggerAfter(now, zone))
    }

    @Test
    fun `a start moment on an excluded weekday waits for the next selected day`() {
        // Saturday 10:00, but the timer only runs Mon–Fri.
        val start = at(2026, 9, 26, 10, 0)
        val reminder = Reminder(
            id = 1,
            intervalMinutes = 30,
            days = Reminder.WEEKDAYS,
            startAtMillis = start,
        )
        val now = at(2026, 9, 22, 8, 0)

        // The following Monday, at midnight since the schedule is all-day.
        assertEquals(at(2026, 9, 28, 0, 0), reminder.nextTriggerAfter(now, zone))
    }

    @Test
    fun `a window never opens before the start moment`() {
        val start = at(2026, 10, 1, 12, 0)
        val reminder = Reminder(id = 1, startAtMillis = start)

        assertEquals(start, reminder.nextWindowStartAfter(at(2026, 9, 22, 0, 0), zone))
    }

    @Test
    fun `a timer with no weekdays can never fire`() {
        val reminder = Reminder(id = 1, days = emptySet(), startAtMillis = at(2026, 10, 1, 12, 0))

        assertNull(reminder.nextTriggerAfter(at(2026, 9, 22, 0, 0), zone))
    }

    @Test
    fun `startMomentOf builds the local moment the user picked`() {
        val millis = Reminder.startMomentOf(LocalDate.of(2026, 9, 23), 13 * 60 + 47, zone)

        assertEquals(at(2026, 9, 23, 13, 47), millis)
        assertEquals(
            LocalDateTime.of(2026, 9, 23, 13, 47),
            Reminder.localDateTimeOf(millis, zone),
        )
    }
}
