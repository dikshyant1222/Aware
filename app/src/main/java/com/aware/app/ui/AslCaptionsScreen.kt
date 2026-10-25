package com.aware.app.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.55f)) {
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
                        Text(stringResource(R.string.avatar_failed), color = Color.LightGray, textAlign = TextAlign.Center)
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(0.45f),
                state = scroll,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            ) {
                if (lines.isEmpty()) item {
                    Text(stringResource(R.string.captions_waiting), color = Color.LightGray, fontSize = 26.sp)
                }
                items(lines, key = { it.id }) { line ->
                    val highlight = signing?.takeIf { it.lineId == line.id }
                    val caption = AnnotatedString.Builder(line.text).apply {
                        if (highlight != null && highlight.start in 0 until highlight.end && highlight.end <= line.text.length) {
                            addStyle(SpanStyle(color = AwareAccent, background = Color(0xFF26301C)), highlight.start, highlight.end)
                        }
                    }.toAnnotatedString()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            caption,
                            modifier = Modifier.semantics {
                                if (highlight != null) stateDescription = "signing:${highlight.start}:${highlight.end}"
                            },
                            color = if (line.isFinal) Color.White else Color.LightGray,
                            fontSize = 28.sp,
                            lineHeight = 36.sp,
                        )
                        line.translation?.takeIf {
                            it.isNotBlank() && !it.trim().equals(line.text.trim(), ignoreCase = true)
                        }?.let {
                            Text(it, color = Color.LightGray, fontSize = 24.sp, lineHeight = 32.sp)
                        }
                    }
                }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.haptics_back), tint = Color.White)
        }
    }
}
