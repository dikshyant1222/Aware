package com.aware.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aware.app.R
import com.aware.app.alert.Category
import com.aware.app.alert.SoundType
import com.aware.app.llm.Source
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onTestHaptics: () -> Unit = {},
    onSettings: () -> Unit = {},
    onCaptions: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val situation by viewModel.situation.collectAsStateWithLifecycle()
    val situationTime by viewModel.situationTimeMs.collectAsStateWithLifecycle()
    var showDebug by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val nowMs by produceState(System.currentTimeMillis(), running, lifecycleOwner) {
        if (running) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }
    val levelDb = state.levelDb.coerceIn(-90f, 0f)
    val levelDescription = stringResource(R.string.home_level_description, levelDb.roundToInt())
    val levelTitle = stringResource(R.string.home_level_title)
    Box(modifier.fillMaxSize()) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onTestHaptics, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                        Icon(Icons.Default.Vibration, contentDescription = null)
                        Text(stringResource(R.string.home_test_haptics), style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = onSettings, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    text = stringResource(
                        if (running) R.string.home_listening else R.string.home_stopped,
                    ),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (running) {
                    Text(
                        text = stringResource(R.string.listening_background_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = viewModel::toggleListening,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (running) R.string.home_stop_listening else R.string.home_start_listening,
                        ),
                    )
                }
                Button(
                    onClick = viewModel::requestSituation,
                    enabled = running,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.situation_request), style = MaterialTheme.typography.titleLarge)
                }
                state.error?.let { error ->
                    Text(
                        text = stringResource(R.string.home_error, error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = levelDescription, style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(
                        progress = { (levelDb + 90f) / 90f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 16.dp)
                            .clearAndSetSemantics {
                                contentDescription = levelTitle
                                stateDescription = levelDescription
                                progressBarRangeInfo = ProgressBarRangeInfo(levelDb, -90f..0f)
                            },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.home_level_floor), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.home_level_peak), style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { showDebug = !showDebug }) {
                    Text(stringResource(if (showDebug) R.string.home_debug_hide else R.string.home_debug_show))
                }
                if (showDebug) {
                    state.labels.take(5).forEach { label ->
                        val score = label.score.coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = label.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = stringResource(R.string.home_score, (score * 100).roundToInt()),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            LinearProgressIndicator(
                                progress = { score },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp)
                                    .semantics { contentDescription = label.name },
                            )
                        }
                    }
                }
                situation?.let { summary ->
                    Card(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.situation_title), style = MaterialTheme.typography.headlineMedium)
                            Text(summary.text, style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (summary.source == Source.LLM) Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Text(stringResource(if (summary.source == Source.LLM) R.string.situation_ai else R.string.situation_template),
                                    style = MaterialTheme.typography.labelSmall)
                                Text(timeAgo(situationTime, nowMs), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.home_latest_alert), style = MaterialTheme.typography.headlineMedium)
                val alert = state.latestAlert
                val nameTime = state.latestNameAlertMs
                if (nameTime != null && (alert == null || nameTime > alert.timeMs)) {
                    Card(onClick = viewModel::replayName, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.aware.app.ui.theme.AwareSurface)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryIcon(Category.PEOPLE)
                            Text(stringResource(R.string.alert_name_called), color = categoryColor(Category.PEOPLE), style = MaterialTheme.typography.headlineLarge)
                            Text(timeAgo(nameTime, nowMs))
                            Text(stringResource(R.string.alert_replay_hint))
                        }
                    }
                } else if (alert == null) {
                    Text(stringResource(R.string.home_no_alert), style = MaterialTheme.typography.bodyLarge)
                } else {
                    AlertCard(alert, nowMs, onReplay = { viewModel.replayAlert(alert.type) },
                        situation = situation, situationTimeMs = situationTime)
                }
                Text(stringResource(R.string.home_feed_title), style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (state.feed.isEmpty()) {
            item {
                Text(
                    text = stringResource(
                        if (state.listening) R.string.home_waiting_for_sounds else R.string.home_start_hint,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(state.feed) { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(item.type.category)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (item.nameCalled) stringResource(R.string.alert_name_called) else soundName(item.type), style = MaterialTheme.typography.titleLarge)
                    val speechText = if (item.type == SoundType.SPEECH && !item.nameCalled) {
                        item.text ?: transcript.lastOrNull { it.isFinal }?.text
                    } else null
                    Text(speechText ?: trendText(item.trend), style = MaterialTheme.typography.bodyMedium)
                }
                Text(timeAgo(item.timeMs, nowMs), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    CaptionsBubble(onCaptions, Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp))
    }
}

