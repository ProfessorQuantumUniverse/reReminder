package com.olaf.rereminder.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlarmOutcomeTest {

    private val ringing = alarm(reminderId = 4, eventAt = 1_790_000_000_000L)
    private val earlier = alarm(reminderId = 4, eventAt = ringing.eventAt - 1)
    private val other = alarm(reminderId = 5, eventAt = ringing.eventAt)

    @Test
    fun `the buttons act on the alarm they were shown for`() {
        assertEquals(AlarmOutcome.DISMISS, outcome(AlarmAnswer.DISMISS, ringing.key))
        assertEquals(AlarmOutcome.SNOOZE, outcome(AlarmAnswer.SNOOZE, ringing.key))
    }

    @Test
    fun `a late button tap from an alarm that was taken over is ignored`() {
        for (stale in listOf(earlier, other)) {
            assertEquals(AlarmOutcome.IGNORE, outcome(AlarmAnswer.DISMISS, stale.key))
            assertEquals(AlarmOutcome.IGNORE, outcome(AlarmAnswer.SNOOZE, stale.key))
        }
    }

    @Test
    fun `a button without a key goes to whatever rings`() {
        assertEquals(AlarmOutcome.DISMISS, outcome(AlarmAnswer.DISMISS, null))
        assertEquals(AlarmOutcome.SNOOZE, outcome(AlarmAnswer.SNOOZE, null))
    }

    @Test
    fun `a swipe on the ringing alarm dismisses it by default`() {
        assertEquals(AlarmOutcome.DISMISS, outcome(AlarmAnswer.SWIPE, ringing.key))
    }

    @Test
    fun `a swipe on the ringing alarm snoozes it when set to`() {
        assertEquals(AlarmOutcome.SNOOZE, outcome(AlarmAnswer.SWIPE, ringing.key, snoozeOnSwipe = true))
    }

    @Test
    fun `a late swipe from an alarm that was taken over is ignored`() {
        for (stale in listOf(earlier, other)) {
            assertEquals(AlarmOutcome.IGNORE, outcome(AlarmAnswer.SWIPE, stale.key))
            assertEquals(AlarmOutcome.IGNORE, outcome(AlarmAnswer.SWIPE, stale.key, snoozeOnSwipe = true))
        }
    }

    @Test
    fun `a swipe without a key is ignored`() {
        assertEquals(AlarmOutcome.IGNORE, outcome(AlarmAnswer.SWIPE, null))
    }

    @Test
    fun `nothing ringing means nothing to do`() {
        for (answer in AlarmAnswer.entries) {
            assertEquals(AlarmOutcome.IGNORE, alarmOutcome(answer, null, ringing.key, snoozeOnSwipe = true))
            assertEquals(AlarmOutcome.IGNORE, alarmOutcome(answer, null, null, snoozeOnSwipe = false))
        }
    }

    @Test
    fun `the key names the occurrence, not what the alarm shows`() {
        val retitled = ringing.copy(title = "Other", message = "Changed", snoozeMinutes = 30)
        assertEquals(ringing.key, retitled.key)
        assertNotEquals(ringing.key, earlier.key)
        assertNotEquals(ringing.key, other.key)
    }

    private fun outcome(answer: AlarmAnswer, targetKey: String?, snoozeOnSwipe: Boolean = false) =
        alarmOutcome(answer, ringing, targetKey, snoozeOnSwipe)

    private fun alarm(reminderId: Int, eventAt: Long) = RingingAlarm(
        reminderId = reminderId,
        title = "Stretch",
        message = "",
        eventAt = eventAt,
        colorIndex = 0,
        snoozeMinutes = 10,
    )
}
