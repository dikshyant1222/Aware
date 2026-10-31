package com.aware.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.aware.app.classify.Label
import com.aware.app.service.ListeningRepository
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Sample only developer-visible labels; alerts and speech remain immediate. */
@Composable
fun SoundDiagnostics() {
    val running by ListeningRepository.running.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val labels by produceState<List<Label>>(emptyList(), running, lifecycle) {
        value = emptyList()
        if (running) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = ListeningRepository.state.value.labels.take(4)
                delay(2000)
            }
        }
    }
    if (running) labels.forEach { label ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${(label.score.coerceIn(0f, 1f) * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
