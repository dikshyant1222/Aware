package com.aware.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aware.app.R
import com.aware.app.haptics.HapticEvent
import com.aware.app.haptics.HapticPlayer

@Composable
fun HapticTestScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(context) { HapticPlayer(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            player.stop()
        }
    }
    LazyColumn(
        modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.haptics_back))
                Text(stringResource(R.string.haptics_back))
            }
            Text(stringResource(R.string.haptics_title), style = MaterialTheme.typography.headlineLarge)
        }
        items(HapticEvent.entries.filter { it != HapticEvent.SPEECH }, key = { it.name }) { event ->
            val name = event.soundType?.let { soundName(it) } ?: stringResource(R.string.sound_name_called)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryIcon(event.category, Modifier.size(48.dp))
                    Text(name, style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(patternDescription(event)), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { player.play(event) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.haptics_play, name))
                    }
                }
            }
        }
    }
}

private fun patternDescription(event: HapticEvent): Int = when (event) {
    HapticEvent.SIREN, HapticEvent.SMOKE_ALARM, HapticEvent.SCREAM, HapticEvent.URGENT -> R.string.pattern_rising
    HapticEvent.GLASS_BREAK -> R.string.pattern_glass
    HapticEvent.CAR_HORN -> R.string.pattern_horn
    HapticEvent.VEHICLE -> R.string.pattern_vehicle
    HapticEvent.BICYCLE_BELL -> R.string.pattern_bicycle
    HapticEvent.BABY_CRY -> R.string.pattern_heartbeat
    HapticEvent.DOORBELL, HapticEvent.KNOCK -> R.string.pattern_knock
    HapticEvent.PHONE_RING -> R.string.pattern_phone
    HapticEvent.APPLIANCE_BEEP -> R.string.pattern_beep
    HapticEvent.WATER_RUNNING -> R.string.pattern_water
    HapticEvent.DOG_BARK -> R.string.pattern_dog
    HapticEvent.CAT -> R.string.pattern_cat
    HapticEvent.BIRD -> R.string.pattern_bird
    HapticEvent.NAME_CALLED -> R.string.pattern_name
    HapticEvent.SPEECH -> R.string.pattern_silent
}
