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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mss.netspeedindicator.data.AppSettings
import com.mss.netspeedindicator.service.SpeedMonitorService
import com.mss.netspeedindicator.ui.theme.NetSpeedIndicatorTheme

class MainActivity : ComponentActivity() {
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        updateServiceStatus()
        setContent {
            NetSpeedIndicatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionWrapper {
                        MainScreen(settings) { updateServiceStatus() }
                    }
                }
            }
        }
    }

    private fun updateServiceStatus() {
        val shouldRun = settings.isMasterEnabled && (settings.isRealTimeEnabled || settings.isDailyUsageEnabled)
        val intent = Intent(this, SpeedMonitorService::class.java)
        if (shouldRun) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(settings: AppSettings, onSettingsChanged: () -> Unit) {
    var masterEnabled by remember { mutableStateOf(settings.isMasterEnabled) }
    var realTimeEnabled by remember { mutableStateOf(settings.isRealTimeEnabled) }
    var dailyUsageEnabled by remember { mutableStateOf(settings.isDailyUsageEnabled) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsToggle(
                label = if (masterEnabled) "Aplicativo Ativado" else "Aplicativo Desativado",
                checked = masterEnabled,
                onCheckedChange = {
                    masterEnabled = it
                    settings.isMasterEnabled = it
                    onSettingsChanged()
                },
                isMaster = true
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                color = if (masterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingsToggle(
                label = stringResource(R.string.enable_real_time),
                checked = realTimeEnabled,
                enabled = masterEnabled,
                onCheckedChange = {
                    realTimeEnabled = it
                    settings.isRealTimeEnabled = it
                    onSettingsChanged()
                }
            )

            if (realTimeEnabled && masterEnabled) {
                ThresholdSettings(settings)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsToggle(
                label = stringResource(R.string.enable_daily_usage),
                checked = dailyUsageEnabled,
                enabled = masterEnabled,
                onCheckedChange = {
                    dailyUsageEnabled = it
                    settings.isDailyUsageEnabled = it
                    onSettingsChanged()
                }
            )
        }
    }
}

@Composable
fun ThresholdSettings(settings: AppSettings) {
    var valueStr by remember { mutableStateOf(settings.thresholdValue.let { if (it == 0f) "" else it.toString() }) }
    var unit by remember { mutableStateOf(settings.thresholdUnit) }
    val units = listOf("B/s", "KB/s", "MB/s")
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.min_speed_threshold),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = valueStr,
                onValueChange = {
                    if (it.isEmpty() || it.toFloatOrNull() != null) {
                        valueStr = it
                        settings.thresholdValue = it.toFloatOrNull() ?: 0f
                    }
                },
                label = { Text(stringResource(R.string.threshold_value)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(unit)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    units.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                unit = selectionOption
                                settings.thresholdUnit = selectionOption
                                expanded = false
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggle(
    label: String, 
    checked: Boolean, 
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    isMaster: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = if (isMaster) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
