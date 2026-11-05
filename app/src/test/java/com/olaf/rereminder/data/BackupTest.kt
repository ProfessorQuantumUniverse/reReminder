package com.olaf.rereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {

    private val now = 1_790_000_000_000L

    private val stretch = Reminder(
        id = 3,
        name = "Stretch",
        message = "Up!",
        colorIndex = 2,
        repeat = Repeat(RepeatUnit.TIME, 45),
        windows = listOf(TimeWindow(Reminder.WEEKDAYS, 540, 1020)),
        anchorMillis = now - 600_000L,
        earlyAlerts = listOf(5),
        alertStyle = AlertStyle.ALARM,
        sound = SoundChoice(SoundMode.SPEAK),
        sortIndex = 1,
        nextTriggerAt = now + 60_000L,
        nextEventAt = now + 360_000L,
        snoozeUntil = now + 120_000L,
    )
    private val water = Reminder(
        id = 7,
        name = "Water",
        repeat = Repeat(RepeatUnit.DAYS, 1),
        startAtMillis = now + 3_600_000L,
        sortIndex = 0,
    )
    private val settings = BackupSettings(
        ringtone = "",
        alarmTone = "content://media/internal/audio/media/12",
        speechStream = "alarm",
        snoozeMinutes = 15,
        snoozeOnAlarmSwipe = true,
        vibrationEnabled = false,
        vibrationPattern = 2,
        compactList = true,
        sortMode = "next",
    )

    @Test
    fun `an export reads back as the same reminders and settings`() {
        val text = Backup.encode(listOf(stretch, water), settings, exportedAt = now, appVersion = "4.1")

        val contents = Backup.decode(text, now)

        assertEquals(settings, contents.settings)
        assertEquals(listOf("Water", "Stretch"), contents.reminders.map { it.name })
        assertEquals(stretch.copy(nextTriggerAt = 0L, nextEventAt = 0L, snoozeUntil = 0L), contents.reminders[1])
    }

    @Test
    fun `an export names its format and leaves out what only mattered on the old phone`() {
        val text = Backup.encode(listOf(stretch), settings = null, exportedAt = now, appVersion = null)

        assertTrue(text.contains("\"format\": \"${Backup.FORMAT}\""))
        assertTrue(text.contains("\"version\": ${Backup.VERSION}"))
        assertTrue(text.contains("\"nextTriggerAt\": 0"))
        assertTrue(text.contains("\"snoozeUntil\": 0"))
    }

    @Test
    fun `the app's own store file and a 3_x list can be imported too`() {
        val store = ReminderCodec.encode(listOf(stretch))
        val fromStore = Backup.decode(store, now)
        assertEquals(listOf("Stretch"), fromStore.reminders.map { it.name })
        assertEquals(0L, fromStore.reminders.single().nextTriggerAt)
        assertNull(fromStore.settings)

        val legacy = """[{"id":1,"name":"Old","intervalMinutes":30}]"""
        assertEquals(listOf("Old"), Backup.decode(legacy, now).reminders.map { it.name })
    }

    @Test
    fun `files that aren't exports are rejected`() {
        assertRejected("not json at all", BackupError.NOT_A_BACKUP)
        assertRejected("""{"some":"thing"}""", BackupError.NOT_A_BACKUP)
        assertRejected("""{"format":"another-app","version":1,"reminders":[]}""", BackupError.NOT_A_BACKUP)
        assertRejected("""{"format":"rereminder-backup","reminders":[]}""", BackupError.NOT_A_BACKUP)
        assertRejected("""{"format":"rereminder-backup","version":1,"reminders":[{"name":"no id"}]}""", BackupError.NOT_A_BACKUP)
        assertRejected("42", BackupError.NOT_A_BACKUP)
    }

    @Test
    fun `a file from a newer version is rejected rather than half read`() {
        assertRejected(
            """{"format":"rereminder-backup","version":${Backup.VERSION + 1},"reminders":[{"id":1}]}""",
            BackupError.TOO_NEW,
        )
        assertRejected("""{"version":${ReminderCodec.VERSION + 1},"reminders":[{"id":1}]}""", BackupError.TOO_NEW)
    }

    @Test
    fun `a file without reminders is rejected`() {
        assertRejected(Backup.encode(emptyList(), settings, now, null), BackupError.EMPTY)
    }

    @Test
    fun `an oversized file is rejected`() {
        assertRejected(" ".repeat(Backup.MAX_BYTES + 1), BackupError.TOO_LARGE)
    }

    @Test
    fun `unknown fields from a newer app or a hand edit are ignored`() {
        val text = """
            {"format":"rereminder-backup","version":1,"comment":"mine",
             "reminders":[{"id":1,"name":"Tea","futureField":true}],
             "settings":{"snoozeMinutes":5,"somethingNew":1}}
        """.trimIndent()

        val contents = Backup.decode(text, now)

        assertEquals("Tea", contents.reminders.single().name)
        assertEquals(BackupSettings(snoozeMinutes = 5), contents.settings)
    }

    @Test
    fun `values a hand edit broke are brought back into range`() {
        val broken = Reminder(
            id = 1,
            repeat = Repeat(RepeatUnit.WEEKS, every = 0, weekdays = setOf(0, 3, 9), monthDays = setOf(0, 15, 40)),
            windows = listOf(TimeWindow(days = setOf(1, 8), startMinute = -5, endMinute = 5000)),
            earlyAlerts = listOf(10, 10, -3, 0, 5, 20, 30),
            startAtMillis = -1L,
            anchorMillis = -1L,
            sound = SoundChoice(SoundMode.TONE, toneUri = null),
        )

        val clean = Backup.sanitize(broken)

        assertEquals(1, clean.repeat.every)
        assertEquals(setOf(3), clean.repeat.weekdays)
        assertEquals(setOf(15), clean.repeat.monthDays)
        assertEquals(TimeWindow(days = setOf(1), startMinute = 0, endMinute = Reminder.MINUTES_PER_DAY), clean.windows.single())
        assertEquals(listOf(10, 5, 20), clean.earlyAlerts)
        assertEquals(0L, clean.startAtMillis)
        assertEquals(0L, clean.anchorMillis)
        assertEquals(SoundChoice(SoundMode.DEFAULT), clean.sound)
    }

    @Test
    fun `early alerts longer than the gap between events are dropped`() {
        val everyHalfHour = Reminder(id = 1, repeat = Repeat(RepeatUnit.TIME, 30), earlyAlerts = listOf(10, 30, 45))
        assertEquals(listOf(10), Backup.sanitize(everyHalfHour).earlyAlerts)
    }

    @Test
    fun `adding puts new reminders after the current ones under fresh ids`() {
        val current = listOf(water.copy(id = 1, sortIndex = 0), stretch.copy(id = 4, sortIndex = 3))
        val tea = Reminder(id = 1, name = "Tea", sortIndex = 0)

        val result = Backup.merge(current, listOf(tea))

        assertEquals(1, result.added)
        assertEquals(0, result.skipped)
        val added = result.reminders.last()
        assertEquals("Tea", added.name)
        assertEquals(5, added.id)
        assertEquals(4, added.sortIndex)
        assertEquals(current, result.reminders.dropLast(1))
    }

    @Test
    fun `adding the same file twice changes nothing the second time`() {
        val imported = Backup.decode(Backup.encode(listOf(stretch, water), null, now, null), now).reminders
        val once = Backup.merge(emptyList(), imported)
        assertEquals(2, once.added)

        // On the device the reminders have since been armed and maybe switched off.
        val onDevice = once.reminders.map { it.copy(nextTriggerAt = now + 1, enabled = false, anchorMillis = now) }
        val twice = Backup.merge(onDevice, imported)

        assertEquals(0, twice.added)
        assertEquals(2, twice.skipped)
        assertEquals(onDevice, twice.reminders)
    }

    @Test
    fun `duplicates inside one file are added once`() {
        val tea = Reminder(id = 1, name = "Tea")
        val result = Backup.merge(emptyList(), listOf(tea, tea.copy(id = 2, sortIndex = 5)))
        assertEquals(1, result.added)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `reminders that differ in content are not duplicates`() {
        val tea = Reminder(id = 1, name = "Tea")
        val result = Backup.merge(listOf(tea), listOf(tea.copy(id = 9, message = "Green")))
        assertEquals(1, result.added)
    }

    @Test
    fun `replacing renumbers the imported list in its order`() {
        val replaced = Backup.replace(listOf(stretch.copy(id = 40), water.copy(id = 40)))
        assertEquals(listOf(1, 2), replaced.map { it.id })
        assertEquals(listOf(0, 1), replaced.map { it.sortIndex })
        assertEquals(listOf("Stretch", "Water"), replaced.map { it.name })
    }

    private fun assertRejected(text: String, expected: BackupError) {
        try {
            Backup.decode(text, now)
            fail("Expected $expected for: ${text.take(80)}")
        } catch (e: BackupException) {
            assertEquals(expected, e.error)
        }
    }
}
