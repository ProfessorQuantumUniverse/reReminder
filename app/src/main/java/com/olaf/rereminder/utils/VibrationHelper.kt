package com.olaf.rereminder.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat

object VibrationHelper {
    
    fun vibrate(context: Context, pattern: Int) {
        try {
            // Prüfe Vibrations-Berechtigung
            if (!hasVibrationPermission(context)) {
                android.util.Log.w("VibrationHelper", "No vibration permission")
                return
            }

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // Prüfen ob Vibrator verfügbar ist
            if (!vibrator.hasVibrator()) {
                android.util.Log.w("VibrationHelper", "Device has no vibrator")
                return
            }

            val vibrationPattern = getVibrationPattern(pattern)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(vibrationPattern, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("VibrationHelper", "Security exception during vibration", e)
        } catch (e: Exception) {
            android.util.Log.e("VibrationHelper", "Error during vibration", e)
        }
    }

    fun vibrateOnce(context: Context, duration: Long = 500) {
        try {
            // Prüfe Vibrations-Berechtigung
            if (!hasVibrationPermission(context)) {
                android.util.Log.w("VibrationHelper", "No vibration permission")
                return
            }

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) {
                android.util.Log.w("VibrationHelper", "Device has no vibrator")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("VibrationHelper", "Security exception during vibration", e)
        } catch (e: Exception) {
            android.util.Log.e("VibrationHelper", "Error during vibration", e)
        }
    }

    private fun hasVibrationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.VIBRATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getVibrationPattern(pattern: Int): LongArray {
        return when (pattern) {
            0 -> longArrayOf(0, 200) // Kurz
            1 -> longArrayOf(0, 500, 200, 500) // Standard mit Wiederholung
            2 -> longArrayOf(0, 1000) // Lang
            3 -> longArrayOf(0, 300, 100, 300, 100, 300, 100, 300) // Pulsierend
            4 -> longArrayOf(0, 100, 50, 100, 50, 100, 200, 500) // Komplex
            else -> longArrayOf(0, 500, 200, 500) // Standard
        }
    }
}