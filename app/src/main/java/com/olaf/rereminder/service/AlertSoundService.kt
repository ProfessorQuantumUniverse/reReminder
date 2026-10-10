package com.olaf.rereminder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.utils.AlertPlayer
import com.olaf.rereminder.utils.AlertSound
import com.olaf.rereminder.utils.NotificationHelper

/**
 * Reads a spoken reminder aloud.
 *
 * Android 17 mutes audio that background apps start. Tones avoid this by being played by the
 * system through their notification channel (see [com.olaf.rereminder.utils.SoundChannels]), but
 * speech has to come from the app. While this foreground service runs, the speech engine it binds
 * counts as in use and may play. The service borrows the reminder's own notification as its
 * foreground notification and detaches it again when done — the user sees the same single
 * notification as before, and it stays after the speech ends.
 */
class AlertSoundService : Service() {

    private val players = mutableSetOf<AlertPlayer>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_PLAY) {
            finishIfIdle()
            return START_NOT_STICKY
        }

        val reminderId = intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        // Deleted in the instant since the alarm fired: a placeholder still has to go
        // foreground, because startForegroundService() demands it whatever happens next.
        val reminder = ReminderRepository.get(this).get(reminderId)
            ?: com.olaf.rereminder.data.Reminder(id = reminderId)
        runCatching {
            val notification = NotificationHelper.buildReminderNotification(
                this,
                reminder,
                intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
                intent.getLongExtra(ReminderScheduler.EXTRA_EVENT_AT, 0L),
                intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET_MINUTES, 0),
                channelId = intent.getStringExtra(EXTRA_CHANNEL) ?: NotificationHelper.CHANNEL_REMINDERS,
                // The notification already rang when it was posted; going foreground must not ring again.
                onlyAlertOnce = true,
            )
            val id = NotificationHelper.reminderNotificationId(reminderId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(id, notification)
            }
        }.onFailure { Log.w(TAG, "Could not go foreground; the sound may be muted", it) }

        if (ReminderRepository.get(this).get(reminderId) == null) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            finishIfIdle()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        val player = AlertPlayer(
            context = this,
            usage = AudioAttributes.USAGE_NOTIFICATION,
            loop = false,
            speechUsage = intent.getIntExtra(EXTRA_SPEECH_USAGE, AudioAttributes.USAGE_NOTIFICATION),
        )
        players += player
        player.play(
            sound = intent.readSound(),
            vibration = null,
        ) {
            players -= player
            finishIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun finishIfIdle() {
        if (players.isNotEmpty()) return
        // Detach rather than remove: the notification is the reminder, and it stays.
        runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        players.toList().forEach { it.stop() }
        players.clear()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { acquire(AlertPlayer.DEFAULT_MAX_MILLIS + 10_000L) }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "AlertSoundService"
        private const val WAKE_LOCK_TAG = "reReminder:sound"
        private const val ACTION_PLAY = "com.olaf.rereminder.action.PLAY_ALERT"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_SOUND_KIND = "sound_kind"
        private const val EXTRA_SOUND_VALUE = "sound_value"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_SPEECH_USAGE = "speech_usage"

        /**
         * Hands the sound to the service. Returns false when Android would not start it; the
         * caller then plays from where it is, which may be muted on recent Android but is still
         * better than nothing.
         */
        fun start(
            context: Context,
            reminderId: Int,
            message: String,
            eventAt: Long,
            offsetMinutes: Int,
            sound: AlertSound,
            channelId: String,
            speechUsage: Int,
        ): Boolean {
            val intent = Intent(context, AlertSoundService::class.java).apply {
                action = ACTION_PLAY
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(ReminderScheduler.EXTRA_EVENT_AT, eventAt)
                putExtra(ReminderScheduler.EXTRA_OFFSET_MINUTES, offsetMinutes)
                when (sound) {
                    AlertSound.Silent -> putExtra(EXTRA_SOUND_KIND, KIND_SILENT)
                    is AlertSound.Tone -> {
                        putExtra(EXTRA_SOUND_KIND, KIND_TONE)
                        putExtra(EXTRA_SOUND_VALUE, sound.uri?.toString())
                    }

                    is AlertSound.Speech -> {
                        putExtra(EXTRA_SOUND_KIND, KIND_SPEECH)
                        putExtra(EXTRA_SOUND_VALUE, sound.text)
                    }
                }
                putExtra(EXTRA_CHANNEL, channelId)
                putExtra(EXTRA_SPEECH_USAGE, speechUsage)
            }
            return runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Sound service not allowed to start", it) }
                .isSuccess
        }

        private const val KIND_SILENT = "silent"
        private const val KIND_TONE = "tone"
        private const val KIND_SPEECH = "speech"

        private fun Intent.readSound(): AlertSound {
            val value = getStringExtra(EXTRA_SOUND_VALUE)
            return when (getStringExtra(EXTRA_SOUND_KIND)) {
                KIND_TONE -> AlertSound.Tone(value?.toUri())
                KIND_SPEECH -> AlertSound.Speech(value.orEmpty())
                else -> AlertSound.Silent
            }
        }
    }
}
