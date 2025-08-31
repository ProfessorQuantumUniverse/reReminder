package com.olaf.rereminder.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class PreferenceHelper(context: Context) {
    
    private val preferences: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    fun isReminderEnabled(): Boolean = 
        preferences.getBoolean(KEY_REMINDER_ENABLED, false)
    
    fun setReminderEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }
    
    fun getReminderInterval(): Int = 
        preferences.getInt(KEY_REMINDER_INTERVAL, DEFAULT_INTERVAL)
    
    fun setReminderInterval(minutes: Int) {
        preferences.edit().putInt(KEY_REMINDER_INTERVAL, minutes).apply()
    }
    
    fun getSelectedRingtone(): Uri? {
        val uriString = preferences.getString(KEY_SELECTED_RINGTONE, null)
        return if (uriString != null) Uri.parse(uriString) else null
    }
    
    fun setSelectedRingtone(uri: Uri?) {
        preferences.edit().putString(KEY_SELECTED_RINGTONE, uri?.toString()).apply()
    }
    
    fun isSoundEnabled(): Boolean = 
        preferences.getBoolean(KEY_SOUND_ENABLED, true)
    
    fun setSoundEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }
    
    fun isVibrationEnabled(): Boolean = 
        preferences.getBoolean(KEY_VIBRATION_ENABLED, true)
    
    fun setVibrationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }
    
    fun getVibrationPattern(): Int = 
        preferences.getInt(KEY_VIBRATION_PATTERN, 1)
    
    fun setVibrationPattern(pattern: Int) {
        preferences.edit().putInt(KEY_VIBRATION_PATTERN, pattern).apply()
    }
    
    companion object {
        private const val PREF_NAME = "reminder_preferences"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_INTERVAL = "reminder_interval"
        private const val KEY_SELECTED_RINGTONE = "selected_ringtone"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_VIBRATION_PATTERN = "vibration_pattern"
        private const val DEFAULT_INTERVAL = 60 // 1 Stunde
    }
}