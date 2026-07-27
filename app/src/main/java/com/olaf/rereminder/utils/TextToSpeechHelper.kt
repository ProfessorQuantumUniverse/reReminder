package com.olaf.rereminder.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object TextToSpeechHelper : TextToSpeech.OnInitListener {

    private const val TAG = "TextToSpeechHelper"
    private const val UTTERANCE_ID = "reReminderTTS"

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /** Set when [speak] is called before the engine finished starting up. */
    private var pendingText: String? = null

    fun initialize(context: Context) {
        if (tts != null) return
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
            return
        }

        // Follow the device language rather than forcing a single locale.
        val locale = Locale.getDefault()
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale $locale is not supported, falling back to English")
            tts?.setLanguage(Locale.ENGLISH)
        }

        isInitialized = true
        Log.d(TAG, "TextToSpeech initialized")

        pendingText?.let { text ->
            pendingText = null
            speak(text)
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "Nothing to speak")
            return
        }

        if (!isInitialized || tts == null) {
            Log.d(TAG, "TTS not ready yet, buffering text")
            pendingText = text
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun shutdown() {
        tts?.let {
            it.stop()
            it.shutdown()
        }
        tts = null
        isInitialized = false
        pendingText = null
        Log.d(TAG, "TextToSpeech shut down")
    }
}
