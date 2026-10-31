package com.aware.app.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import com.aware.app.speech.TranscriptLine
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import com.aware.app.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aware.app.R
import com.aware.app.service.ListeningRepository
import com.aware.app.ui.theme.AwareAccent
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** A quiet viewing surface: signer above, live caption text below. */
@Composable
fun AslCaptionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lines by ListeningRepository.transcript.collectAsStateWithLifecycle()
    val active by ListeningRepository.captions.collectAsStateWithLifecycle()
    val speechError by ListeningRepository.speechError.collectAsStateWithLifecycle()
    val scroll = rememberLazyListState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var avatarReady by remember { mutableStateOf(false) }
    var avatarFailed by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    val batcher = remember(avatarVersion) { CaptionBatcher(lines) }
    var signing by remember { mutableStateOf<SigningHighlight?>(null) }
    // Translation updates must not keep restarting partial-word stability timers.
    val speechRevision = lines.map { Triple(it.id, it.text, it.isFinal) }

    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        ListeningRepository.setCaptions(context, true)
        onDispose {
            view.keepScreenOn = previous
            ListeningRepository.setCaptions(context, false)
        }
    }

    // Keep the caption being performed visible as the recognizer adds new lines.
    LaunchedEffect(speechRevision, signing?.lineId) {
        if (lines.isNotEmpty()) {
            val index = lines.indexOfFirst { it.id == signing?.lineId }
            scroll.animateScrollToItem(if (index >= 0) index else lines.lastIndex)
        }
    }
    LaunchedEffect(avatarVersion, avatarReady, avatarFailed) {
        if (!avatarReady && !avatarFailed) {
            delay(30_000)
            avatarFailed = true
        }
    }
    LaunchedEffect(speechRevision, active, avatarReady, avatarFailed, webViewRef) {
        if (!active || lines.isEmpty()) {
            webViewRef?.evaluateJavascript("window.clearSigns?.()", null)
            signing = null
        }
        val ready = avatarReady && !avatarFailed && webViewRef != null
        fun send(settled: Boolean = false) {
            val batch = batcher.next(lines, active, ready, settled)
            if (batch.isNotEmpty()) {
                val payload = JSONArray()
                batch.forEach { payload.put(JSONObject()
                    .put("id", it.id).put("lineId", it.lineId.toString())
                    .put("text", it.text).put("start", it.start).put("end", it.end)) }
                webViewRef?.evaluateJavascript("window.enqueueCaptions($payload)", null)
            }
        }
        send()
        if (active && ready && lines.any { !it.isFinal }) {
            delay(350)
            send()
            delay(450)
            send(settled = true)
        }
    }

    AwareBackdrop {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AwareHeader(stringResource(R.string.ui_live_talk), onBack)
            LivePanelLayout(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color(0xFFF3F8F2))
                .border(1.dp, Color.White, RoundedCornerShape(24.dp))) {
                key(avatarVersion) {
                    AvatarWebView(
                        onWebViewCreated = { webViewRef = it },
                        onReady = { avatarReady = true; avatarFailed = false },
                        onSigningStarted = { id, lineId, start, end ->
                            val sourceId = lineId.toLongOrNull()
                            if (active && sourceId != null && lines.any { it.id == sourceId }) {
                                signing = SigningHighlight(id, sourceId, start, end)
                            }
                        },
                        onSigningComplete = { if (signing?.id == it) signing = null },
                        onError = {
                            avatarFailed = true
                            signing = null
                            webViewRef?.evaluateJavascript("window.clearSigns?.()", null)
                        },
                    )
                }
                if (avatarFailed) {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.avatar_failed), color = AwareSecondary, textAlign = TextAlign.Center)
                        TextButton(onClick = {
                            avatarReady = false
                            avatarFailed = false
                            signing = null
                            webViewRef = null
                            avatarVersion++
                        }) { Text(stringResource(R.string.avatar_retry)) }
                    }
                } else if (!avatarReady) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = AwareAccent, strokeWidth = 2.dp)
                }
            }
            TranscriptPanel(lines, signing, scroll, active, speechError != null, ListeningRepository::clearTranscript)
            }
        }
    }
}

