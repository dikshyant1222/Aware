package com.aware.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aware.app.R
import com.aware.app.llm.Summary
import com.aware.app.service.ListeningRepository
import com.aware.app.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AwareBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to AwareSky, .4f to AwareMint, .7f to Color(0xFFEFF5EF), 1f to AwareBackground,
        endY = with(density) { 335.dp.toPx() },
    )), content = content)
}

@Composable
fun SoftCircleButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick, Modifier.size(48.dp), enabled = enabled) {
        Surface(shape = CircleShape, color = Color.White.copy(alpha = .85f),
            border = BorderStroke(1.dp, Color.White), shadowElevation = 1.dp) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(icon, description, Modifier.size(22.dp), tint = if (enabled) AwareInk else AwareSecondary.copy(alpha = .45f))
            }
        }
    }
}

@Composable
fun AwareHeader(title: String, onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
        Text(title, Modifier.padding(horizontal = 60.dp), style = MaterialTheme.typography.titleLarge)
        Box(Modifier.align(Alignment.CenterStart)) {
            if (onBack != null) SoftCircleButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.haptics_back), onBack)
            else leading?.invoke()
        }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing?.invoke() }
    }
}

@Composable
fun TintedIconWell(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(46.dp).clip(CircleShape).background(Brush.verticalGradient(
        listOf(Color.White, tint.copy(alpha = .17f)))), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(25.dp), tint = tint)
    }
}

@Composable
fun SoftActionCard(title: String, subtitle: String, icon: ImageVector, tint: Color,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: ImageVector? = null) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White,
        border = BorderStroke(1.dp, tint.copy(alpha = .08f)), shadowElevation = 1.dp) {
        Row(Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 76.dp).padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TintedIconWell(icon, tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = AwareSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (trailing != null) Box(Modifier.size(36.dp).clip(CircleShape).background(AwareGroup), contentAlignment = Alignment.Center) {
                Icon(trailing, null, Modifier.size(18.dp), tint = AwareSecondary)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (action != null) Box(Modifier.align(Alignment.End)) { DottedAction(action, onAction) }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (action != null) DottedAction(action, onAction)
    }
}

@Composable
private fun DottedAction(action: String, onAction: () -> Unit) {
        TextButton(onAction, Modifier.heightIn(min = 48.dp)) {
            Text(action, style = MaterialTheme.typography.labelLarge, modifier = Modifier.drawBehind {
                drawLine(AwareAccent, Offset(0f, size.height), Offset(size.width, size.height),
                    1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
            })
        }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AwareGroup).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.padding(horizontal = 8.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
fun ListeningHero(running: Boolean, alertsPaused: Boolean, onToggle: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "listening")
    val phase by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "halo")
    val status = stringResource(if (!running) R.string.ui_paused else if (alertsPaused) R.string.ui_alerts_paused else R.string.ui_listening)
    val halo = running && !alertsPaused
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
            // Two soft rings drift outward and fade, half a cycle apart; they stop when listening stops.
            if (halo) Canvas(Modifier.fillMaxSize()) {
                val inner = 44.dp.toPx()
                val outer = size.minDimension / 2
                for (offset in listOf(0f, .5f)) {
                    val progress = (phase + offset) % 1f
                    drawCircle(AwareGreen.copy(alpha = (1f - progress) * .32f), inner + (outer - inner) * progress,
                        style = Stroke(2.dp.toPx()))
                }
                drawCircle(AwareGreen.copy(alpha = .14f), 52.dp.toPx())
            }
            FilledIconButton(onToggle, Modifier.size(76.dp).shadow(8.dp, CircleShape, ambientColor = Color(0xFF8FC5A2), spotColor = Color(0xFF8FC5A2))
                .semantics { stateDescription = status },
                shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White, contentColor = if (halo) AwareAccent else AwareInk)) {
                Icon(if (running) Icons.Default.Mic else Icons.Default.MicOff,
                    stringResource(if (running) R.string.home_stop_listening else R.string.home_start_listening), Modifier.size(32.dp))
            }
        }
        Text(status, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (!running) Text(stringResource(R.string.ui_tap_to_start), color = AwareSecondary,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun SummaryPreview(summary: Summary?, running: Boolean, timeMs: Long, nowMs: Long, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(onClick = onOpen, shape = shape, color = Color(0xFF303738), contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF94BAA6).copy(alpha = .4f)), shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().shadow(16.dp, shape, ambientColor = Color(0xFF8FC5A2), spotColor = Color(0xFF8FC5A2))) {
        Column(Modifier.heightIn(min = 110.dp).background(Brush.linearGradient(listOf(Color(0xFF37423F), Color(0xFF292E31))))
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val sentence = summary?.text ?: stringResource(if (running) R.string.summary_auto_waiting else R.string.ui_summary_paused)
            AnimatedContent(sentence, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "summary text") { text ->
                Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (summary != null) Text(timeAgo(timeMs, nowMs), Modifier.align(Alignment.End), color = Color(0xFFD1DBD6), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarySheet(onDismiss: () -> Unit) {
    val summary by ListeningRepository.situation.collectAsStateWithLifecycle()
    val time by ListeningRepository.situationTimeMs.collectAsStateWithLifecycle()
    val running by ListeningRepository.running.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AwareHeader(stringResource(R.string.ui_summary), trailing = { SoftCircleButton(Icons.Default.Close, stringResource(R.string.ui_close), onDismiss) })
            Text(summary?.text ?: stringResource(if (running) R.string.ui_summary_waiting else R.string.ui_summary_paused),
                style = MaterialTheme.typography.titleLarge)
            if (summary != null) Text(timeAgo(time, System.currentTimeMillis()), color = AwareSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AwareFloatingDock(selected: String, onHome: () -> Unit, onCaptions: () -> Unit, onHistory: () -> Unit,
    onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(Triple("home", Icons.Default.Home, R.string.ui_home),
        Triple("captions", Icons.Default.ClosedCaption, R.string.ui_live_talk),
        Triple("history", Icons.Default.History, R.string.summary_history_pill),
        Triple("settings", Icons.Default.Settings, R.string.settings_title))
    val actions = listOf(onHome, onCaptions, onHistory, onSettings)
    val compact = LocalDensity.current.fontScale > 1.2f
    Surface(modifier.widthIn(max = 300.dp).fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .97f),
        border = BorderStroke(1.dp, Color.White), shadowElevation = 8.dp) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, (route, icon, label) ->
                val active = route == selected
                val weight by animateFloatAsState(if (active && !compact) 1.75f else 1f, tween(260), label = "dock pill")
                Row(Modifier.weight(weight).heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(50)).background(if (active) Color(0xFFDEDEDE) else Color.Transparent)
                    .clickable(onClick = actions[index]).semantics(mergeDescendants = true) {
                        this.selected = active; role = Role.Tab
                    }.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, if (active && !compact) null else stringResource(if (route == "history") R.string.summary_history_action else label), Modifier.size(22.dp), tint = if (active) AwareInk else AwareSecondary)
                    if (active && !compact) Text(stringResource(label), Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
