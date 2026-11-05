package com.olaf.rereminder.service

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.olaf.rereminder.MainActivity
import com.olaf.rereminder.data.AlertStyle
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.data.Storage
import com.olaf.rereminder.ui.settings.SettingsViewModel
import com.olaf.rereminder.utils.AlertMode
import com.olaf.rereminder.utils.NotificationHelper
import com.olaf.rereminder.utils.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fires the ringing alarm notification's real delete intent, end to end. Which swipes count is
 * decided in [swipeOutcome] and covered by `SwipeOutcomeTest`.
 *
 * Works on the app's real data: it adds a reminder and changes settings, and puts both back
 * afterwards. Run it on an emulator, not on a phone in daily use.
 */
@RunWith(AndroidJUnit4::class)
class AlarmNotificationSwipeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceHelper(context)
    private val repository = ReminderRepository.get(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val createdIds = mutableListOf<Int>()
    private lateinit var activity: ActivityScenario<MainActivity>
    private var originalMode = AlertMode.SOUND
    private var originalSnooze = PreferenceHelper.DEFAULT_SNOOZE_MINUTES
    private var originalMasterEnabled = true
    private var originalSwipe: Boolean? = null

    @Before
    fun setUp() {
        assertNull("Run on a device with no active alarm", AlarmService.ringing.value)
        originalMode = preferences.getAlertMode()
        originalSnooze = preferences.getSnoozeMinutes()
        originalMasterEnabled = preferences.isMasterEnabled()
        val stored = Storage.preferences(context)
        originalSwipe = if (stored.contains(SWIPE_KEY)) preferences.isSnoozeOnAlarmSwipe() else null
        stored.edit { remove(SWIPE_KEY) }
        preferences.setAlertMode(AlertMode.MUTE)
        preferences.setSnoozeMinutes(10)
        preferences.setMasterEnabled(true)
        // Some OEM builds (e.g. ColorOS) block shell permission grants, so only ask when needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        activity = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        AlarmService.send(context, AlarmService.ACTION_DISMISS)
        await("alarm stopped") { AlarmService.ringing.value == null }
        createdIds.forEach {
            ReminderScheduler(context).cancel(it)
            repository.delete(it)
            NotificationHelper.cancelReminderNotification(context, it)
        }
        preferences.setAlertMode(originalMode)
        preferences.setSnoozeMinutes(originalSnooze)
        preferences.setMasterEnabled(originalMasterEnabled)
        val swipe = originalSwipe
        if (swipe == null) {
            Storage.preferences(context).edit { remove(SWIPE_KEY) }
        } else {
            preferences.setSnoozeOnAlarmSwipe(swipe)
        }
        if (::activity.isInitialized) activity.close()
    }

    @Test
    fun swipeDismissesByDefault() {
        assertFalse(preferences.isSnoozeOnAlarmSwipe())
        val reminder = addReminder()
        val notification = ring(reminder.id, System.currentTimeMillis())

        notification.send()

        await("swipe dismissed alarm") { AlarmService.ringing.value == null }
        await("foreground notification removed") { alarmNotification() == null }
        assertEquals(0L, repository.get(reminder.id)?.snoozeUntil)
    }

    @Test
    fun swipeSnoozesForConfiguredDuration() {
        preferences.setSnoozeOnAlarmSwipe(true)
        val reminder = addReminder()
        val notification = ring(reminder.id, System.currentTimeMillis())
        val beforeSwipe = System.currentTimeMillis()

        notification.send()

        await("swipe snoozed alarm") { AlarmService.ringing.value == null }
        val afterSwipe = System.currentTimeMillis()
        val snoozeUntil = requireNotNull(repository.get(reminder.id)).snoozeUntil
        assertTrue(snoozeUntil >= beforeSwipe + 10 * 60_000L)
        assertTrue(snoozeUntil <= afterSwipe + 10 * 60_000L)
        await("foreground notification removed") { alarmNotification() == null }
    }

    @Test
    fun settingsPersistsSwipeChoice() {
        val application = context.applicationContext as Application
        val viewModel = SettingsViewModel(application)
        assertFalse(viewModel.uiState.value.snoozeOnAlarmSwipe)

        viewModel.setSnoozeOnAlarmSwipe(true)

        assertTrue(PreferenceHelper(context).isSnoozeOnAlarmSwipe())
        assertTrue(SettingsViewModel(application).uiState.value.snoozeOnAlarmSwipe)
        viewModel.setSnoozeOnAlarmSwipe(false)
        assertFalse(PreferenceHelper(context).isSnoozeOnAlarmSwipe())
        assertFalse(viewModel.uiState.value.snoozeOnAlarmSwipe)
    }

    private fun addReminder(): Reminder =
        repository.add(Reminder(id = 0, name = "Swipe regression test", alertStyle = AlertStyle.ALARM))
            .also { createdIds += it.id }

    private fun ring(id: Int, eventAt: Long): PendingIntent {
        assertTrue(AlarmService.start(context, id, eventAt))
        await("alarm ringing with a notification") {
            AlarmService.ringing.value?.eventAt == eventAt && alarmNotification() != null
        }
        val deleteIntent = alarmNotification()?.deleteIntent
        assertNotNull("Ringing notification must handle swipes", deleteIntent)
        return requireNotNull(deleteIntent)
    }

    private fun alarmNotification() =
        manager.activeNotifications.firstOrNull { it.id == NotificationHelper.ALARM_NOTIFICATION_ID }
            ?.notification

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000L
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(25)
        }
        assertTrue(description, condition())
    }

    private companion object {
        const val SWIPE_KEY = "snooze_on_alarm_swipe"
    }
}
