package com.aware.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aware.app.R
import com.aware.app.demo.DemoScenario
import kotlinx.coroutines.delay

@Composable
fun DemoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DemoViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val activeDemo by viewModel.demo.collectAsStateWithLifecycle()
    val liveRunning by viewModel.liveRunning.collectAsStateWithLifecycle()
    val nowMs by produceState(System.currentTimeMillis(), running) {
        while (running) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }

    fun leaveDemo() {
        if (activeDemo != null) viewModel.stop()
        onBack()
    }
    BackHandler(onBack = ::leaveDemo)

    LazyColumn(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = ::leaveDemo) { Text(stringResource(R.string.demo_back)) }
            Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.displayMedium)
            Text(
                stringResource(R.string.demo_explanation),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.demo_safety),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (running || liveRunning) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            activeDemo?.let {
                                stringResource(R.string.demo_running, soundName(it.soundType))
                            } ?: stringResource(R.string.demo_live_running),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        if (activeDemo != null) {
                            Text(stringResource(R.string.demo_processing), style = MaterialTheme.typography.bodyLarge)
                        }
                        Button(
                            onClick = if (activeDemo != null) viewModel::stop else viewModel::stopLiveListening,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Text(stringResource(
                                if (activeDemo != null) R.string.demo_stop else R.string.demo_stop_live,
                            ))
                        }
                    }
                }
            }
        }

        state.error?.let { error ->
            item {
                Text(
                    stringResource(R.string.home_error, error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        state.latestAlert?.let { alert ->
            item {
                Text(stringResource(R.string.demo_result), style = MaterialTheme.typography.headlineMedium)
                AlertCard(
                    detection = alert,
                    nowMs = nowMs,
                    onReplay = { viewModel.replay(alert.type) },
                )
            }
        }

        item {
            Text(stringResource(R.string.demo_choose), style = MaterialTheme.typography.headlineMedium)
        }
        items(DemoScenario.entries) { scenario ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(scenario.soundType.category)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(soundName(scenario.soundType), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.demo_category, categoryName(scenario.soundType.category)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = { viewModel.start(scenario) },
                        enabled = !running && !liveRunning,
                        modifier = Modifier.heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.demo_try))
                    }
                }
            }
        }
    }
}
