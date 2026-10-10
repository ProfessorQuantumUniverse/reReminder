package com.olaf.rereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCodecTest {

    private val now = 1_790_000_000_000L

    @Test
    fun `a 3_x list becomes interval reminders with one window`() {
        val legacy = """
            [{"id":3,"name":"Stretch","message":"Up!","intervalMinutes":45,"enabled":true,
              "days":[1,2,3,4,5],"startMinute":540,"endMinute":1020,"colorIndex":2,
              "soundEnabled":false,"vibrationEnabled":true,"startAtMillis":0,"nextTriggerAt":0}]
        """.trimIndent()

        val reminder = ReminderCodec.decode(legacy, now).single()

        assertEquals(3, reminder.id)
        assertEquals("Stretch", reminder.name)
        assertEquals(Repeat(RepeatUnit.TIME, 45), reminder.repeat)
        assertEquals(listOf(TimeWindow(Reminder.WEEKDAYS, 540, 1020)), reminder.windows)
        assertEquals(SoundMode.SILENT, reminder.sound.mode)
        assertEquals(2, reminder.colorIndex)
        assertEquals(now, reminder.anchorMillis)
    }

    @Test
    fun `an unrestricted 3_x timer gets no windows and keeps its running countdown`() {
        val armed = now + 10 * 60_000L
        val legacy = """[{"id":1,"intervalMinutes":30,"nextTriggerAt":$armed}]"""

        val reminder = ReminderCodec.decode(legacy, now).single()

        assertTrue(reminder.windows.isEmpty())
        // The next alert stays where 3.x had armed it.
        assertEquals(armed, reminder.nextEventAfter(now))
    }

    @Test
    fun `the current format round-trips`() {
        val reminders = listOf(
            Reminder(
                id = 1,
                name = "Pills",
                repeat = Repeat(RepeatUnit.WEEKS, 2, weekdays = setOf(1, 4)),
                startAtMillis = now,
                earlyAlerts = listOf(10, 5),
                alertStyle = AlertStyle.ALARM,
                sound = SoundChoice(SoundMode.TONE, "content://tone/1"),
                sortIndex = 4,
            ),
            Reminder(id = 2, windows = listOf(TimeWindow())),
        )

        assertEquals(reminders, ReminderCodec.decode(ReminderCodec.encode(reminders), now))
    }

    @Test
    fun `the old global text-to-speech setting makes default reminders speak, alarms untouched`() {
        val reminders = listOf(
            Reminder(id = 1),
            Reminder(id = 2, sound = SoundChoice(SoundMode.SILENT)),
            Reminder(id = 3, alertStyle = AlertStyle.ALARM),
        )

        val moved = ReminderCodec.applyLegacySoundDefaults(reminders, speak = true, muted = false)

        assertEquals(SoundMode.SPEAK, moved[0].sound.mode)
        assertEquals(SoundMode.SILENT, moved[1].sound.mode)
        assertEquals(SoundMode.DEFAULT, moved[2].sound.mode)
    }

    @Test
    fun `an app muted in 3_x keeps its reminders silent`() {
        val moved = ReminderCodec.applyLegacySoundDefaults(listOf(Reminder(id = 1)), speak = true, muted = true)
        assertEquals(SoundMode.SILENT, moved.single().sound.mode)
    }

    @Test
    fun `nothing to carry over leaves reminders alone`() {
        val reminders = listOf(Reminder(id = 1))
        assertEquals(reminders, ReminderCodec.applyLegacySoundDefaults(reminders, speak = false, muted = false))
    }

    @Test(expected = Exception::class)
    fun `damaged data throws instead of reading as empty`() {
        ReminderCodec.decode("{\"version\":2,\"reminders\":[{\"id\":", now)
    }
}
