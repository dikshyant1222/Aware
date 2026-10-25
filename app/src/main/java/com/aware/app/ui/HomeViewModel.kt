package com.aware.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aware.app.alert.SoundType
import com.aware.app.haptics.HapticEvent
import com.aware.app.haptics.HapticPlayer
import com.aware.app.service.ListeningRepository

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    val state = ListeningRepository.state
    val running = ListeningRepository.running
    val transcript = ListeningRepository.transcript
    val situation = ListeningRepository.situation
    val situationTimeMs = ListeningRepository.situationTimeMs
    fun requestSituation() = ListeningRepository.requestSituation()
    private val haptics = HapticPlayer(application)

    fun toggleListening() {
        if (running.value) ListeningRepository.stop(getApplication())
        else ListeningRepository.start(getApplication())
    }

    fun replayAlert(type: SoundType) = haptics.play(HapticEvent.from(type))
    fun replayName() = haptics.play(HapticEvent.NAME_CALLED)
}
