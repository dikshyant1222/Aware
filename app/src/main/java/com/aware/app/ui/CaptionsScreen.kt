package com.aware.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aware.app.R
import com.aware.app.data.Settings
import com.aware.app.data.settingsRepository
import com.aware.app.service.ListeningRepository
import java.util.Locale

@Composable
fun CaptionsBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(72.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        containerColor = Color.Yellow,
        contentColor = Color.Black,
    ) {
        Icon(Icons.Default.ClosedCaption, stringResource(R.string.live_captions), Modifier.size(40.dp))
    }
}

@Composable
fun CaptionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lines by ListeningRepository.transcript.collectAsStateWithLifecycle()
    val active by ListeningRepository.captions.collectAsStateWithLifecycle()
    val error by ListeningRepository.speechError.collectAsStateWithLifecycle()
    val pipeline by ListeningRepository.state.collectAsStateWithLifecycle()
    val settings by context.settingsRepository.settings.collectAsStateWithLifecycle(Settings())
    val scroll = rememberLazyListState()
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        ListeningRepository.setCaptions(context, true)
        onDispose {
            view.keepScreenOn = previous
            ListeningRepository.setCaptions(context, false)
        }
    }
    LaunchedEffect(lines) { if (lines.isNotEmpty()) scroll.animateScrollToItem(lines.lastIndex) }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.haptics_back)) }
            Text(stringResource(R.string.live_captions), color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            listOfNotNull(Locale.getDefault().displayName, settings.translationTarget?.let { Locale.forLanguageTag(it).displayName }).joinToString(" → "),
            color = Color.White, style = MaterialTheme.typography.titleLarge,
        )
        if (Build.VERSION.SDK_INT < 33) Text(stringResource(R.string.captions_legacy_limitation), color = Color.Yellow)
        if (error != null) Text(stringResource(R.string.speech_error, error!!), color = Color.Yellow)
        pipeline.error?.let { Text(stringResource(R.string.home_error, it), color = Color.Yellow) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { ListeningRepository.setCaptions(context, !active) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                Text(stringResource(if (active) R.string.captions_stop else R.string.captions_start))
            }
            OutlinedButton(onClick = ListeningRepository::clearTranscript, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.captions_clear))
            }
        }
        LazyColumn(Modifier.weight(1f), state = scroll, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (lines.isEmpty()) item { Text(stringResource(R.string.captions_waiting), color = Color.LightGray, fontSize = 32.sp) }
            items(lines, key = { it.id }) { line ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.text, color = if (line.isFinal) Color.White else Color.LightGray, fontSize = 36.sp, lineHeight = 44.sp)
                    line.translation?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 32.sp, lineHeight = 40.sp) }
                }
            }
        }
    }
}
