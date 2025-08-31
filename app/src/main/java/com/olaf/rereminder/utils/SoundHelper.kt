package com.olaf.rereminder.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

object SoundHelper {
    
    private var mediaPlayer: MediaPlayer? = null
    private const val TAG = "SoundHelper"

    fun playRingtone(context: Context, ringtoneUri: Uri?) {
        try {
            stopRingtone()
            
            // Prüfen ob Audio verfügbar ist
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) {
                Log.w(TAG, "AudioManager not available")
                return
            }

            if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                Log.d(TAG, "Device is in silent mode, skipping sound")
                return
            }

            val uri = ringtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                try {
                    // Audio-Attribute setzen
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
                    }

                    setDataSource(context, uri)
                    prepareAsync()

                    setOnPreparedListener { player ->
                        try {
                            player.start()
                            Log.d(TAG, "Sound started playing")
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, "Error starting playback", e)
                        }
                    }

                    setOnCompletionListener { player ->
                        try {
                            player.release()
                            mediaPlayer = null
                            Log.d(TAG, "Sound finished playing")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in completion listener", e)
                        }
                    }

                    setOnErrorListener { player, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        try {
                            player.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing player", e)
                        }
                        mediaPlayer = null

                        // Fallback: versuche mit Standard-Notification-Sound
                        if (ringtoneUri != null) {
                            playRingtone(context, null)
                        }
                        true
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception setting up MediaPlayer", e)
                    release()
                    mediaPlayer = null
                } catch (e: Exception) {
                    Log.e(TAG, "Exception setting up MediaPlayer", e)
                    release()
                    mediaPlayer = null
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception playing ringtone", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing ringtone", e)
        }
    }
    
    fun stopRingtone() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                Log.d(TAG, "Sound stopped")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "MediaPlayer already released", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ringtone", e)
            } finally {
                mediaPlayer = null
            }
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "MediaPlayer in illegal state", e)
            false
        }
    }
}