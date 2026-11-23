package com.olaf.rereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Monthly repeats by weekday and on the last day of the month (#22). */
class MonthWeekdayTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun Reminder.next(from: Long) = nextEventAfter(from, zone)

    private fun monthly(week: Int, day: Int, every: Int = 1) =
        Repeat(RepeatUnit.MONTHS, every, monthWeekday = MonthWeekday(week, day))

    @Test
    fun `dates of a weekday in a month`() {
        val november = YearMonth.of(2026, 11) // starts on a Sunday
        assertEquals(LocalDate.of(2026, 11, 6), MonthWeekday(1, 5).dateIn(november))
        assertEquals(LocalDate.of(2026, 11, 27), MonthWeekday(4, 5).dateIn(november))
        assertEquals(LocalDate.of(2026, 11, 27), MonthWeekday(MonthWeekday.LAST, 5).dateIn(november))
        assertEquals(LocalDate.of(2026, 11, 29), MonthWeekday(MonthWeekday.LAST, 7).dateIn(november))
        assertEquals(LocalDate.of(2026, 11, 1), MonthWeekday(1, 7).dateIn(november))
    }

    @Test
    fun `a date's own place in its month`() {
        assertEquals(MonthWeekday(4, 1), MonthWeekday.of(LocalDate.of(2026, 11, 23)))
        assertEquals(MonthWeekday(1, 5), MonthWeekday.of(LocalDate.of(2026, 11, 6)))
        assertEquals(MonthWeekday(MonthWeekday.LAST, 7), MonthWeekday.of(LocalDate.of(2026, 11, 29)))
    }

    @Test
    fun `every month on the first Friday`() {
        val reminder = Reminder(id = 1, repeat = monthly(1, 5), startAtMillis = at(2026, 10, 1, 9, 0))
        assertEquals(at(2026, 10, 2, 9, 0), reminder.next(at(2026, 10, 1, 9, 0)))
        assertEquals(at(2026, 11, 6, 9, 0), reminder.next(at(2026, 10, 2, 9, 0)))
        assertEquals(at(2026, 12, 4, 9, 0), reminder.next(at(2026, 11, 6, 9, 0)))
    }

    @Test
    fun `every month on the last Sunday`() {
        val reminder = Reminder(id = 1, repeat = monthly(MonthWeekday.LAST, 7), startAtMillis = at(2026, 1, 1, 10, 0))
        assertEquals(at(2026, 1, 25, 10, 0), reminder.next(at(2026, 1, 1, 10, 0)))
        assertEquals(at(2026, 2, 22, 10, 0), reminder.next(at(2026, 1, 25, 10, 0)))
        // March 2026 ends on a Tuesday; its last Sunday is also the night clocks go forward.
        assertEquals(at(2026, 3, 29, 10, 0), reminder.next(at(2026, 2, 22, 10, 0)))
    }

    @Test
    fun `every other month skips the month in between`() {
        val reminder = Reminder(id = 1, repeat = monthly(2, 2, every = 2), startAtMillis = at(2026, 1, 1, 8, 0))
        assertEquals(at(2026, 1, 13, 8, 0), reminder.next(at(2026, 1, 1, 8, 0)))
        assertEquals(at(2026, 3, 10, 8, 0), reminder.next(at(2026, 1, 13, 8, 0)))
    }

    @Test
    fun `a weekday before the start date waits for the next month`() {
        // Starts on 10 Oct: that month's first Friday (2 Oct) is already behind the start.
        val reminder = Reminder(id = 1, repeat = monthly(1, 5), startAtMillis = at(2026, 10, 10, 9, 0))
        assertEquals(at(2026, 11, 6, 9, 0), reminder.next(at(2026, 10, 1, 0, 0)))
    }

    @Test
    fun `a weekday works with several times of day`() {
        val reminder = Reminder(
            id = 1,
            repeat = monthly(1, 1).copy(timesOfDay = setOf(8 * 60, 18 * 60)),
            startAtMillis = at(2026, 11, 1, 0, 0),
        )
        assertEquals(at(2026, 11, 2, 8, 0), reminder.next(at(2026, 11, 1, 0, 0)))
        assertEquals(at(2026, 11, 2, 18, 0), reminder.next(at(2026, 11, 2, 8, 0)))
        assertEquals(at(2026, 12, 7, 8, 0), reminder.next(at(2026, 11, 2, 18, 0)))
    }

    @Test
    fun `the last day of the month is day 31`() {
        val reminder = Reminder(
            id = 1,
            repeat = Repeat(RepeatUnit.MONTHS, 1, monthDays = setOf(31)),
            startAtMillis = at(2026, 1, 1, 9, 0),
        )
        assertEquals(at(2026, 1, 31, 9, 0), reminder.next(at(2026, 1, 1, 9, 0)))
        assertEquals(at(2026, 2, 28, 9, 0), reminder.next(at(2026, 1, 31, 9, 0)))
        assertEquals(at(2026, 4, 30, 9, 0), reminder.next(at(2026, 3, 31, 9, 0)))
    }

    @Test
    fun `early alerts stay shorter than four weeks for a monthly weekday`() {
        assertEquals(28L * 24 * 60, monthly(1, 5).shortestGapMinutes)
    }

    @Test
    fun `an import with a weekday the app cannot place falls back to the days of the month`() {
        val broken = Reminder(id = 1, repeat = monthly(7, 9).copy(monthDays = setOf(15)))
        val clean = Backup.sanitize(broken)
        assertNull(clean.repeat.monthWeekday)
        assertEquals(setOf(15), clean.repeat.monthDays)
        assertFalse(MonthWeekday(5, 1).isValid)
    }
}
