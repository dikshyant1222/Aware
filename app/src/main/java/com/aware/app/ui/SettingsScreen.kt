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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.aware.app.ui.theme.*
import android.content.pm.ApplicationInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
fun SettingsScreen(onBack: () -> Unit, onTestHaptics: () -> Unit) {
    val context = LocalContext.current
    val repository = context.settingsRepository
    val stored by repository.settings.collectAsStateWithLifecycle<Settings?>(null)
    val scope = rememberCoroutineScope()
    val settings = stored
    if (settings == null) { CircularProgressIndicator(); return }
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
    AwareBackdrop {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AwareHeader(stringResource(R.string.settings_title), onBack)
            SettingsGroup(stringResource(R.string.ui_sound_categories)) {
            Category.entries.forEach { category ->
                val label = categoryName(category)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (LocalDensity.current.fontScale <= 1.3f) TintedIconWell(categoryIcon(category), categoryColor(category))
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = category in settings.enabledCategories,
                        modifier = Modifier.semantics { contentDescription = label },
                        onCheckedChange = { enabled -> scope.launch { repository.setCategory(category, enabled) } })
                }
            }
            }
            SettingsGroup(stringResource(R.string.profile_title)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TintedIconWell(Icons.Default.Person, AwareAccent)
                    Column(Modifier.weight(1f)) {
                        Text(settings.userName, style = MaterialTheme.typography.titleMedium)
                        if (settings.dateOfBirth.isNotEmpty()) Text(java.time.LocalDate.parse(settings.dateOfBirth)
                            .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)),
                            style = MaterialTheme.typography.bodyMedium, color = AwareSecondary)
                    }
                    Icon(Icons.Default.Lock, stringResource(R.string.profile_fixed), tint = AwareSecondary)
                }
                Text(stringResource(R.string.profile_name_detection), color = AwareAccent, style = MaterialTheme.typography.bodyMedium)
            }
            SettingsGroup(stringResource(R.string.ui_detection)) {
            Text(stringResource(R.string.ui_sensitivity_hint), color = AwareSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_sensitivity, (sensitivity * 100).roundToInt()), style = MaterialTheme.typography.titleLarge)
            val sensitivityLabel = stringResource(R.string.settings_sensitivity, (sensitivity * 100).roundToInt())
            Slider(value = sensitivity, modifier = Modifier.semantics { contentDescription = sensitivityLabel }, onValueChange = { value ->
                sensitivity = value
                scope.launch { repository.setSensitivity(value) }
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.sensitivity_low)); Text(stringResource(R.string.sensitivity_high))
            }
            }
            SettingsGroup(stringResource(R.string.ui_language_speech)) {
            Text(stringResource(R.string.settings_translation), style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Translate, null, Modifier.size(20.dp))
                    Text(settings.translationTarget?.let { Locale.forLanguageTag(it).displayName } ?: stringResource(R.string.translation_none), Modifier.weight(1f).padding(horizontal = 12.dp))
                    Icon(Icons.Default.ExpandMore, null)
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
            }

            SettingsGroup(stringResource(R.string.llm_setting)) { SmartSummarySetting() }
            if (Build.VERSION.SDK_INT >= 34) SettingsGroup(stringResource(R.string.ui_device_permissions)) { FullScreenAlertSetting() }
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                var toolsExpanded by rememberSaveable { mutableStateOf(false) }
                TextButton({ toolsExpanded = !toolsExpanded }) { Text(stringResource(R.string.ui_developer_tools)) }
                if (toolsExpanded) SettingsGroup(stringResource(R.string.ui_developer_tools)) {
                    TextButton(onTestHaptics) { Text(stringResource(R.string.home_test_haptics)) }
                    SoundDiagnostics()
                }
            }
        }
    }
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
    if (allowed) Text(stringResource(R.string.ui_full_screen_allowed), color = AwareAccent)
    else Card(onClick = {
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Hearing, null, Modifier.size(20.dp), tint = AwareSecondary)
        Text(stringResource(status), Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
        IconButton({ refresh++ }) { Icon(Icons.Default.Refresh, stringResource(R.string.speech_refresh)) }
    }
    if (canDownload) Button(onClick = {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                recognizer?.triggerModelDownload(recognitionIntent())
                status = R.string.speech_download_requested
            } catch (_: RuntimeException) { status = R.string.model_download_failed }
        }
    }, modifier = Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.speech_download)) }

}
