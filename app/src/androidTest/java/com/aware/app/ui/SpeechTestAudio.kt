package com.aware.app.ui

import android.app.Instrumentation
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Generates known test PCM without playing sound or recording the user. */
internal object SpeechTestAudio {
    fun synthesize(instrumentation: Instrumentation): ShortArray {
        val initialized = CountDownLatch(1)
        val complete = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        var synthesisError = false
        lateinit var tts: TextToSpeech
        // Instrumentation executes under the target app UID, so its files must
        // be in the target's cache rather than the separate test APK's cache.
        val file = File(instrumentation.targetContext.cacheDir, "hello-thank-you.wav")
        instrumentation.runOnMainSync {
            tts = TextToSpeech(instrumentation.context) { status = it; initialized.countDown() }
        }
        try {
            check(initialized.await(15, TimeUnit.SECONDS) && status == TextToSpeech.SUCCESS) { "Test speech engine unavailable" }
            instrumentation.runOnMainSync {
                check(tts.setLanguage(Locale.US) >= 0) { "English test voice unavailable" }
                tts.setSpeechRate(.85f)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) { complete.countDown() }
                    @Deprecated("Platform callback")
                    override fun onError(id: String?) { synthesisError = true; complete.countDown() }
                })
                val result = tts.synthesizeToFile("Hello, thank you.", null, file, "sync-check")
                check(result == TextToSpeech.SUCCESS) { "Test voice synthesis request failed: $result" }
            }
            check(complete.await(15, TimeUnit.SECONDS) && !synthesisError) { "Could not synthesize test speech" }
            return decodeWav(file.readBytes())
        } finally {
            instrumentation.runOnMainSync { tts.shutdown() }
        }
    }

    private fun decodeWav(bytes: ByteArray): ShortArray {
        check(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Test voice did not produce WAV" }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var channels = 0
        var rate = 0
        var start = 0
        var size = 0
        while (offset + 8 <= bytes.size) {
            val name = String(bytes, offset, 4)
            val count = data.getInt(offset + 4)
            val payload = offset + 8
            if (name == "fmt ") {
                check(data.getShort(payload).toInt() == 1 && data.getShort(payload + 14).toInt() == 16) { "Expected PCM16 test voice" }
                channels = data.getShort(payload + 2).toInt()
                rate = data.getInt(payload + 4)
            }
            if (name == "data") { start = payload; size = minOf(count, bytes.size - payload); break }
            offset = payload + count + (count and 1)
        }
        check(channels > 0 && rate > 0 && size > 0) { "Missing test audio data" }
        val frames = size / (2 * channels)
        val mono = DoubleArray(frames) { frame ->
            (0 until channels).sumOf { data.getShort(start + 2 * (frame * channels + it)).toDouble() } / channels
        }
        return ShortArray((frames.toLong() * 16_000 / rate).toInt()) { index ->
            val source = index.toDouble() * rate / 16_000
            val left = source.toInt().coerceAtMost(frames - 1)
            val right = (left + 1).coerceAtMost(frames - 1)
            (mono[left] + (mono[right] - mono[left]) * (source - left)).toInt().toShort()
        }
    }
}
