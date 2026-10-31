package com.aware.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aware.app.R
import com.aware.app.alert.Category
import com.aware.app.alert.SoundType
import com.aware.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = viewModel(),
    onOpenSummary: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val summary by viewModel.situation.collectAsStateWithLifecycle()
    val summaryTime by viewModel.situationTimeMs.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val nowMs by produceState(System.currentTimeMillis(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = System.currentTimeMillis(); delay(1000) }
        }
    }
    val sounds = remember(state.feed) { recentSurroundings(state.feed) }
    AwareBackdrop(modifier) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 480.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item("header") {
                    AwareHeader(stringResource(R.string.app_name))
                }
                item("hero") {
                    Column {
                        ListeningHero(running, state.alertsPaused, viewModel::toggleListening)
                        Box(Modifier.offset(y = (-12).dp)) { SummaryPreview(summary, running, summaryTime, nowMs, onOpenSummary) }
                    }
                }
                state.error?.let { error -> item("error") {
                    Text(stringResource(R.string.home_error, error), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                } }
                val alert = state.latestAlert
                val nameTime = state.latestNameAlertMs
                if (nameTime != null && (alert == null || nameTime > alert.timeMs)) {
                    item("latest-name") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(stringResource(R.string.ui_last_alert))
                            SoftActionCard(stringResource(R.string.alert_name_called), timeAgo(nameTime, nowMs),
                                Icons.Default.Person, categoryColor(Category.PEOPLE), onClick = viewModel::replayName, trailing = Icons.Default.Vibration)
                        }
                    }
                } else if (alert != null) item("latest-alert") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(stringResource(R.string.ui_last_alert))
                        AlertCard(alert, nowMs, { viewModel.replayAlert(alert.type) }, situation = summary, situationTimeMs = summaryTime)
                    }
                }
                item("surroundings") {
                    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.ui_surroundings),
                            if (sounds.size > 2) stringResource(if (expanded) R.string.ui_show_less else R.string.ui_show_more) else null,
                            onAction = { expanded = !expanded })
                        if (sounds.isEmpty()) SoftActionCard(stringResource(R.string.ui_empty_sounds),
                            stringResource(if (running) R.string.ui_empty_sounds_hint else R.string.ui_paused_hint),
                            Icons.Default.Hearing, AwareAccent)
                        sounds.take(if (expanded) 5 else 2).forEach { sound -> key(sound.type, sound.nameCalled) {
                            val title = if (sound.nameCalled) stringResource(R.string.alert_name_called) else soundName(sound.type)
                            val speechText = if (sound.type == SoundType.SPEECH && !sound.nameCalled)
                                sound.text ?: transcript.lastOrNull { it.isFinal }?.text else null
                            val replay: (() -> Unit)? = if (sound.nameCalled) viewModel::replayName
                                else if (sound.type.alerts) ({ viewModel.replayAlert(sound.type) }) else null
                            SoftActionCard(title, listOfNotNull(speechText, timeAgo(sound.timeMs, nowMs)).joinToString(" · "),
                                if (sound.nameCalled) Icons.Default.Person else soundIcon(sound.type), categoryColor(sound.type.category),
                                onClick = replay, trailing = if (replay != null) Icons.Default.Vibration else null)
                        } }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(100.dp).background(
                Brush.verticalGradient(listOf(AwareBackground.copy(alpha = 0f), AwareBackground))))
        }
    }
}
