package com.olaf.rereminder.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.util.Log
import java.io.File

/**
 * Where reminder data lives: device-protected storage.
 *
 * Credential-protected storage (the default) stays locked after a reboot until the user unlocks
 * the phone — and phones like to reboot at night for updates. Keeping the reminders and settings
 * in device-protected storage lets the boot receiver re-arm them straight after a reboot, so the
 * 07:00 reminder still rings on a phone that restarted at 03:00 and has not been unlocked yet.
 *
 * There is nothing secret in a list of reminders, which is exactly the case device-protected
 * storage is meant for.
 */
object Storage {

    private const val TAG = "Storage"
    const val PREF_NAME = "reminder_preferences"
    private const val LOCKED_SCRATCH_NAME = "reminder_preferences_locked"
    private const val MIGRATED_MARKER = "storage_v2_migrated"

    @Volatile
    private var migrated = false

    fun deviceContext(context: Context): Context =
        context.applicationContext.createDeviceProtectedStorageContext()

    fun isUserUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    /**
     * Moves the 3.x preferences file into device-protected storage the first time that is
     * possible. Returns false only while the phone is still locked and the move has not happened —
     * the data is unreachable until then, and callers must not mistake that for "no data".
     */
    @Synchronized
    fun ensureMigrated(context: Context): Boolean {
        if (migrated) return true
        val device = deviceContext(context)
        // A marker file rather than a preference: reading the device preferences before the move
        // would cache an empty instance that the move then cannot refresh.
        val marker = File(device.noBackupFilesDir, MIGRATED_MARKER)
        if (marker.exists()) {
            migrated = true
            return true
        }
        if (!isUserUnlocked(context)) return false

        val moved = runCatching {
            device.moveSharedPreferencesFrom(context.applicationContext, PREF_NAME)
        }.onFailure { Log.e(TAG, "Could not move preferences to device storage", it) }
            .getOrDefault(false)
        if (!moved) {
            // Nothing is lost: the old file stays where it is and the move is retried next time.
            return false
        }
        runCatching { marker.createNewFile() }
            .onFailure { Log.e(TAG, "Could not write the migration marker", it) }
        migrated = true
        return true
    }

    /**
     * The app's preferences in device-protected storage.
     *
     * While the phone is still locked and the 3.x file has not been moved yet, this hands out a
     * separate scratch file instead: opening the real name now would cache an empty instance that
     * the later move could not refresh, and the next write would then overwrite the moved data.
     */
    fun preferences(context: Context): SharedPreferences {
        val name = if (ensureMigrated(context)) PREF_NAME else LOCKED_SCRATCH_NAME
        return deviceContext(context).getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun filesDir(context: Context): File = deviceContext(context).filesDir
}
