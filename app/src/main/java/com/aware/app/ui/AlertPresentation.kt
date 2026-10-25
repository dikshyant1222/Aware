package com.aware.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aware.app.R
import com.aware.app.alert.Category
import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import com.aware.app.ui.theme.CategoryColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun CategoryIcon(category: Category, modifier: Modifier = Modifier) {
    Icon(categoryIcon(category), contentDescription = categoryName(category), tint = categoryColor(category), modifier = modifier)
}

@Composable
fun categoryName(category: Category): String = stringResource(when (category) {
        Category.EMERGENCY -> R.string.category_emergency
        Category.TRAFFIC -> R.string.category_traffic
        Category.HOME -> R.string.category_home
        Category.PEOPLE -> R.string.category_people
        Category.ANIMALS -> R.string.category_animals
    })

fun categoryColor(category: Category): Color = when (category) {
    Category.EMERGENCY -> CategoryColors.EMERGENCY
    Category.TRAFFIC -> CategoryColors.TRAFFIC
    Category.HOME -> CategoryColors.HOME
    Category.PEOPLE -> CategoryColors.PEOPLE
    Category.ANIMALS -> CategoryColors.ANIMALS
}

fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.EMERGENCY -> Icons.Default.Warning
    Category.TRAFFIC -> Icons.Default.DirectionsCar
    Category.HOME -> Icons.Default.Home
    Category.PEOPLE -> Icons.Default.Person
    Category.ANIMALS -> Icons.Default.Pets
}

@Composable
fun trendText(trend: Trend): String = stringResource(when (trend) {
    Trend.APPROACHING -> R.string.trend_approaching
    Trend.STEADY -> R.string.trend_steady
    Trend.FADING -> R.string.trend_fading
})

@Composable
fun timeAgo(timeMs: Long, nowMs: Long): String {
    val seconds = ((nowMs - timeMs) / 1000).coerceAtLeast(0)
    return when {
        seconds < 10 -> stringResource(R.string.ui_just_now)
        seconds < 60 -> stringResource(R.string.home_seconds_ago, seconds)
        seconds < 3600 -> stringResource(R.string.ui_minutes_ago, seconds / 60)
        seconds < 86400 -> stringResource(R.string.ui_hours_ago, seconds / 3600)
        else -> pluralStringResource(R.plurals.ui_days_ago, (seconds / 86400).toInt(), seconds / 86400)
    }
}

fun soundIcon(type: SoundType): ImageVector = when (type) {
    SoundType.SIREN, SoundType.SMOKE_ALARM -> Icons.Default.Warning
    SoundType.GLASS_BREAK -> Icons.Default.BrokenImage
    SoundType.SCREAM, SoundType.SPEECH -> Icons.Default.RecordVoiceOver
    SoundType.CAR_HORN, SoundType.VEHICLE -> Icons.Default.DirectionsCar
    SoundType.BICYCLE_BELL -> Icons.Default.PedalBike
    SoundType.DOORBELL -> Icons.Default.Doorbell
    SoundType.KNOCK -> Icons.Default.MeetingRoom
    SoundType.PHONE_RING -> Icons.Default.PhoneInTalk
    SoundType.APPLIANCE_BEEP -> Icons.Default.Kitchen
    SoundType.WATER_RUNNING -> Icons.Default.WaterDrop
    SoundType.BABY_CRY -> Icons.Default.ChildCare
    SoundType.DOG_BARK, SoundType.CAT, SoundType.FARM_ANIMAL -> Icons.Default.Pets
    SoundType.BIRD -> Icons.Default.Park
}

@Composable
fun soundName(type: SoundType): String = stringResource(when (type) {
    SoundType.SIREN -> R.string.sound_siren
    SoundType.SMOKE_ALARM -> R.string.sound_smoke_alarm
    SoundType.GLASS_BREAK -> R.string.sound_glass_break
    SoundType.SCREAM -> R.string.sound_scream
    SoundType.CAR_HORN -> R.string.sound_car_horn
    SoundType.VEHICLE -> R.string.sound_vehicle
    SoundType.BICYCLE_BELL -> R.string.sound_bicycle_bell
    SoundType.DOORBELL -> R.string.sound_doorbell
    SoundType.KNOCK -> R.string.sound_knock
    SoundType.PHONE_RING -> R.string.sound_phone_ring
    SoundType.APPLIANCE_BEEP -> R.string.sound_appliance_beep
    SoundType.WATER_RUNNING -> R.string.sound_water_running
    SoundType.BABY_CRY -> R.string.sound_baby_cry
    SoundType.SPEECH -> R.string.sound_speech
    SoundType.DOG_BARK -> R.string.sound_dog_bark
    SoundType.CAT -> R.string.sound_cat
    SoundType.FARM_ANIMAL -> R.string.sound_farm_animal
    SoundType.BIRD -> R.string.sound_bird
})
