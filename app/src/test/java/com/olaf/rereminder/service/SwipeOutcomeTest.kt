package com.olaf.rereminder.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SwipeOutcomeTest {

    private val ringing = alarm(reminderId = 4, eventAt = 1_790_000_000_000L)

    @Test
    fun `a swipe on the ringing alarm dismisses it by default`() {
        assertEquals(SwipeOutcome.DISMISS, swipeOutcome(ringing, ringing.key, snoozeOnSwipe = false))
    }

    @Test
    fun `a swipe on the ringing alarm snoozes it when set to`() {
        assertEquals(SwipeOutcome.SNOOZE, swipeOutcome(ringing, ringing.key, snoozeOnSwipe = true))
    }

    @Test
    fun `a late swipe from an earlier occurrence of the same reminder is ignored`() {
        val earlier = alarm(reminderId = 4, eventAt = ringing.eventAt - 1)
        assertEquals(SwipeOutcome.IGNORE, swipeOutcome(ringing, earlier.key, snoozeOnSwipe = false))
        assertEquals(SwipeOutcome.IGNORE, swipeOutcome(ringing, earlier.key, snoozeOnSwipe = true))
    }

    @Test
    fun `a late swipe from another reminder is ignored`() {
        val other = alarm(reminderId = 5, eventAt = ringing.eventAt)
        assertEquals(SwipeOutcome.IGNORE, swipeOutcome(ringing, other.key, snoozeOnSwipe = false))
    }

    @Test
    fun `a swipe with nothing ringing or no key is ignored`() {
        assertEquals(SwipeOutcome.IGNORE, swipeOutcome(null, ringing.key, snoozeOnSwipe = false))
        assertEquals(SwipeOutcome.IGNORE, swipeOutcome(ringing, null, snoozeOnSwipe = false))
    }

    @Test
    fun `the key names the occurrence, not what the alarm shows`() {
        val retitled = ringing.copy(title = "Other", message = "Changed", snoozeMinutes = 30)
        assertEquals(ringing.key, retitled.key)
        assertNotEquals(ringing.key, ringing.copy(eventAt = ringing.eventAt + 60_000L).key)
    }

    private fun alarm(reminderId: Int, eventAt: Long) = RingingAlarm(
        reminderId = reminderId,
        title = "Stretch",
        message = "",
        eventAt = eventAt,
        colorIndex = 0,
        snoozeMinutes = 10,
    )
}
