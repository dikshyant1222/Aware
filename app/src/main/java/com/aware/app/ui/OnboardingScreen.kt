package com.aware.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aware.app.R
import com.aware.app.data.Settings
import com.aware.app.data.settingsRepository
import com.aware.app.data.validProfile
import com.aware.app.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AwareApp() {
    val repository = LocalContext.current.settingsRepository
    val settings by repository.settings.collectAsStateWithLifecycle<Settings?>(null)
    val profile = settings
    when {
        profile == null -> AwareBackdrop { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        !profile.onboardingComplete -> OnboardingScreen(profile.userName, repository::completeOnboarding)
        else -> PermissionScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingScreen(initialName: String = "", onComplete: suspend (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var dob by rememberSaveable { mutableStateOf("") }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var responses by rememberSaveable { mutableStateOf("") }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val back = { if (!saving) { if (step == 2) responses = responses.dropLast(1); step = (step - 1).coerceAtLeast(0) } }
    BackHandler(step > 0 || saving, back)
    AwareBackdrop {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AwareHeader(stringResource(R.string.setup_welcome), if (step > 0) back else null)
            Text(stringResource(R.string.setup_step, step + 1), color = AwareSecondary, style = MaterialTheme.typography.labelLarge)
            when (step) {
                0 -> {
                    TintedIconWell(Icons.Default.Person, AwareAccent)
                    Text(stringResource(R.string.setup_profile_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.setup_profile_body), color = AwareSecondary)
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.setup_name)) },
                        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedButton({ dateOpen = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Text(if (dob.isEmpty()) stringResource(R.string.setup_dob) else LocalDate.parse(dob)
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), Modifier.weight(1f).padding(horizontal = 12.dp))
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    Text(stringResource(R.string.setup_local), color = AwareSecondary, style = MaterialTheme.typography.bodySmall)
                    Button({ responses = ""; step = 1 }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = validProfile(name, dob)) {
                        Text(stringResource(R.string.setup_continue))
                    }
                }
                1 -> {
                    Text(stringResource(R.string.setup_sound_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.setup_sound_body), color = AwareSecondary)
                    val question = responses.length
                    SoundCheckStep(question) { heard ->
                        if (responses.length == question && question < 3) {
                            responses += if (heard) "Y" else "N"
                            if (responses.length == 3) step = 2
                        }
                    }
                }
                2 -> {
                    TintedIconWell(Icons.Default.CheckCircle, AwareAccent)
                    Text(stringResource(R.string.setup_done_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.setup_done_body), color = AwareSecondary)
                    if (failed) Text(stringResource(R.string.setup_save_failed), color = MaterialTheme.colorScheme.error)
                    Button({
                        scope.launch {
                            saving = true; failed = false
                            try { onComplete(name.trim(), dob, responses) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { saving = false }
                        }
                    }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !saving) {
                        Text(stringResource(if (saving) R.string.setup_saving else R.string.setup_finish))
                    }
                }
            }
        }
    }
    if (dateOpen) {
        val today = LocalDate.now()
        val picker = rememberDatePickerState(initialSelectedDateMillis = dob.takeIf { it.isNotEmpty() }
            ?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            yearRange = 1900..today.year, selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            })
        DatePickerDialog(onDismissRequest = { dateOpen = false }, confirmButton = {
            TextButton({
                dob = Instant.ofEpochMilli(checkNotNull(picker.selectedDateMillis)).atZone(ZoneOffset.UTC).toLocalDate().toString()
                dateOpen = false
            }, enabled = picker.selectedDateMillis != null) { Text(stringResource(R.string.setup_choose_date)) }
        }, dismissButton = { TextButton({ dateOpen = false }) { Text(stringResource(R.string.setup_cancel)) } }) { DatePicker(picker) }
    }
}

@Composable
private fun SoundCheckStep(index: Int, onAnswer: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 15) }.getOrNull() }
    var played by remember(index) { mutableStateOf(false) }
    var playing by remember(index) { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(tone == null) }
    DisposableEffect(tone, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { tone?.stopTone(); playing = false } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); tone?.stopTone(); tone?.release() }
    }
    Text(stringResource(R.string.setup_sound_number, index + 1), style = MaterialTheme.typography.titleLarge)
    Button({
        scope.launch {
            playing = true; played = true
            unavailable = runCatching { tone?.startTone(listOf(ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_4, ToneGenerator.TONE_DTMF_7)[index], 350) != true }.getOrDefault(true)
            delay(450); playing = false
        }
    }, Modifier.fillMaxWidth().heightIn(min = 64.dp), enabled = !playing) {
        Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(10.dp))
        Text(stringResource(if (playing) R.string.setup_playing else R.string.setup_play))
    }
    if (unavailable) Text(stringResource(R.string.setup_sound_unavailable), color = AwareSecondary)
    OutlinedButton({ onAnswer(true) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = played && !playing) { Text(stringResource(R.string.setup_heard)) }
    OutlinedButton({ onAnswer(false) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = played && !playing) { Text(stringResource(R.string.setup_not_heard)) }
}
