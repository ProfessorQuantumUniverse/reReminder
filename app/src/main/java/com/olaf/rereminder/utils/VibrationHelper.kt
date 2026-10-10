package com.olaf.rereminder.utils

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object VibrationHelper {

    private const val TAG = "VibrationHelper"

    /** The patterns offered in Settings, by index. */
    fun pattern(index: Int): LongArray = when (index) {
        0 -> longArrayOf(0, 200)
        2 -> longArrayOf(0, 1000)
        3 -> longArrayOf(0, 300, 100, 300, 100, 300, 100, 300)
        else -> longArrayOf(0, 500, 200, 500)
    }

    /** An alarm's pattern: the chosen one, then a pause, over and over. */
    fun alarmPattern(index: Int): LongArray = pattern(index) + longArrayOf(800)

    /**
     * Vibrates [pattern] once, or until [cancel] when [repeat]. Alarm vibrations carry alarm
     * attributes so Do Not Disturb's alarm exception lets them through.
     */
    fun vibrate(context: Context, pattern: LongArray, repeat: Boolean = false, alarm: Boolean = false) {
        try {
            val vibrator = vibrator(context)
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(pattern, if (repeat) 0 else -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val usage = if (alarm) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_NOTIFICATION
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(usage))
            } else {
                val usage = if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(usage).build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not vibrate", e)
        }
    }

    fun cancel(context: Context) {
        runCatching { vibrator(context).cancel() }
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
}
