package com.olaf.rereminder.ui.editor

import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.Repeat
import com.olaf.rereminder.data.RepeatUnit
import com.olaf.rereminder.data.nextEventAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DraftEditsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val daily = Repeat(RepeatUnit.DAYS, 1)

    @Test
    fun `adding a second time keeps the first one, taken from the start`() {
        val reminder = Reminder(id = 1, repeat = daily, startAtMillis = at(2026, 10, 5, 7, 0))

        val twice = reminder.withTime(-1, 19 * 60, zone)

        assertEquals(setOf(7 * 60, 19 * 60), twice.repeat.timesOfDay)
        assertEquals(listOf(7 * 60, 19 * 60), twice.calendarTimes(zone))
    }

    @Test
    fun `once times are set the start only names the day`() {
        // Started at 15:00: the 7:00 picked afterwards must still go off on that first day.
        val reminder = Reminder(id = 1, repeat = daily, startAtMillis = at(2026, 10, 5, 15, 0))

        val morning = reminder.withTime(0, 7 * 60, zone)

        assertEquals(at(2026, 10, 5, 0, 0), morning.startAtMillis)
        assertEquals(at(2026, 10, 5, 7, 0), morning.nextEventAfter(at(2026, 10, 4, 12, 0), zone))
    }

    @Test
    fun `picking a new start date keeps the times of day`() {
        val reminder = Reminder(
            id = 1,
            repeat = daily.copy(timesOfDay = setOf(7 * 60, 19 * 60)),
            startAtMillis = at(2026, 10, 5, 0, 0),
        )

        val moved = reminder.withStartDate(LocalDate.of(2026, 10, 12), zone)

        assertEquals(at(2026, 10, 12, 0, 0), moved.startAtMillis)
        assertEquals(setOf(7 * 60, 19 * 60), moved.repeat.timesOfDay)
        assertEquals(at(2026, 10, 12, 7, 0), moved.nextEventAfter(at(2026, 10, 1, 0, 0), zone))
    }

    @Test
    fun `a reminder from before times of day keeps its time when its date moves`() {
        val legacy = Reminder(id = 1, repeat = daily, startAtMillis = at(2026, 10, 5, 9, 30))

        val moved = legacy.withStartDate(LocalDate.of(2026, 10, 8), zone)

        assertEquals(listOf(9 * 60 + 30), moved.calendarTimes(zone))
        assertEquals(at(2026, 10, 8, 9, 30), moved.nextEventAfter(at(2026, 10, 1, 0, 0), zone))
    }

    @Test
    fun `the last time of day cannot be removed`() {
        val reminder = Reminder(id = 1, repeat = daily.copy(timesOfDay = setOf(8 * 60)), startAtMillis = at(2026, 10, 5, 0, 0))

        assertEquals(setOf(8 * 60), reminder.withoutTime(0, zone).repeat.timesOfDay)
    }

    @Test
    fun `times survive a detour through an interval but are not saved with one`() {
        val twice = Reminder(
            id = 1,
            repeat = daily.copy(timesOfDay = setOf(7 * 60, 19 * 60)),
            startAtMillis = at(2026, 10, 5, 0, 0),
        )

        val interval = twice.withRepeat(Repeat(RepeatUnit.TIME, 120), zone = zone)
        val back = interval.withRepeat(Repeat(RepeatUnit.WEEKS, 1), zone = zone)

        assertEquals(setOf(7 * 60, 19 * 60), back.repeat.timesOfDay)
        assertTrue(interval.withoutStrayTimes().repeat.timesOfDay.isEmpty())
    }

    @Test
    fun `an interval made from a calendar repeat starts at its first time`() {
        val twice = Reminder(
            id = 1,
            repeat = daily.copy(timesOfDay = setOf(7 * 60, 19 * 60)),
            startAtMillis = at(2026, 10, 5, 0, 0),
        )

        val interval = twice.withRepeat(Repeat(RepeatUnit.TIME, 120), zone = zone)

        assertEquals(at(2026, 10, 5, 7, 0), interval.startAtMillis)
    }

    @Test
    fun `a calendar repeat without a start gets the next full hour`() {
        val now = at(2026, 10, 5, 13, 20)
        val fresh = Reminder(id = 1, repeat = Repeat(RepeatUnit.TIME, 30))

        val calendar = fresh.withRepeat(daily, nowMillis = now, zone = zone)

        assertEquals(at(2026, 10, 5, 14, 0), calendar.startAtMillis)
        assertEquals(listOf(14 * 60), calendar.calendarTimes(zone))
    }

    @Test
    fun `early alerts longer than the gap between two times are dropped`() {
        val reminder = Reminder(
            id = 1,
            repeat = daily,
            startAtMillis = at(2026, 10, 5, 7, 0),
            earlyAlerts = listOf(180, 30),
        )

        assertEquals(listOf(30), reminder.withTime(-1, 9 * 60, zone).earlyAlerts)
    }
}
