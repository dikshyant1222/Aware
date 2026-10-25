package com.aware.app.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MicAudioSource : AudioSource {
    override val sampleRate: Int = 16_000
    private val mutablePcm = MutableSharedFlow<ShortArray>(
        extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pcm: SharedFlow<ShortArray> = mutablePcm.asSharedFlow()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun windows(): Flow<FloatArray> = flow {
        val recorder = createRecorder()
        val effects = disableProcessing(recorder)
        // Speech recognition still gets a usable level from the unprocessed source.
        val speechGain = AutoGain(release = 0.05f)
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Microphone recording could not be started."
            }
            val pcm = ShortArray(1_024)
            val window = FloatArray(WINDOW_SIZE)
            var filled = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                // Polling avoids a blocking native read delaying cancellation and release.
                val count = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_NON_BLOCKING)
                check(count >= 0) { "Microphone read failed (AudioRecord error $count)." }
                if (count == 0) {
                    delay(10)
                    continue
                }
                mutablePcm.tryEmit(boosted(pcm, count, speechGain))
                for (index in 0 until count) {
                    window[filled++] = pcm[index] / 32_768f
                    if (filled == WINDOW_SIZE) {
                        emit(window.copyOf())
                        window.copyInto(window, 0, HOP_SIZE, WINDOW_SIZE)
                        filled = WINDOW_SIZE - HOP_SIZE
                    }
                }
            }
        } finally {
            effects.forEach { runCatching { it.release() } }
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } finally {
                recorder.release()
            }
        }
    }
        .flowOn(Dispatchers.IO)
        // Keep capture current if inference cannot keep up; never accumulate stale audio.
        .conflate()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createRecorder(): AudioRecord {
        val minimumBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBytes > 0) { "16 kHz mono microphone capture is unavailable." }
        val bufferBytes = maxOf(minimumBytes, WINDOW_SIZE * 2)
        var failure: Exception? = null
        // The phone's MIC path often applies noise suppression and gain control, which treat steady tonal
        // sounds such as sirens and smoke alarms as noise. Prefer the least processed source available.
        for (source in intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT,
        )) {
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
                check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                    "Microphone initialization failed for audio source $source."
                }
                return recorder
            } catch (error: Exception) {
                recorder?.release()
                failure = error
            }
        }
        throw IllegalStateException("Could not initialize the microphone (MIC or DEFAULT).", failure)
    }

    /** Switch off any noise suppression, gain control and echo cancellation the device attached. */
    private fun disableProcessing(recorder: AudioRecord): List<AudioEffect> = buildList {
        val session = recorder.audioSessionId
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.also { it.enabled = false }?.let(::add) }
        runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(session)?.also { it.enabled = false }?.let(::add) }
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.also { it.enabled = false }?.let(::add) }
    }

    private fun boosted(pcm: ShortArray, count: Int, gain: AutoGain): ShortArray {
        val floats = FloatArray(count) { pcm[it] / 32_768f }
        val lifted = gain.apply(floats)
        if (lifted === floats) return pcm.copyOf(count)
        return ShortArray(count) { (lifted[it] * 32_767f).toInt().toShort() }
    }

    private companion object {
        const val WINDOW_SIZE = 15_600
        const val HOP_SIZE = 7_800
    }
}
