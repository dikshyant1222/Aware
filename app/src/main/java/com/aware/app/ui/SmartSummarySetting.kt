package com.aware.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aware.app.R
import com.aware.app.llm.LlmStatus
import com.aware.app.llm.LiteRtLlmSummarizer
import com.aware.app.service.ListeningRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SmartSummarySetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val running by ListeningRepository.running.collectAsStateWithLifecycle()
    val serviceStatus by ListeningRepository.llmStatus.collectAsStateWithLifecycle()
    var installed by remember { mutableStateOf<LlmStatus>(LlmStatus.NOT_INSTALLED) }
    var hasModel by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh, running) {
        val snapshot = withContext(Dispatchers.IO) {
            LiteRtLlmSummarizer.installedStatus(context) to (LiteRtLlmSummarizer.modelFile(context) != null)
        }
        installed = snapshot.first
        hasModel = snapshot.second
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            progress = 0
            error = null
            try {
                LiteRtLlmSummarizer.importModel(context, uri) { percent -> withContext(Dispatchers.Main) { progress = percent } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: context.getString(R.string.llm_import_failed) }
            finally {
                progress = null
                refresh++
                ListeningRepository.modelChanged()
            }
        }
    }
    val status = if (running && serviceStatus != LlmStatus.NOT_INSTALLED) serviceStatus else installed
    Text(when (status) {
        LlmStatus.NOT_INSTALLED -> stringResource(R.string.llm_not_installed)
        LlmStatus.UNSUPPORTED -> stringResource(R.string.llm_unsupported)
        LlmStatus.IDLE -> stringResource(R.string.llm_idle)
        LlmStatus.LOADING -> stringResource(R.string.llm_loading)
        LlmStatus.READY -> stringResource(R.string.llm_ready)
        LlmStatus.DISABLED -> stringResource(R.string.llm_disabled)
        is LlmStatus.FAILED -> stringResource(R.string.llm_failed, status.message)
    }, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    Text(stringResource(R.string.llm_help), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = { launcher.launch(arrayOf("*/*")) }, enabled = progress == null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(stringResource(R.string.llm_import))
    }
    progress?.let { percent ->
        Text(stringResource(R.string.llm_import_progress, percent), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
    }
    OutlinedButton(onClick = {
        scope.launch {
            progress = 0
            error = null
            try { LiteRtLlmSummarizer.removeModel(context) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: context.getString(R.string.llm_remove_failed) }
            finally { progress = null; refresh++; ListeningRepository.modelChanged() }
        }
    }, enabled = hasModel && progress == null, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(stringResource(R.string.llm_remove))
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
}
