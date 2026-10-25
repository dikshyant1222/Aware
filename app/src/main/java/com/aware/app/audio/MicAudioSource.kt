package com.aware.app.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
                mutablePcm.tryEmit(pcm.copyOf(count))
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
        for (source in intArrayOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT)) {
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

    private companion object {
        const val WINDOW_SIZE = 15_600
        const val HOP_SIZE = 7_800
    }
}
