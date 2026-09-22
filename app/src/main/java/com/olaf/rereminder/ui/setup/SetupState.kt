package com.olaf.rereminder.ui.setup

import com.olaf.rereminder.R
import com.olaf.rereminder.utils.DkmaVendor

/**
 * One thing the user is walked through before the app is usable.
 *
 * Ordered by how badly the app needs it: without notifications a reminder cannot appear at all,
 * without exact alarms it appears late, without a battery exemption it may not appear at all on
 * a sleeping phone, and the manufacturer guide is advice rather than a switch.
 */
enum class SetupStep(
    val titleRes: Int,
    val bodyRes: Int,
    val actionRes: Int,
    /** A reminder simply cannot work without it, so setup will not report itself finished. */
    val required: Boolean,
) {
    NOTIFICATIONS(
        titleRes = R.string.setup_notifications_title,
        bodyRes = R.string.setup_notifications_body,
        actionRes = R.string.setup_notifications_action,
        required = true,
    ),
    EXACT_ALARMS(
        titleRes = R.string.setup_alarms_title,
        bodyRes = R.string.setup_alarms_body,
        actionRes = R.string.setup_alarms_action,
        required = false,
    ),
    BATTERY(
        titleRes = R.string.setup_battery_title,
        bodyRes = R.string.setup_battery_body,
        actionRes = R.string.setup_battery_action,
        required = false,
    ),
    MANUFACTURER(
        titleRes = R.string.setup_vendor_title,
        bodyRes = R.string.setup_vendor_body,
        actionRes = R.string.setup_vendor_action,
        required = false,
    ),
}

data class SetupStepState(
    val step: SetupStep,
    val done: Boolean,
)

/**
 * The guided first-run checklist.
 *
 * [steps] only ever contains what this device actually needs: a phone on Android 11 has no
 * notification permission to ask for, one from a well-behaved manufacturer gets no vendor step.
 * A checklist that lists things the user cannot act on teaches them to ignore it.
 */
data class SetupUiState(
    val steps: List<SetupStepState> = emptyList(),
    val vendor: DkmaVendor? = null,
    val vendorName: String = "",
) {
    /** The first unfinished step — the one the screen expands and highlights. */
    val currentStep: SetupStep? get() = steps.firstOrNull { !it.done }?.step

    /** Everything the app cannot work without is in place. */
    val canContinue: Boolean
        get() = steps.none { it.step.required && !it.done }

    /** Nothing left to do at all, so the screen can bow out by itself. */
    val allDone: Boolean get() = steps.all { it.done }

    val doneCount: Int get() = steps.count { it.done }

    fun isDone(step: SetupStep): Boolean = steps.firstOrNull { it.step == step }?.done == true
}

/**
 * Builds the checklist for the conditions passed in.
 *
 * Kept free of Android types so the branching — which steps appear, when setup counts as
 * finished — can be tested without a device.
 */
fun buildSetupSteps(
    needsNotificationPermission: Boolean,
    notificationsGranted: Boolean,
    supportsExactAlarmSetting: Boolean,
    exactAlarmsAllowed: Boolean,
    batteryUnrestricted: Boolean,
    vendor: DkmaVendor?,
    vendorGuideSeen: Boolean,
): List<SetupStepState> = buildList {
    if (needsNotificationPermission) {
        add(SetupStepState(SetupStep.NOTIFICATIONS, notificationsGranted))
    }
    if (supportsExactAlarmSetting) {
        add(SetupStepState(SetupStep.EXACT_ALARMS, exactAlarmsAllowed))
    }
    add(SetupStepState(SetupStep.BATTERY, batteryUnrestricted))
    // Only for the manufacturers that actually break background alarms; there is nothing to
    // detect afterwards, so opening the guide once is what counts as done.
    if (vendor?.aggressive == true) {
        add(SetupStepState(SetupStep.MANUFACTURER, vendorGuideSeen))
    }
}
