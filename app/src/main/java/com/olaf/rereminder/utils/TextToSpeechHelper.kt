package com.olaf.rereminder.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object TextToSpeechHelper : TextToSpeech.OnInitListener {

    private const val TAG = "TextToSpeechHelper"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var textToSpeak: String? = null
    private var context: Context? = null

    fun initialize(context: Context) {
        if (this.context == null) {
            this.context = context.applicationContext
        }
        if (tts == null) {
            try {
                // Initialisiere TTS im Kontext der App
                tts = TextToSpeech(this.context, this)
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Initialisierung von TextToSpeech", e)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Stelle die Sprache auf Deutsch ein
            val result = tts?.setLanguage(Locale.GERMAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Die deutsche Sprache wird nicht unterstützt.")
            } else {
                isInitialized = true
                Log.d(TAG, "TextToSpeech erfolgreich initialisiert.")
                // Spreche zwischengespeicherten Text, falls vorhanden
                textToSpeak?.let {
                    speak(it)
                    textToSpeak = null
                }
            }
        } else {
            Log.e(TAG, "Initialisierung von TextToSpeech fehlgeschlagen mit Status: $status")
        }
    }

    fun speak(text: String) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS nicht initialisiert. Text wird zwischengespeichert.")
            textToSpeak = text
            context?.let { initialize(it) } // Versuche erneut zu initialisieren
            return
        }

        if (text.isBlank()) {
            Log.w(TAG, "Text zum Vorlesen ist leer.")
            return
        }

        // Spreche den Text
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reReminderTTS")
        Log.d(TAG, "Spreche: $text")
    }

    fun shutdown() {
        tts?.let {
            it.stop()
            it.shutdown()
            tts = null
            isInitialized = false
            context = null
            Log.d(TAG, "TextToSpeech heruntergefahren.")
        }
    }
}