package com.olaf.rereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun every(minutes: Int) = Repeat(RepeatUnit.TIME, minutes)

    private fun Reminder.next(from: Long) = nextEventAfter(from, zone)

    // --- Interval reminders without windows --------------------------------------------------

    @Test
    fun `a timer switched on fires one interval after the anchor`() {
        val anchor = at(2026, 9, 22, 14, 0)
        val reminder = Reminder(id = 1, repeat = every(30), anchorMillis = anchor)

        assertEquals(at(2026, 9, 22, 14, 30), reminder.next(anchor))
    }

    @Test
    fun `a late delivery does not push the grid later`() {
        val anchor = at(2026, 9, 22, 14, 0)
        val reminder = Reminder(id = 1, repeat = every(30), anchorMillis = anchor)

        // The 14:30 alarm arrived 4 minutes late; the next one is still on the grid.
        assertEquals(at(2026, 9, 22, 15, 0), reminder.next(at(2026, 9, 22, 14, 34)))
    }

    @Test
    fun `a future start moment is the first event, whatever the interval`() {
        val start = at(2026, 10, 6, 13, 47)
        val reminder = Reminder(id = 1, repeat = every(5), startAtMillis = start)
        val now = at(2026, 9, 22, 14, 0)

        assertTrue(reminder.isPending(now))
        assertEquals(start, reminder.next(now))
    }

    @Test
    fun `once the start moment has passed the interval takes over`() {
        val start = at(2026, 9, 22, 9, 0)
        val reminder = Reminder(id = 1, repeat = every(60), startAtMillis = start)

        assertFalse(reminder.isPending(start))
        assertEquals(at(2026, 9, 22, 10, 0), reminder.next(start))
    }

    // --- Windows -----------------------------------------------------------------------------

    private val workHours = TimeWindow(days = Reminder.WEEKDAYS, startMinute = 9 * 60, endMinute = 17 * 60)

    @Test
    fun `outside its window a timer waits for the next opening and fires there`() {
        val reminder = Reminder(
            id = 1,
            repeat = every(30),
            windows = listOf(workHours),
            anchorMillis = at(2026, 9, 1, 12, 0),
        )
        // Tuesday 20:00 → Wednesday 09:00, then on a 30-minute grid from the opening.
        val opening = reminder.next(at(2026, 9, 22, 20, 0))
        assertEquals(at(2026, 9, 23, 9, 0), opening)
        assertEquals(at(2026, 9, 23, 9, 30), reminder.next(opening!!))
    }

    @Test
    fun `the last event of the day is the last tick before the window closes`() {
        val reminder = Reminder(
            id = 1,
            repeat = every(45),
            windows = listOf(workHours),
            anchorMillis = at(2026, 9, 1, 12, 0),
        )
        // 09:00 + 10 × 45 min = 16:30; 17:15 is past closing, so Friday rolls to Monday.
        assertEquals(at(2026, 9, 28, 9, 0), reminder.next(at(2026, 9, 25, 16, 30)))
    }

    @Test
    fun `switching on inside a window counts from that moment`() {
        val anchor = at(2026, 9, 22, 10, 7)
        val reminder = Reminder(id = 1, repeat = every(30), windows = listOf(workHours), anchorMillis = anchor)

        assertEquals(at(2026, 9, 22, 10, 37), reminder.next(anchor))
    }

    @Test
    fun `a start moment outside the window snaps to the next opening`() {
        val reminder = Reminder(
            id = 1,
            repeat = every(30),
            windows = listOf(workHours),
            startAtMillis = at(2026, 9, 22, 3, 0),
        )
        assertEquals(at(2026, 9, 22, 9, 0), reminder.next(at(2026, 9, 21, 20, 0)))
    }

    @Test
    fun `a start moment on an excluded weekday waits for the next selected day`() {
        val reminder = Reminder(
            id = 1,
            repeat = every(30),
            windows = listOf(TimeWindow(days = Reminder.WEEKDAYS, startMinute = 0, endMinute = Reminder.MINUTES_PER_DAY)),
            startAtMillis = at(2026, 9, 26, 10, 0), // Saturday
        )
        assertEquals(at(2026, 9, 28, 0, 0), reminder.next(at(2026, 9, 22, 8, 0)))
    }

    @Test
    fun `an overnight window runs across midnight`() {
        val night = TimeWindow(days = Reminder.ALL_DAYS, startMinute = 22 * 60, endMinute = 6 * 60)
        val reminder = Reminder(id = 1, repeat = every(120), windows = listOf(night), anchorMillis = at(2026, 9, 1, 12, 0))

        assertEquals(at(2026, 9, 22, 22, 0), reminder.next(at(2026, 9, 22, 12, 0)))
        assertEquals(at(2026, 9, 23, 0, 0), reminder.next(at(2026, 9, 22, 22, 0)))
        assertEquals(at(2026, 9, 23, 4, 0), reminder.next(at(2026, 9, 23, 2, 0)))
        assertEquals(at(2026, 9, 23, 22, 0), reminder.next(at(2026, 9, 23, 4, 0)))
        assertTrue(reminder.isWithinWindowsAt(at(2026, 9, 23, 1, 0), zone))
        assertFalse(reminder.isWithinWindowsAt(at(2026, 9, 23, 12, 0), zone))
    }

    @Test
    fun `several windows per reminder each get their own grid`() {
        val weekdays = workHours
        val saturday = TimeWindow(days = setOf(6), startMinute = 10 * 60, endMinute = 12 * 60)
        val reminder = Reminder(
            id = 1,
            repeat = every(60),
            windows = listOf(weekdays, saturday),
            anchorMillis = at(2026, 9, 1, 12, 0),
        )
        // Friday 17:00 → Saturday 10:00, 11:00, then Monday 09:00.
        assertEquals(at(2026, 9, 26, 10, 0), reminder.next(at(2026, 9, 25, 17, 0)))
        assertEquals(at(2026, 9, 26, 11, 0), reminder.next(at(2026, 9, 26, 10, 0)))
        assertEquals(at(2026, 9, 28, 9, 0), reminder.next(at(2026, 9, 26, 11, 0)))
    }

    @Test
    fun `consecutive all-day windows read as one stretch`() {
        val allWeekdays = TimeWindow(days = Reminder.WEEKDAYS, startMinute = 0, endMinute = Reminder.MINUTES_PER_DAY)
        val reminder = Reminder(id = 1, repeat = every(50), windows = listOf(allWeekdays), anchorMillis = at(2026, 9, 1, 12, 0))
        // Monday 00:00 opens the stretch; 29 × 50 min later it is Tuesday 00:10 — no restart at midnight.
        val mondayOpening = at(2026, 9, 28, 0, 0)
        assertEquals(at(2026, 9, 29, 0, 10), reminder.next(at(2026, 9, 28, 23, 50)))
        assertEquals(mondayOpening, reminder.next(at(2026, 9, 27, 12, 0)))
    }

    @Test
    fun `a window without days can never fire`() {
        val reminder = Reminder(
            id = 1,
            windows = listOf(TimeWindow(days = emptySet())),
            startAtMillis = at(2026, 10, 1, 12, 0),
        )
        assertFalse(reminder.isSchedulable)
        assertNull(reminder.next(at(2026, 9, 22, 0, 0)))
    }

    // --- Calendar reminders ------------------------------------------------------------------

    @Test
    fun `every day at 09_00 stays at 09_00 across daylight saving`() {
        val reminder = Reminder(
            id = 1,
            repeat = Repeat(RepeatUnit.DAYS, 1),
            startAtMillis = at(2026, 3, 27, 9, 0),
        )
        // Clocks go forward in Berlin on 29 March 2026 and back on 25 October 2026.
        assertEquals(at(2026, 3, 29, 9, 0), reminder.next(at(2026, 3, 28, 9, 0)))
        assertEquals(at(2026, 3, 30, 9, 0), reminder.next(at(2026, 3, 29, 9, 0)))
        assertEquals(at(2026, 10, 26, 9, 0), reminder.next(at(2026, 10, 25, 9, 0)))
    }

    @Test
    fun `every 90 days counts from the start`() {
        val reminder = Reminder(id = 1, repeat = Repeat(RepeatUnit.DAYS, 90), startAtMillis = at(2026, 1, 10, 8, 0))

        assertEquals(at(2026, 1, 10, 8, 0), reminder.next(at(2026, 1, 1, 0, 0)))
        assertEquals(at(2026, 4, 10, 8, 0), reminder.next(at(2026, 1, 10, 8, 0)))
    }

    @Test
    fun `every 2 weeks on Monday and Thursday`() {
        // Anchor: Monday 5 Oct 2026, 07:30.
        val reminder = Reminder(
            id = 1,
            repeat = Repeat(RepeatUnit.WEEKS, 2, weekdays = setOf(1, 4)),
            startAtMillis = at(2026, 10, 5, 7, 30),
        )
        assertEquals(at(2026, 10, 5, 7, 30), reminder.next(at(2026, 10, 1, 0, 0)))
        assertEquals(at(2026, 10, 8, 7, 30), reminder.next(at(2026, 10, 5, 7, 30)))
        // The week of 12 Oct is skipped.
        assertEquals(at(2026, 10, 19, 7, 30), reminder.next(at(2026, 10, 8, 7, 30)))
    }

    @Test
    fun `weekly without chosen days repeats on the start's weekday`() {
        val reminder = Reminder(id = 1, repeat = Repeat(RepeatUnit.WEEKS, 1), startAtMillis = at(2026, 10, 7, 18, 0))
        assertEquals(at(2026, 10, 14, 18, 0), reminder.next(at(2026, 10, 7, 18, 0)))
    }

    @Test
    fun `monthly on the 31st falls back to the last day of shorter months`() {
        val reminder = Reminder(
            id = 1,
            repeat = Repeat(RepeatUnit.MONTHS, 1, monthDays = setOf(31)),
            startAtMillis = at(2026, 1, 31, 12, 0),
        )
        assertEquals(at(2026, 2, 28, 12, 0), reminder.next(at(2026, 1, 31, 12, 0)))
        assertEquals(at(2026, 3, 31, 12, 0), reminder.next(at(2026, 2, 28, 12, 0)))
        assertEquals(at(2026, 4, 30, 12, 0), reminder.next(at(2026, 3, 31, 12, 0)))
    }

    @Test
    fun `every 3 months on the 15th`() {
        val reminder = Reminder(id = 1, repeat = Repeat(RepeatUnit.MONTHS, 3), startAtMillis = at(2026, 1, 15, 10, 0))
        assertEquals(at(2026, 4, 15, 10, 0), reminder.next(at(2026, 1, 15, 10, 0)))
        assertEquals(at(2026, 7, 15, 10, 0), reminder.next(at(2026, 4, 20, 0, 0)))
    }

    @Test
    fun `a calendar reminder needs a start moment`() {
        val reminder = Reminder(id = 1, repeat = Repeat(RepeatUnit.DAYS, 1), anchorMillis = at(2026, 1, 1, 9, 0))
        assertFalse(reminder.isSchedulable)
        assertNull(reminder.next(at(2026, 1, 2, 0, 0)))
    }

    // --- Early alerts ------------------------------------------------------------------------

    @Test
    fun `early alerts come before their event, then the event itself`() {
        // The case from issue #11: an event every 3 h 30 min from 10:30, alerts 10 and 5 min ahead.
        val reminder = Reminder(
            id = 1,
            repeat = every(210),
            startAtMillis = at(2026, 10, 5, 10, 30),
            earlyAlerts = listOf(10, 5),
        )
        val first = reminder.nextAlertAfter(at(2026, 10, 5, 10, 0), zone)!!
        assertEquals(Alert(at(2026, 10, 5, 10, 20), at(2026, 10, 5, 10, 30), 10), first)

        val second = reminder.nextAlertAfter(first.alertAt, zone)!!
        assertEquals(Alert(at(2026, 10, 5, 10, 25), at(2026, 10, 5, 10, 30), 5), second)

        val event = reminder.nextAlertAfter(second.alertAt, zone)!!
        assertEquals(Alert(at(2026, 10, 5, 10, 30), at(2026, 10, 5, 10, 30), 0), event)

        val nextEarly = reminder.nextAlertAfter(event.alertAt, zone)!!
        assertEquals(Alert(at(2026, 10, 5, 13, 50), at(2026, 10, 5, 14, 0), 10), nextEarly)
    }

    @Test
    fun `early alerts as long as the interval are ignored`() {
        val reminder = Reminder(id = 1, repeat = every(10), anchorMillis = at(2026, 10, 5, 10, 0), earlyAlerts = listOf(10, 30))
        val alert = reminder.nextAlertAfter(at(2026, 10, 5, 10, 0), zone)!!
        assertEquals(0, alert.offsetMinutes)
        assertEquals(at(2026, 10, 5, 10, 10), alert.alertAt)
    }

    @Test
    fun `an early alert a day ahead of a weekly event`() {
        val reminder = Reminder(
            id = 1,
            repeat = Repeat(RepeatUnit.WEEKS, 1),
            startAtMillis = at(2026, 10, 9, 18, 0),
            earlyAlerts = listOf(24 * 60),
        )
        val alert = reminder.nextAlertAfter(at(2026, 10, 9, 18, 0), zone)!!
        assertEquals(Alert(at(2026, 10, 15, 18, 0), at(2026, 10, 16, 18, 0), 24 * 60), alert)
    }

    @Test
    fun `producesAlertAt rejects alerts the schedule no longer makes`() {
        val reminder = Reminder(id = 1, repeat = every(30), anchorMillis = at(2026, 10, 5, 10, 0))
        assertTrue(reminder.producesAlertAt(at(2026, 10, 5, 11, 0), zone))
        assertFalse(reminder.producesAlertAt(at(2026, 10, 5, 11, 10), zone))
    }

    @Test
    fun `startMomentOf builds the local moment the user picked`() {
        val millis = Reminder.startMomentOf(LocalDate.of(2026, 9, 23), 13 * 60 + 47, zone)

        assertEquals(at(2026, 9, 23, 13, 47), millis)
        assertEquals(LocalDateTime.of(2026, 9, 23, 13, 47), Reminder.localDateTimeOf(millis, zone))
    }
}
