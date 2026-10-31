package com.aware.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.aware.app.R
import com.aware.app.data.SummaryHistoryRepository
import com.aware.app.data.recentSummaries
import com.aware.app.ui.theme.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.io.IOException

@Composable
fun SummaryHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { SummaryHistoryRepository(context) }
    val records by repository.history.collectAsStateWithLifecycle(emptyList())
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = System.currentTimeMillis(); delay(1000) }
        }
    }
    LaunchedEffect(repository) { try { repository.prune() } catch (_: IOException) { /* Reading remains available. */ } }
    val recent = recentSummaries(records, now)
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
    AwareBackdrop {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 480.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { AwareHeader(stringResource(R.string.summary_history_title), onBack) }
                item { Text(stringResource(R.string.summary_history_range), color = AwareSecondary, style = MaterialTheme.typography.bodyMedium) }
                if (recent.isEmpty()) item {
                    SoftActionCard(stringResource(R.string.summary_history_empty), stringResource(R.string.summary_history_hint), Icons.Default.History, AwareAccent)
                }
                items(recent, key = { it.id }) { entry ->
                    Surface(shape = MaterialTheme.shapes.large, color = AwareSurface) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(entry.summary.text, style = MaterialTheme.typography.bodyLarge)
                            Text("${timeFormat.format(Instant.ofEpochMilli(entry.timeMs))} · ${timeAgo(entry.timeMs, now)}",
                                color = AwareSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
