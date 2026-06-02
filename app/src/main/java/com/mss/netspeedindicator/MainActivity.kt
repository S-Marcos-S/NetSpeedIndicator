package com.mss.netspeedindicator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mss.netspeedindicator.data.AppSettings
import com.mss.netspeedindicator.service.SpeedMonitorService
import com.mss.netspeedindicator.ui.screens.SettingsScreen
import com.mss.netspeedindicator.ui.theme.NetSpeedIndicatorTheme

class MainActivity : ComponentActivity() {
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        setContent {
            NetSpeedIndicatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionWrapper {
                        MainNavigation(settings,
                            onStartService = { startSpeedService() },
                            onStopService = { stopSpeedService() }
                        )
                    }
                }
            }
        }
    }

    private fun startSpeedService() {
        val intent = Intent(this, SpeedMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSpeedService() {
        val intent = Intent(this, SpeedMonitorService::class.java)
        stopService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(settings: AppSettings, onStartService: () -> Unit, onStopService: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(
                            imageVector = if (showSettings) Icons.Default.Settings else Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            if (showSettings) {
                SettingsScreen(settings)
            } else {
                MainScreen(onStartService, onStopService)
            }
        }
    }
}

@Composable
fun PermissionWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
        }
    )

    if (hasNotificationPermission) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("O aplicativo precisa de permissão para mostrar notificações.")
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) {
                Text("Conceder Permissão")
            }
        }
    }
}

@Composable
fun MainScreen(onStartService: () -> Unit, onStopService: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Net Speed Indicator",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(
            onClick = onStartService,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text("Iniciar Serviço")
        }
        Button(onClick = onStopService) {
            Text("Parar Serviço")
        }
    }
}
