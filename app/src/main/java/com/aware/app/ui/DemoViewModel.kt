package com.aware.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aware.app.alert.AlertReactor
import com.aware.app.alert.Category
import com.aware.app.alert.SoundType
import com.aware.app.demo.DemoAudioSource
import com.aware.app.demo.DemoClassifier
import com.aware.app.demo.DemoScenario
import com.aware.app.haptics.HapticEvent
import com.aware.app.haptics.HapticPlayer
import com.aware.app.service.AwarePipeline
import com.aware.app.service.ListeningRepository
import com.aware.app.service.PipelineState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DemoViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = mutableState.asStateFlow()
    private val mutableRunning = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()
    private val mutableDemo = MutableStateFlow<DemoScenario?>(null)
    val demo: StateFlow<DemoScenario?> = mutableDemo.asStateFlow()
    val liveRunning = ListeningRepository.running

    private val haptics = HapticPlayer(application)
    private var sessionJob: Job? = null
    private var pipeline: AwarePipeline? = null
    private var classifier: DemoClassifier? = null
    private var reactor: AlertReactor? = null

    fun start(scenario: DemoScenario) {
        if (running.value || liveRunning.value) return
        release()
        val activeClassifier = DemoClassifier(scenario)
        val activePipeline = AwarePipeline(DemoAudioSource(scenario), activeClassifier).apply {
            setSensitivity(0f)
            setEnabledCategories(Category.entries.toSet())
        }
        val activeReactor = AlertReactor(getApplication(), postExternalAlerts = false)
        classifier = activeClassifier
        pipeline = activePipeline
        reactor = activeReactor
        mutableDemo.value = scenario
        mutableRunning.value = true
        mutableState.value = PipelineState(listening = true)
        sessionJob = viewModelScope.launch {
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    activePipeline.alerts.collect(activeReactor::react)
                }
                launch(start = CoroutineStart.UNDISPATCHED) {
                    activePipeline.state.collect { state ->
                        mutableState.value = state
                        mutableRunning.value = state.listening
                    }
                }
                activePipeline.start(this)
                awaitCancellation()
            }
        }
    }

    fun stop() = release()

    fun stopLiveListening() = ListeningRepository.stop(getApplication())

    fun replay(type: SoundType) = haptics.play(HapticEvent.from(type))

    private fun release() {
        sessionJob?.cancel()
        sessionJob = null
        pipeline?.stop()
        pipeline = null
        reactor?.stop()
        reactor = null
        classifier?.close()
        classifier = null
        mutableState.value = PipelineState()
        mutableRunning.value = false
        mutableDemo.value = null
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }
}
