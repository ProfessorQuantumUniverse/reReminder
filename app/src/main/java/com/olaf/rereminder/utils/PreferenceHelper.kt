package com.olaf.rereminder.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * App-wide alert settings. Anything that varies per timer lives on
 * [com.olaf.rereminder.data.Reminder] instead.
 */
class PreferenceHelper(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Global pause. Turning this off silences every timer without touching each timer's own
     * enabled flag, so flipping it back on restores exactly what was running before.
     */
    fun isMasterEnabled(): Boolean =
        preferences.getBoolean(KEY_MASTER_ENABLED, true)

    fun setMasterEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_MASTER_ENABLED, enabled) }
    }

    fun getSelectedRingtone(): Uri? =
        preferences.getString(KEY_SELECTED_RINGTONE, null)?.toUri()

    fun setSelectedRingtone(uri: Uri?) {
        preferences.edit { putString(KEY_SELECTED_RINGTONE, uri?.toString()) }
    }

    fun isSoundEnabled(): Boolean =
        preferences.getBoolean(KEY_SOUND_ENABLED, true)

    fun setSoundEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SOUND_ENABLED, enabled) }
    }

    fun isVibrationEnabled(): Boolean =
        preferences.getBoolean(KEY_VIBRATION_ENABLED, true)

    fun setVibrationEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_VIBRATION_ENABLED, enabled) }
    }

    fun getVibrationPattern(): Int =
        preferences.getInt(KEY_VIBRATION_PATTERN, 1)

    fun setVibrationPattern(pattern: Int) {
        preferences.edit { putInt(KEY_VIBRATION_PATTERN, pattern) }
    }

    fun getNotificationSoundType(): String =
        preferences.getString(KEY_NOTIFICATION_SOUND_TYPE, SOUND_TYPE_RINGTONE) ?: SOUND_TYPE_RINGTONE

    fun setNotificationSoundType(type: String) {
        preferences.edit { putString(KEY_NOTIFICATION_SOUND_TYPE, type) }
    }

    /** Flag so the "don't kill my app" notice is only shown once. */
    fun isDontKillMyAppWarningShown(): Boolean =
        preferences.getBoolean(KEY_DKMA_SHOWN, false)

    fun setDontKillMyAppWarningShown(shown: Boolean = true) {
        preferences.edit { putBoolean(KEY_DKMA_SHOWN, shown) }
    }

    companion object {
        private const val PREF_NAME = "reminder_preferences"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_SELECTED_RINGTONE = "selected_ringtone"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_VIBRATION_PATTERN = "vibration_pattern"
        private const val KEY_NOTIFICATION_SOUND_TYPE = "notification_sound_type"
        private const val KEY_DKMA_SHOWN = "dontkillmyapp_shown"

        const val SOUND_TYPE_RINGTONE = "ringtone"
        const val SOUND_TYPE_TTS = "tts"
    }
}
