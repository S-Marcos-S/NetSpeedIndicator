package com.mss.netspeedindicator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mss.netspeedindicator.R
import com.mss.netspeedindicator.data.AppSettings

@Composable
fun SettingsScreen(settings: AppSettings) {
    var realTimeEnabled by remember { mutableStateOf(settings.isRealTimeEnabled) }
    var dailyUsageEnabled by remember { mutableStateOf(settings.isDailyUsageEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSection(title = stringResource(R.string.real_time_speed)) {
            SettingsToggle(
                label = stringResource(R.string.enable_real_time),
                checked = realTimeEnabled,
                onCheckedChange = {
                    realTimeEnabled = it
                    settings.isRealTimeEnabled = it
                }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        SettingsSection(title = stringResource(R.string.daily_usage)) {
            SettingsToggle(
                label = stringResource(R.string.enable_daily_usage),
                checked = dailyUsageEnabled,
                onCheckedChange = {
                    dailyUsageEnabled = it
                    settings.isDailyUsageEnabled = it
                }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
