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
        private const val PREF_NAME = "reminder_preferences"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_SELECTED_RINGTONE = "selected_ringtone"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_VIBRATION_PATTERN = "vibration_pattern"
        private const val KEY_NOTIFICATION_SOUND_TYPE = "notification_sound_type"
        private const val KEY_DKMA_SHOWN = "dontkillmyapp_shown"
        private const val KEY_RELIABILITY_SIGNATURE = "reliability_prompt_signature"
        private const val KEY_RELIABILITY_SILENCED = "reliability_prompt_silenced"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_VENDOR_GUIDE_SEEN = "vendor_guide_seen"

        const val SOUND_TYPE_RINGTONE = "ringtone"
        const val SOUND_TYPE_TTS = "tts"
    }
}
