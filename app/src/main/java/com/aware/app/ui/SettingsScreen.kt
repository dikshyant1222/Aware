package com.aware.app.ui

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aware.app.R
import com.aware.app.alert.Category
import com.aware.app.data.Settings
import com.aware.app.data.settingsRepository
import com.aware.app.speech.LiveTranslator
import com.aware.app.speech.recognitionIntent
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onTestHaptics: () -> Unit, onCaptions: () -> Unit) {
    val context = LocalContext.current
    val repository = context.settingsRepository
    val stored by repository.settings.collectAsStateWithLifecycle<Settings?>(null)
    val scope = rememberCoroutineScope()
    val settings = stored
    if (settings == null) { CircularProgressIndicator(); return }
    var name by rememberSaveable { mutableStateOf(settings.userName) }
    var sensitivity by rememberSaveable { mutableFloatStateOf(settings.sensitivity) }
    var expanded by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableIntStateOf(0) }
    var downloadClient by remember { mutableStateOf<Translator?>(null) }
    var downloadGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { downloadGeneration++; downloadClient?.close() } }
    val languages = remember { listOf(
        TranslateLanguage.ENGLISH, TranslateLanguage.SPANISH, TranslateLanguage.FRENCH,
        TranslateLanguage.GERMAN, TranslateLanguage.ITALIAN, TranslateLanguage.PORTUGUESE,
        TranslateLanguage.CHINESE, TranslateLanguage.JAPANESE, TranslateLanguage.KOREAN,
        TranslateLanguage.HINDI, TranslateLanguage.ARABIC, TranslateLanguage.RUSSIAN,
    ) }
    fun selectLanguage(code: String?) {
        expanded = false
        val token = ++downloadGeneration
        downloadClient?.close()
        downloadClient = null
        scope.launch { repository.setTranslationTarget(code) }
        downloadStatus = if (code == null) 0 else R.string.model_downloading
        if (code != null) {
            downloadClient = LiveTranslator.download(code) { success ->
                if (token == downloadGeneration) {
                    downloadStatus = if (success) R.string.model_downloaded else R.string.model_download_failed
                    downloadClient?.close()
                    downloadClient = null
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.haptics_back)) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
            Category.entries.forEach { category ->
                val label = categoryName(category)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryIcon(category, Modifier.size(32.dp))
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Switch(checked = category in settings.enabledCategories,
                        modifier = Modifier.semantics { contentDescription = label },
                        onCheckedChange = { enabled -> scope.launch { repository.setCategory(category, enabled) } })
                }
            }
            OutlinedTextField(name, onValueChange = { value ->
                name = value
                scope.launch { repository.setUserName(value) }
            }, label = { Text(stringResource(R.string.settings_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val detectLabel = stringResource(R.string.settings_detect_name)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(detectLabel, Modifier.weight(1f))
                Switch(settings.nameDetection, { enabled -> scope.launch { repository.setNameDetection(enabled) } },
                    modifier = Modifier.semantics { contentDescription = detectLabel })
            }
            if (Build.VERSION.SDK_INT < 33) Text(stringResource(R.string.name_legacy_limitation))
            Text(stringResource(R.string.settings_sensitivity, (sensitivity * 100).roundToInt()), style = MaterialTheme.typography.titleLarge)
            val sensitivityLabel = stringResource(R.string.settings_sensitivity, (sensitivity * 100).roundToInt())
            Slider(value = sensitivity, modifier = Modifier.semantics { contentDescription = sensitivityLabel }, onValueChange = { value ->
                sensitivity = value
                scope.launch { repository.setSensitivity(value) }
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.sensitivity_low)); Text(stringResource(R.string.sensitivity_high))
            }
            Text(stringResource(R.string.settings_translation), style = MaterialTheme.typography.titleLarge)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text(settings.translationTarget?.let { Locale.forLanguageTag(it).displayName } ?: stringResource(R.string.translation_none))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.translation_none)) }, onClick = { selectLanguage(null) })
                    languages.forEach { code ->
                        DropdownMenuItem(text = { Text(Locale.forLanguageTag(code).displayName) }, onClick = { selectLanguage(code) })
                    }
                }
            }
            if (downloadStatus != 0) Text(stringResource(downloadStatus))
            if (downloadStatus == R.string.model_downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
            OfflineSpeechStatus()
            FullScreenAlertSetting()
            TextButton(onClick = onTestHaptics, modifier = Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.home_test_haptics)) }
        }
        CaptionsBubble(onCaptions, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
private fun FullScreenAlertSetting() {
    if (Build.VERSION.SDK_INT < 34) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val notifications = remember { context.getSystemService(NotificationManager::class.java) }
    var allowed by remember { mutableStateOf(notifications.canUseFullScreenIntent()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = notifications.canUseFullScreenIntent() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!allowed) Card(onClick = {
        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.fromParts("package", context.packageName, null)))
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Text(stringResource(R.string.settings_full_screen), Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun OfflineSpeechStatus() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var status by remember { mutableIntStateOf(R.string.speech_checking) }
    var canDownload by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle, refresh) {
        var disposed = false
        var device: SpeechRecognizer? = null
        fun checkStatus() {
            if (disposed) return
            try {
                val onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
                    status = R.string.speech_unavailable
                    return
                }
                if (device == null) device = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = device
                canDownload = Build.VERSION.SDK_INT >= 33
                if (Build.VERSION.SDK_INT >= 33) {
                    device!!.checkRecognitionSupport(recognitionIntent(), context.mainExecutor, object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            if (disposed) return
                            val locale = Locale.getDefault()
                            val installed = support.installedOnDeviceLanguages.any {
                                it.equals(locale.toLanguageTag(), true) || it.equals(locale.language, true)
                            }
                            status = if (installed) R.string.speech_ready else if (onDevice) R.string.speech_download else R.string.speech_network
                            canDownload = !installed
                        }
                        override fun onError(error: Int) {
                            if (!disposed) status = if (onDevice) R.string.speech_status_unknown else R.string.speech_network
                        }
                    })
                } else status = if (onDevice) R.string.speech_ready else R.string.speech_network
            } catch (_: RuntimeException) { status = R.string.speech_unavailable; canDownload = false }
        }
        checkStatus()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checkStatus() }
        lifecycle.addObserver(observer)
        onDispose { disposed = true; lifecycle.removeObserver(observer); device?.destroy(); recognizer = null }
    }
    Text(stringResource(status), style = MaterialTheme.typography.titleLarge)
    if (Build.VERSION.SDK_INT in 31..32 && status == R.string.speech_ready) Text(stringResource(R.string.speech_locale_unverified))
    if (canDownload) Button(onClick = {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                recognizer?.triggerModelDownload(recognitionIntent())
                status = R.string.speech_download_requested
            } catch (_: RuntimeException) { status = R.string.model_download_failed }
        }
    }, modifier = Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.speech_download)) }
    TextButton(onClick = { refresh++ }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.speech_refresh)) }
}
