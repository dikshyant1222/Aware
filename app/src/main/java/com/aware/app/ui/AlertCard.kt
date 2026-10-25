package com.aware.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aware.app.R
import com.aware.app.alert.AlertMessages
import com.aware.app.alert.Detection
import com.aware.app.ui.theme.AwareSurface
import kotlin.math.roundToInt

@Composable
fun AlertCard(detection: Detection, nowMs: Long, onReplay: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val messages = remember(context) { AlertMessages(context::getString) }
    Card(
        onClick = onReplay,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        // White on #111418: >18:1; category color is confined to the band and icon.
        colors = CardDefaults.cardColors(containerColor = AwareSurface, contentColor = Color.White),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(categoryColor(detection.type.category)))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryIcon(detection.type.category, Modifier.size(56.dp))
                Text(soundName(detection.type), style = MaterialTheme.typography.headlineLarge)
                Text(messages.templateFor(detection), style = MaterialTheme.typography.titleLarge)
                Surface(color = Color(0xFF252A30), contentColor = Color.White, shape = RoundedCornerShape(12.dp)) {
                    Text(trendText(detection.trend), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    stringResource(R.string.home_score, (detection.score.coerceIn(0f, 1f) * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(timeAgo(detection.timeMs, nowMs), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.alert_replay_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