/** Same two child instances in either arrangement, including the measured WebView. */
@Composable
private fun LivePanelLayout(modifier: Modifier, content: @Composable () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    val wideMinimum = with(LocalDensity.current) { 600.dp.roundToPx() }
    Layout(content, modifier) { children, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val wide = width >= wideMinimum && width > height
        val spacing = minOf(gap, if (wide) width else height)
        val first = if (wide) ((width - spacing) * .55f).toInt() else ((height - spacing) * if (largeText) .45f else .55f).toInt()
        val avatar = children[0].measure(androidx.compose.ui.unit.Constraints.fixed(if (wide) first else width, if (wide) height else first))
        val captions = children[1].measure(androidx.compose.ui.unit.Constraints.fixed(if (wide) width - spacing - first else width, if (wide) height else height - spacing - first))
        layout(width, height) {
            avatar.placeRelative(0, 0)
            captions.placeRelative(if (wide) first + spacing else 0, if (wide) 0 else first + spacing)
        }
    }
}

/**
 * The live line is the strongest thing in the box and earlier lines recede, so the eye stays on the
 * newest words. The signing highlight and its semantics are unchanged.
 */
@Composable
private fun TranscriptPanel(lines: List<TranscriptLine>, signing: SigningHighlight?, scroll: LazyListState, active: Boolean, hasError: Boolean, onClear: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxSize().shadow(6.dp, shape, ambientColor = Color(0xFF8FC5A2), spotColor = Color(0xFF8FC5A2))
        .clip(shape).background(Color.White).border(1.dp, Color.White, shape)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) AwareGreen else AwareSecondary))
            Text(stringResource(if (active) R.string.ui_listening else R.string.ui_paused), Modifier.weight(1f), color = AwareSecondary,
                style = MaterialTheme.typography.labelLarge)
            if (lines.isNotEmpty()) TextButton(onClear, Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                Text(stringResource(R.string.captions_clear), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        HorizontalDivider(color = AwareGroup)
        if (lines.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Hearing, null, Modifier.size(40.dp), tint = if (hasError) MaterialTheme.colorScheme.error else AwareAccent)
                Text(stringResource(if (hasError) R.string.ui_speech_error else R.string.captions_waiting),
                    Modifier.padding(top = 12.dp), color = if (hasError) MaterialTheme.colorScheme.error else AwareSecondary,
                    fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), state = scroll, verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
            itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
                val latest = index == lines.lastIndex
                val highlight = signing?.takeIf { it.lineId == line.id }
                val caption = AnnotatedString.Builder(line.text).apply {
                    if (highlight != null && highlight.start in 0 until highlight.end && highlight.end <= line.text.length) {
                        addStyle(SpanStyle(color = AwareAccent, background = Color(0xFFDDF8D7)), highlight.start, highlight.end)
                    }
                }.toAnnotatedString()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        caption,
                        modifier = Modifier.semantics {
                            if (highlight != null) stateDescription = "signing:${highlight.start}:${highlight.end}"
                        },
                        color = if (latest) AwareInk else AwareInk.copy(alpha = .65f),
                        fontSize = 28.sp,
                        lineHeight = 38.sp,
                        fontWeight = if (latest) FontWeight.Medium else FontWeight.Normal,
                    )
                    line.translation?.takeIf {
                        it.isNotBlank() && !it.trim().equals(line.text.trim(), ignoreCase = true)
                    }?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(AwareAccent.copy(alpha = .5f)))
                            Text(it, color = AwareSecondary, fontSize = 24.sp, lineHeight = 32.sp)
                        }
                    }
                }
            }
        }
    }
}
