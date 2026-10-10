package com.olaf.rereminder.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.olaf.rereminder.R
import com.olaf.rereminder.data.AlertStyle
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.SoundChoice
import com.olaf.rereminder.data.SoundMode
import java.util.UUID

/**
 * One notification channel per sound.
 *
 * Android fixes a channel's sound and vibration the moment the channel is created — that is why
 * the sound picked in 3.x was never heard (#12). And since Android 17, an app in the background
 * may not play notification sounds itself either. So the system plays them, and every distinct
 * combination of tone and vibration gets a channel of its own: picking another tone simply means
 * posting to another channel. Channels nothing uses any more are removed again.
 *
 * Letting the system play the sound also means Do Not Disturb, silent and vibrate mode work the
 * way the user expects without the app second-guessing them.
 */
object SoundChannels {

    private const val TAG = "SoundChannels"
    private const val PREFIX = "sound_"

    /** The system's default notification sound — follows the user when they change it. */
    val defaultTone: Uri = Settings.System.DEFAULT_NOTIFICATION_URI

    /**
     * The channel that plays [tone] (null: no sound) and vibrates [vibration] (null: no
     * vibration). Without either, it is the plain silent reminder channel.
     */
    fun channelFor(context: Context, tone: Uri?, vibration: LongArray?): String {
        if (tone == null && vibration == null) return NotificationHelper.CHANNEL_REMINDERS
        val id = channelId(tone, vibration)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return id
        if (manager.getNotificationChannel(id) != null) return id

        val toneName = tone?.let { toneTitle(context, it) }
        val name = when {
            toneName != null && vibration != null -> context.getString(R.string.channel_sound_vibrate_name, toneName)
            toneName != null -> context.getString(R.string.channel_sound_name, toneName)
            else -> context.getString(R.string.channel_vibrate_only_name)
        }
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_sound_description)
            enableLights(true)
            if (tone != null) {
                setSound(
                    tone,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            } else {
                setSound(null, null)
            }
            if (vibration != null) {
                enableVibration(true)
                vibrationPattern = vibration
            } else {
                enableVibration(false)
            }
        }
        runCatching { manager.createNotificationChannel(channel) }
            .onFailure { Log.e(TAG, "Could not create channel for $tone", it) }
        return id
    }

    /** Removes sound channels that are not in [inUse] — left over from tones nobody picks now. */
    fun prune(context: Context, inUse: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notificationChannels
                .filter { it.id.startsWith(PREFIX) && it.id !in inUse }
                .forEach { manager.deleteNotificationChannel(it.id) }
        }.onFailure { Log.w(TAG, "Could not tidy up sound channels", it) }
    }

    /** How a notification-style alert sounds: a tone and vibration the channel plays, or speech. */
    class Plan(val tone: Uri?, val vibration: LongArray?, val speak: Boolean) {
        val channelId: String get() = if (tone == null && vibration == null) NotificationHelper.CHANNEL_REMINDERS else channelId(tone, vibration)
    }

    fun plan(reminder: Reminder, preferences: PreferenceHelper, early: Boolean): Plan {
        // An alarm's own tone is a long ringing loop; its early heads-up is a notification and
        // gets the notification default instead.
        val source = if (early && reminder.alertStyle == AlertStyle.ALARM && reminder.sound.mode == SoundMode.TONE) {
            reminder.copy(sound = SoundChoice())
        } else {
            reminder
        }
        val pattern = VibrationHelper.pattern(preferences.getVibrationPattern())
        // The top-bar mode wins over everything a reminder has set for itself.
        when (preferences.getAlertMode()) {
            AlertMode.MUTE -> return Plan(null, null, speak = false)
            AlertMode.VIBRATE -> return Plan(null, pattern, speak = false)
            AlertMode.SOUND -> Unit
        }
        val vibration = if (reminder.vibrationEnabled && preferences.isVibrationEnabled()) pattern else null
        return when (val sound = AlertPlayer.resolveSound(source, preferences, alarm = false, spokenText = "")) {
            AlertSound.Silent -> Plan(null, vibration, speak = false)
            is AlertSound.Tone -> Plan(sound.uri ?: defaultTone, vibration, speak = false)
            is AlertSound.Speech -> Plan(null, vibration, speak = true)
        }
    }

    /** Every channel the current reminders could post to, for [prune]. */
    fun channelsInUse(reminders: List<Reminder>, preferences: PreferenceHelper): Set<String> =
        reminders.flatMap { reminder ->
            listOf(false, true).map { early -> plan(reminder, preferences, early).channelId }
        }.toSet()

    fun channelId(tone: Uri?, vibration: LongArray?): String {
        val key = "${tone ?: "none"}|${vibration?.joinToString(",") ?: "none"}"
        return PREFIX + UUID.nameUUIDFromBytes(key.toByteArray()).toString().take(13)
    }

    private fun toneTitle(context: Context, uri: Uri): String? =
        if (uri == defaultTone) {
            context.getString(R.string.default_label)
        } else {
            runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
        }
}
