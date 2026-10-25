package com.aware.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.aware.app.R
import com.aware.app.alert.AlertMessages
import com.aware.app.alert.Detection
import com.aware.app.llm.Summary
import com.aware.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertCard(detection: Detection, nowMs: Long, onReplay: () -> Unit, modifier: Modifier = Modifier,
    situation: Summary? = null, situationTimeMs: Long = 0L) {
    val context = LocalContext.current
    val messages = remember(context) { AlertMessages(context::getString) }
    var details by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp), color = Color.White,
        border = BorderStroke(1.dp, categoryColor(detection.type.category).copy(alpha = .2f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TintedIconWell(soundIcon(detection.type), categoryColor(detection.type.category))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(soundName(detection.type), style = MaterialTheme.typography.titleMedium)
                    Text(messages.templateFor(detection), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeAgo(detection.timeMs, nowMs), color = AwareSecondary, style = MaterialTheme.typography.bodySmall)
                Text(trendText(detection.trend), color = AwareSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onReplay, Modifier.weight(1f)) { Icon(Icons.Default.Vibration, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_replay)) }
                TextButton({ details = true }, Modifier.weight(1f)) { Text(stringResource(R.string.ui_details)) }
            }
        }
    }
    if (details) ModalBottomSheet(onDismissRequest = { details = false }, containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AwareHeader(soundName(detection.type), trailing = { SoftCircleButton(Icons.Default.Close, stringResource(R.string.ui_close), { details = false }) })
            Text(messages.templateFor(detection), style = MaterialTheme.typography.headlineMedium)
            if (situation != null && kotlin.math.abs(situationTimeMs - detection.timeMs) <= 15_000) Text(situation.text)
            Text(trendText(detection.trend), color = AwareSecondary)
            Text(timeAgo(detection.timeMs, nowMs), color = AwareSecondary)
            Button(onReplay, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_replay)) }
        }
    }
}
