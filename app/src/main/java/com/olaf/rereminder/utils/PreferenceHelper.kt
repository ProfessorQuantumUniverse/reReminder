package com.olaf.rereminder.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import com.olaf.rereminder.data.Storage

/**
 * App-wide alert settings. Anything that varies per timer lives on
 * [com.olaf.rereminder.data.Reminder] instead.
 */
class PreferenceHelper(context: Context) {

    private val preferences: SharedPreferences = Storage.preferences(context)

    /**
     * Global pause. Turning this off silences every timer without touching each timer's own
     * enabled flag, so flipping it back on restores exactly what was running before.
     */
    fun isMasterEnabled(): Boolean =
        preferences.getBoolean(KEY_MASTER_ENABLED, true)

    fun setMasterEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_MASTER_ENABLED, enabled) }
    }

    /** The default sound for notification-style reminders; null means the system default. */
    fun getSelectedRingtone(): Uri? =
        preferences.getString(KEY_SELECTED_RINGTONE, null)?.toUri()

    fun setSelectedRingtone(uri: Uri?) {
        preferences.edit { putString(KEY_SELECTED_RINGTONE, uri?.toString()) }
    }

    /** The default sound for alarm-style reminders; null means the system alarm sound. */
    fun getAlarmTone(): Uri? =
        preferences.getString(KEY_ALARM_TONE, null)?.toUri()

    fun setAlarmTone(uri: Uri?) {
        preferences.edit { putString(KEY_ALARM_TONE, uri?.toString()) }
    }

    fun isVibrationEnabled(): Boolean =
        preferences.getBoolean(KEY_VIBRATION_ENABLED, true)

    fun setVibrationEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_VIBRATION_ENABLED, enabled) }
    }

    fun getVibrationPattern(): Int =
        preferences.getInt(KEY_VIBRATION_PATTERN, 1).coerceIn(0, VIBRATION_PATTERN_COUNT - 1)

    fun setVibrationPattern(pattern: Int) {
        preferences.edit { putInt(KEY_VIBRATION_PATTERN, pattern) }
    }

    /**
     * The one-tap mode from the list's top bar: everything alerts as set, everything only
     * vibrates, or everything stays silent. It overrides each reminder's own sound — alarms
     * included — and is the quick way to quiet the app for a meeting.
     */
    fun getAlertMode(): AlertMode =
        runCatching { AlertMode.valueOf(preferences.getString(KEY_ALERT_MODE, null) ?: "") }
            .getOrDefault(AlertMode.SOUND)

    fun setAlertMode(mode: AlertMode) {
        preferences.edit { putString(KEY_ALERT_MODE, mode.name) }
    }

    /** What the global sound settings of 3.x asked for, before each reminder chose its own. */
    data class LegacySoundDefaults(val speak: Boolean, val muted: Boolean)

    /**
     * The old global "Sound enabled" and "Sound or text-to-speech" settings, until they have been
     * carried over onto the reminders ([markLegacySoundDefaultsMoved]). Null when there is
     * nothing to carry over.
     */
    fun legacySoundDefaults(): LegacySoundDefaults? {
        if (preferences.getBoolean(KEY_SOUND_DEFAULTS_MOVED, false)) return null
        val speak = preferences.getString(KEY_NOTIFICATION_SOUND_TYPE, null) == SOUND_TYPE_TTS
        val muted = !preferences.getBoolean(KEY_SOUND_ENABLED, true)
        return LegacySoundDefaults(speak, muted)
    }

    fun markLegacySoundDefaultsMoved() {
        // Pin the speech volume first: whether this is an upgrade is read from these keys.
        getSpeechStream()
        preferences.edit {
            remove(KEY_NOTIFICATION_SOUND_TYPE)
            remove(KEY_SOUND_ENABLED)
            putBoolean(KEY_SOUND_DEFAULTS_MOVED, true)
        }
    }

    /**
     * Which volume spoken reminders follow.
     *
     * New installs speak at notification volume, so a muted video doesn't swallow the reminder.
     * People upgrading from 3.x heard speech at media volume until now; they keep that until
     * they change it, rather than having their reminders suddenly get louder or quieter.
     */
    fun getSpeechStream(): String {
        preferences.getString(KEY_SPEECH_STREAM, null)?.let { return it }
        val upgrading = preferences.contains(KEY_SETUP_COMPLETE) ||
            preferences.contains(KEY_DKMA_SHOWN) ||
            preferences.contains(KEY_NOTIFICATION_SOUND_TYPE)
        val initial = if (upgrading) STREAM_MEDIA else STREAM_NOTIFICATION
        preferences.edit { putString(KEY_SPEECH_STREAM, initial) }
        return initial
    }

    fun setSpeechStream(stream: String) {
        preferences.edit { putString(KEY_SPEECH_STREAM, stream) }
    }

    fun getSnoozeMinutes(): Int =
        preferences.getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)

    fun setSnoozeMinutes(minutes: Int) {
        preferences.edit { putInt(KEY_SNOOZE_MINUTES, minutes) }
    }

    fun isCompactList(): Boolean =
        preferences.getBoolean(KEY_COMPACT_LIST, false)

    fun setCompactList(compact: Boolean) {
        preferences.edit { putBoolean(KEY_COMPACT_LIST, compact) }
    }

    fun getSortMode(): String =
        preferences.getString(KEY_SORT_MODE, SORT_CUSTOM) ?: SORT_CUSTOM

    fun setSortMode(mode: String) {
        preferences.edit { putString(KEY_SORT_MODE, mode) }
    }

    /**
     * Whether the reliability notice for exactly this set of problems has already been seen.
     *
     * Remembering the signature rather than a plain "shown" flag means a problem that appears
     * later — the user revoking exact alarms, say — still gets surfaced once, while the same
     * warning is never shown twice.
     */
    fun isReliabilityPromptDismissed(signature: String, vendorOnly: Boolean): Boolean {
        if (preferences.getBoolean(KEY_RELIABILITY_SILENCED, false)) return true
        if (preferences.getString(KEY_RELIABILITY_SIGNATURE, null) == signature) return true
        // Users upgrading from 3.0 already dismissed the old manufacturer-only notice; don't
        // greet them with it again just because it is stored under a different key now.
        return vendorOnly && preferences.getBoolean(KEY_DKMA_SHOWN, false)
    }

    fun setReliabilityPromptDismissed(signature: String) {
        preferences.edit { putString(KEY_RELIABILITY_SIGNATURE, signature) }
    }

    /**
     * Whether the guided first run has been through.
     *
     * Users upgrading from 3.0 who already granted notifications and dismissed the old
     * manufacturer notice have effectively done it, so they are not marched through a wizard for
     * settings they have already made.
     */
    fun isSetupComplete(notificationsGranted: Boolean): Boolean =
        preferences.getBoolean(KEY_SETUP_COMPLETE, false) ||
            (notificationsGranted && preferences.getBoolean(KEY_DKMA_SHOWN, false))

    fun setSetupComplete() {
        preferences.edit { putBoolean(KEY_SETUP_COMPLETE, true) }
    }

    /** The manufacturer guide has no system flag, so opening it once is what counts. */
    fun isVendorGuideSeen(): Boolean =
        preferences.getBoolean(KEY_VENDOR_GUIDE_SEEN, false)

    fun setVendorGuideSeen() {
        preferences.edit { putBoolean(KEY_VENDOR_GUIDE_SEEN, true) }
    }

    /** "Don't show again" — suppresses the notice whatever changes later. */
    fun silenceReliabilityPrompt() {
        preferences.edit { putBoolean(KEY_RELIABILITY_SILENCED, true) }
    }

    companion object {
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_SELECTED_RINGTONE = "selected_ringtone"
        private const val KEY_ALARM_TONE = "alarm_tone"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_VIBRATION_PATTERN = "vibration_pattern"
        private const val KEY_NOTIFICATION_SOUND_TYPE = "notification_sound_type"
        private const val KEY_SPEECH_STREAM = "speech_stream"
        private const val KEY_ALERT_MODE = "alert_mode"
        private const val KEY_SOUND_DEFAULTS_MOVED = "sound_defaults_moved_to_reminders"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_COMPACT_LIST = "compact_list"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_DKMA_SHOWN = "dontkillmyapp_shown"
        private const val KEY_RELIABILITY_SIGNATURE = "reliability_prompt_signature"
        private const val KEY_RELIABILITY_SILENCED = "reliability_prompt_silenced"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_VENDOR_GUIDE_SEEN = "vendor_guide_seen"

        private const val SOUND_TYPE_TTS = "tts"

        const val STREAM_MEDIA = "media"
        const val STREAM_NOTIFICATION = "notification"
        const val STREAM_ALARM = "alarm"

        const val SORT_CUSTOM = "custom"
        const val SORT_NEXT = "next"

        const val DEFAULT_SNOOZE_MINUTES = 10
        val SNOOZE_CHOICES = listOf(5, 10, 15, 30)

        const val VIBRATION_PATTERN_COUNT = 4
    }
}

/** How every reminder alerts, cycled from the list's top bar. */
enum class AlertMode {
    SOUND,
    VIBRATE,
    MUTE;

    fun next(): AlertMode = entries[(ordinal + 1) % entries.size]
}
