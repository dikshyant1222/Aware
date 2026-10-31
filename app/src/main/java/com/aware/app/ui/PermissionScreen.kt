package com.aware.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.aware.app.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aware.app.R

@Composable
fun PermissionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val hasPermissions = {
        permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
    var permissionsGranted by remember { mutableStateOf(hasPermissions()) }
    var requestAttempted by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        requestAttempted = true
        permissionsGranted = hasPermissions()
    }

    // Recheck permissions when returning from app settings, including revocations.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = hasPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (permissionsGranted) {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "home"
        var summaryOpen by rememberSaveable { mutableStateOf(false) }
        val openSettings = { navController.navigate("settings") { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }
        val openCaptions = { navController.navigate("captions") { launchSingleTop = true } }
        Box(modifier.fillMaxSize()) {
        NavHost(navController, startDestination = "home", modifier = modifier,
            enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 32 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(180)) }) {
            composable("home") { HomeScreen(
                onOpenSummary = { summaryOpen = true },
            ) }
            composable("settings") { SettingsScreen(
                onBack = { navController.popBackStack() },
                onTestHaptics = { navController.navigate("haptics") },
            ) }
            composable("captions") { AslCaptionsScreen(onBack = { navController.popBackStack() }) }
            composable("history") { SummaryHistoryScreen(onBack = { navController.popBackStack() }) }
            composable("haptics") { HapticTestScreen(onBack = { navController.popBackStack() }) }
        }
        if (route in setOf("home", "settings", "history")) AwareFloatingDock(
            route,
            onHome = { navController.popBackStack("home", inclusive = false) },
            onCaptions = openCaptions, onHistory = { navController.navigate("history") { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, onSettings = openSettings,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 30.dp, vertical = 12.dp))
        }
        if (summaryOpen) SummarySheet { summaryOpen = false }
        return
    }

    PermissionEntry(modifier, requestAttempted, onGrant = { permissionLauncher.launch(permissions) }, onOpenSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    })
}

@Composable
internal fun PermissionEntry(modifier: Modifier = Modifier, requestAttempted: Boolean = false,
    onGrant: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    AwareBackdrop {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        TintedIconWell(Icons.Default.Mic, AwareAccent, Modifier.align(Alignment.CenterHorizontally).size(80.dp))
        Text(stringResource(R.string.ui_permission_welcome), fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.ui_permission_subtitle), color = AwareSecondary)
        Surface(shape = RoundedCornerShape(24.dp), color = AwareSurface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.permission_microphone_explanation),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                text = stringResource(R.string.permission_notifications_explanation),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        }
        }
        if (requestAttempted) {
            Text(
                text = stringResource(R.string.permission_denied),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_app_settings))
            }
        }
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (requestAttempted) R.string.try_permissions_again else R.string.grant_permissions,
                ),
            )
        }
    }
    }
}
