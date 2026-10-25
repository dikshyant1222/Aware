package com.aware.app.speech

import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale
import kotlinx.coroutines.flow.Flow

data class TranscriptEvent(val text: String, val isFinal: Boolean, val timeMs: Long)
data class TranscriptLine(
    val id: Long,
    val text: String,
    val isFinal: Boolean,
    val timeMs: Long,
    val translation: String? = null,
)

interface Transcriber {
    val results: Flow<TranscriptEvent>
    fun start()
    fun stop()
    fun release()
}

internal fun recognitionIntent(locale: Locale = Locale.getDefault()) =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
