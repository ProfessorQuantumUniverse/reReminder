package com.olaf.rereminder.utils

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.net.toUri
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.SoundMode
import java.util.Locale

/** What an alert sounds like, once the reminder's choice and Settings have been combined. */
sealed interface AlertSound {
    data object Silent : AlertSound
    /** A ringtone; null means the system default for the alert's style. */
    data class Tone(val uri: Uri?) : AlertSound
    data class Speech(val text: String) : AlertSound
}

/**
 * Plays one alert's sound and vibration.
 *
 * One instance per alert, unlike the singletons it replaced: two reminders firing in the same
 * minute no longer cut each other off, and every player releases its MediaPlayer, TextToSpeech
 * engine and audio focus when it is done. [onFinished] runs exactly once, after the sound ended,
 * failed or hit its time limit — the caller holds the process awake until then.
 *
 * Must be used from the main thread; all callbacks arrive there.
 */
class AlertPlayer(
    context: Context,
    /** [AudioAttributes.USAGE_NOTIFICATION] for reminders, [AudioAttributes.USAGE_ALARM] for alarms. */
    private val usage: Int,
    /** Alarms repeat until stopped; reminders play once. */
    private val loop: Boolean,
    /** Spoken reminders follow the stream chosen in Settings (#9). */
    private val speechUsage: Int = usage,
    private val maxMillis: Long = DEFAULT_MAX_MILLIS,
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = this.context.getSystemService(AudioManager::class.java)

    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private var onFinished: (() -> Unit)? = null
    private var finished = false

    fun play(sound: AlertSound, vibration: LongArray?, onFinished: () -> Unit) {
        this.onFinished = onFinished
        handler.postDelayed({ finish() }, maxMillis)

        vibration?.let { VibrationHelper.vibrate(context, it, repeat = loop, alarm = usage == AudioAttributes.USAGE_ALARM) }

        when (sound) {
            AlertSound.Silent -> {
                // Let a looping vibration run until stopped; otherwise there is nothing to wait for.
                if (!loop) finish()
            }

            is AlertSound.Tone -> playTone(sound.uri)
            is AlertSound.Speech -> speak(sound.text)
        }
    }

    /** Stops everything at once — dismissing an alarm, or the time limit running out. */
    fun stop() = finish()

    private fun playTone(uri: Uri?, isFallback: Boolean = false) {
        val target = uri ?: defaultTone()
        if (target == null) {
            finishUnlessLooping()
            return
        }
        requestFocus(usage)
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setAudioAttributes(attributes(usage))
            player.setDataSource(context, target)
            player.isLooping = loop
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { if (!loop) finish() }
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Tone failed ($what/$extra), trying the default")
                releasePlayer()
                if (!isFallback) playTone(null, isFallback = true) else finishUnlessLooping()
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            // A tone that was deleted, or a URI the app may no longer read.
            Log.w(TAG, "Could not play $target", e)
            releasePlayer()
            if (!isFallback) playTone(null, isFallback = true) else finishUnlessLooping()
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) {
            playTone(null)
            return
        }
        requestFocus(speechUsage)
        var engine: TextToSpeech? = null
        engine = runCatching { createTts(context) { status ->
            val ready = engine
            if (status != TextToSpeech.SUCCESS || ready == null || finished) {
                Log.w(TAG, "Text-to-speech unavailable ($status), playing a tone instead")
                shutdownTts()
                if (!finished) playTone(null)
                return@createTts
            }
            val language = ready.setLanguage(Locale.getDefault())
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                ready.setLanguage(Locale.ENGLISH)
            }
            ready.setAudioAttributes(attributes(speechUsage))
            ready.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    handler.post { afterSpeech() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post { afterSpeech() }
                }
            })
            val params = Bundle()
            ready.speak(text, TextToSpeech.QUEUE_ADD, params, UTTERANCE_ID)
        } }.onFailure { Log.w(TAG, "Could not start text-to-speech", it) }.getOrNull()
        tts = engine
        if (engine == null) playTone(null)
    }

    private fun createTts(context: Context, onInit: (Int) -> Unit): TextToSpeech =
        TextToSpeech(context, onInit)

    /** An alarm keeps ringing after it has spoken; a reminder is simply done. */
    private fun afterSpeech() {
        shutdownTts()
        if (finished) return
        if (loop) playTone(null) else finish()
    }

    private fun finishUnlessLooping() {
        if (!loop) finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        shutdownTts()
        if (loop) VibrationHelper.cancel(context)
        abandonFocus()
        onFinished?.invoke()
        onFinished = null
    }

    private fun releasePlayer() {
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    private fun shutdownTts() {
        tts?.let { engine ->
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
        }
        tts = null
    }

    private fun defaultTone(): Uri? {
        val type = if (usage == AudioAttributes.USAGE_ALARM) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        return RingtoneManager.getDefaultUri(type)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    /**
     * Asks other audio to duck (a podcast drops its volume) rather than stopping it, and gives the
     * focus back the moment the alert is over.
     */
    private fun requestFocus(focusUsage: Int) {
        if (focusRequest != null || audioManager == null) return
        val gain = if (loop) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(attributes(focusUsage))
            .build()
        runCatching { audioManager.requestAudioFocus(request) }
        focusRequest = request
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        runCatching { audioManager?.abandonAudioFocusRequest(request) }
        focusRequest = null
    }

    companion object {
        private const val TAG = "AlertPlayer"
        private const val UTTERANCE_ID = "reReminderAlert"
        const val DEFAULT_MAX_MILLIS = 30_000L

        fun attributes(usage: Int): AudioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(
                if (usage == AudioAttributes.USAGE_MEDIA) {
                    AudioAttributes.CONTENT_TYPE_SPEECH
                } else {
                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                }
            )
            .build()

        /** Maps the "Speak through" setting to an audio usage. */
        fun speechUsage(stream: String): Int = when (stream) {
            PreferenceHelper.STREAM_ALARM -> AudioAttributes.USAGE_ALARM
            PreferenceHelper.STREAM_NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
            else -> AudioAttributes.USAGE_MEDIA
        }

        /**
         * What the reminder's own sound choice plays. "Default" is the notification or alarm
         * sound picked in Settings — everything else is decided by the reminder alone.
         */
        fun resolveSound(
            reminder: Reminder,
            preferences: PreferenceHelper,
            alarm: Boolean,
            spokenText: String,
        ): AlertSound = when (reminder.sound.mode) {
            SoundMode.SILENT -> AlertSound.Silent
            SoundMode.TONE -> AlertSound.Tone(reminder.sound.toneUri?.toUri())
            SoundMode.SPEAK -> AlertSound.Speech(spokenText)
            SoundMode.DEFAULT -> AlertSound.Tone(
                if (alarm) preferences.getAlarmTone() else preferences.getSelectedRingtone()
            )
        }

        /**
         * Applies the phone's own quiet settings to a notification-style alert: nothing during Do
         * Not Disturb, vibration only on vibrate, nothing at all on silent. Alarms are exempt —
         * that is what Do Not Disturb's alarm exception is for.
         */
        fun gate(context: Context, sound: AlertSound, vibrate: Boolean): Pair<AlertSound, Boolean> {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val filter = notificationManager?.currentInterruptionFilter
                ?: NotificationManager.INTERRUPTION_FILTER_ALL
            if (filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                return AlertSound.Silent to false
            }
            return when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> AlertSound.Silent to false
                AudioManager.RINGER_MODE_VIBRATE -> AlertSound.Silent to vibrate
                else -> sound to vibrate
            }
        }
    }
}
