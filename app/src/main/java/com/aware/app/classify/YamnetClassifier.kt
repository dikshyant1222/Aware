package com.aware.app.classify

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions

class YamnetClassifier(context: Context) : SoundClassifier {
    private val classifier: AudioClassifier = try {
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .setMaxResults(20)
            .setScoreThreshold(0.05f)
            .build()
        AudioClassifier.createFromOptions(context.applicationContext, options)
    } catch (error: Exception) {
        throw IllegalStateException("Could not load YAMNet from assets/yamnet.tflite.", error)
    } catch (error: LinkageError) {
        throw IllegalStateException("Could not load the native YAMNet audio classifier.", error)
    }
    private var closed = false

    // Serialize native inference and cleanup, including ViewModel teardown during inference.
    @Synchronized
    override fun classify(samples: FloatArray, sampleRate: Int): List<Label> {
        check(!closed) { "YAMNet classifier is closed." }
        require(samples.isNotEmpty()) { "An audio window must contain samples." }
        require(sampleRate > 0) { "Sample rate must be positive." }
        val format = AudioData.AudioDataFormat.builder()
            .setNumOfChannels(1)
            .setSampleRate(sampleRate.toFloat())
            .build()
        val audio = AudioData.create(format, samples.size)
        audio.load(samples)
        val categories = classifier.classify(audio)
            .classificationResults().firstOrNull()
            ?.classifications()?.firstOrNull()
            ?.categories().orEmpty()
        return categories
            .sortedByDescending { it.score() }
            .take(20)
            .map { Label(name = it.categoryName(), score = it.score()) }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            classifier.close()
        }
    }
}
