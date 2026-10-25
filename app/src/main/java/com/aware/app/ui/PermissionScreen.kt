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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
        NavHost(navController, startDestination = "home", modifier = modifier) {
            composable("home") { HomeScreen(
                onTestHaptics = { navController.navigate("haptics") },
                onSettings = { navController.navigate("settings") },
                onCaptions = { navController.navigate("captions") { launchSingleTop = true } },
                onDemo = { navController.navigate("demo") { launchSingleTop = true } },
            ) }
            composable("settings") { SettingsScreen(
                onBack = { navController.popBackStack() },
                onTestHaptics = { navController.navigate("haptics") },
                onCaptions = { navController.navigate("captions") { launchSingleTop = true } },
            ) }
            composable("captions") { AslCaptionsScreen(onBack = { navController.popBackStack() }) }
            composable("haptics") { HapticTestScreen(onBack = { navController.popBackStack() }) }
            composable("demo") { DemoScreen(onBack = { navController.popBackStack() }) }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
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
        if (requestAttempted) {
            Text(
                text = stringResource(R.string.permission_denied),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_app_settings))
            }
        }
        Button(
            onClick = { permissionLauncher.launch(permissions) },
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
