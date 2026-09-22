package com.olaf.rereminder.ui.setup

import com.olaf.rereminder.utils.DkmaVendor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checklist must only ever list things this device can actually do — a step the user cannot
 * act on teaches them to ignore the whole screen.
 */
class SetupStateTest {

    private fun steps(
        needsNotificationPermission: Boolean = true,
        notificationsGranted: Boolean = false,
        supportsExactAlarmSetting: Boolean = true,
        exactAlarmsAllowed: Boolean = false,
        batteryUnrestricted: Boolean = false,
        vendor: DkmaVendor? = null,
        vendorGuideSeen: Boolean = false,
    ) = buildSetupSteps(
        needsNotificationPermission = needsNotificationPermission,
        notificationsGranted = notificationsGranted,
        supportsExactAlarmSetting = supportsExactAlarmSetting,
        exactAlarmsAllowed = exactAlarmsAllowed,
        batteryUnrestricted = batteryUnrestricted,
        vendor = vendor,
        vendorGuideSeen = vendorGuideSeen,
    )

    @Test
    fun `a modern phone on an aggressive vendor gets every step`() {
        val list = steps(vendor = DkmaVendor.XIAOMI).map { it.step }

        assertEquals(
            listOf(
                SetupStep.NOTIFICATIONS,
                SetupStep.EXACT_ALARMS,
                SetupStep.BATTERY,
                SetupStep.MANUFACTURER,
            ),
            list,
        )
    }

    @Test
    fun `no notification permission to ask for below Android 13`() {
        val list = steps(needsNotificationPermission = false).map { it.step }

        assertFalse(SetupStep.NOTIFICATIONS in list)
    }

    @Test
    fun `no exact-alarm screen below Android 12`() {
        val list = steps(supportsExactAlarmSetting = false).map { it.step }

        assertFalse(SetupStep.EXACT_ALARMS in list)
    }

    @Test
    fun `a well-behaved vendor gets no manufacturer step`() {
        assertFalse(SetupStep.MANUFACTURER in steps(vendor = DkmaVendor.GOOGLE).map { it.step })
        assertFalse(SetupStep.MANUFACTURER in steps(vendor = null).map { it.step })
    }

    @Test
    fun `only notifications hold setup back`() {
        val optionalOutstanding = SetupUiState(
            steps = steps(notificationsGranted = true, vendor = DkmaVendor.XIAOMI)
        )

        // Exact alarms, battery and the vendor guide are all still undone…
        assertFalse(optionalOutstanding.allDone)
        // …but none of them may trap the user on this screen.
        assertTrue(optionalOutstanding.canContinue)
    }

    @Test
    fun `setup cannot be finished without notifications`() {
        assertFalse(SetupUiState(steps = steps(notificationsGranted = false)).canContinue)
    }

    @Test
    fun `the current step is the first unfinished one`() {
        val state = SetupUiState(
            steps = steps(notificationsGranted = true, exactAlarmsAllowed = true)
        )

        assertEquals(SetupStep.BATTERY, state.currentStep)
    }

    @Test
    fun `a fully configured device has nothing left to do`() {
        val state = SetupUiState(
            steps = steps(
                notificationsGranted = true,
                exactAlarmsAllowed = true,
                batteryUnrestricted = true,
                vendor = DkmaVendor.XIAOMI,
                vendorGuideSeen = true,
            )
        )

        assertTrue(state.allDone)
        assertTrue(state.canContinue)
        assertNull(state.currentStep)
        assertEquals(4, state.doneCount)
    }

    @Test
    fun `opening the vendor guide is what completes that step`() {
        val before = steps(vendor = DkmaVendor.HUAWEI, vendorGuideSeen = false)
        val after = steps(vendor = DkmaVendor.HUAWEI, vendorGuideSeen = true)

        assertFalse(before.first { it.step == SetupStep.MANUFACTURER }.done)
        assertTrue(after.first { it.step == SetupStep.MANUFACTURER }.done)
    }
}
