package com.aware.app.audio

import kotlinx.coroutines.flow.Flow

interface AudioSource {
    val sampleRate: Int

    /** Mono PCM windows in chronological order, normalized to [-1, 1]. */
    fun windows(): Flow<FloatArray>
}
