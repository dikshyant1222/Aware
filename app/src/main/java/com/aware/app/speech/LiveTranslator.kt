package com.aware.app.speech

import android.content.Context
import android.os.SystemClock
import com.aware.app.R
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.*
import java.util.Locale

/** Only explicit settings actions download models. Stale callbacks cannot update new sessions. */
class LiveTranslator(private val context: Context, locale: Locale = Locale.getDefault()) {
    private val source = sourceLanguage(locale)
    private var target: String? = null
    private var client: Translator? = null
    private var generation = 0
    private var lastPartialMs: Long? = null
    private var partialRequest = 0

    fun setTarget(code: String?) {
        if (target == code) return
        close()
        target = code?.takeIf { it in TranslateLanguage.getAllLanguages() }
        if (target != null && target != source) client = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target!!).build(),
        )
    }

    fun translate(event: TranscriptEvent, done: (String) -> Unit) {
        val destination = target ?: return
        val now = SystemClock.elapsedRealtime()
        if (!event.isFinal && lastPartialMs?.let { now - it < 1_000 } == true) return
        if (!event.isFinal) { lastPartialMs = now; partialRequest++ }
        if (destination == source) { done(event.text); return }
        val activeClient = client ?: return
        val token = generation
        val request = partialRequest
        fun current() = token == generation && (event.isFinal || request == partialRequest)
        // English is built in; non-English source and target models are both required.
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                if (!current()) return@addOnSuccessListener
                val installed = models.map { it.language }.toSet() + TranslateLanguage.ENGLISH
                if (source !in installed || destination !in installed) {
                    done(context.getString(R.string.translation_model_missing))
                } else {
                    activeClient.translate(event.text).addOnSuccessListener {
                        if (current()) done(it)
                    }.addOnFailureListener {
                        if (current()) done(context.getString(R.string.translation_failed))
                    }
                }
            }.addOnFailureListener {
                if (current()) done(context.getString(R.string.translation_failed))
            }
    }

    fun close() {
        generation++
        client?.close()
        client = null
        target = null
        lastPartialMs = null
    }

    companion object {
        fun sourceLanguage(locale: Locale): String =
            TranslateLanguage.fromLanguageTag(locale.toLanguageTag()) ?: TranslateLanguage.ENGLISH

        /** The caller owns this client until the download completes or its screen is disposed. */
        fun download(target: String, done: (Boolean) -> Unit): Translator {
            val client = Translation.getClient(TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage(Locale.getDefault())).setTargetLanguage(target).build())
            client.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { done(true) }
                .addOnFailureListener { done(false) }
            return client
        }
    }
}
