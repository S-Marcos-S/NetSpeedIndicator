package com.mss.netspeedindicator.ui.stats

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.mss.netspeedindicator.R
import com.mss.netspeedindicator.models.AppUsageInfo
import com.mss.netspeedindicator.models.TimePeriodStats
import com.mss.netspeedindicator.models.AppUsageSegment
import com.mss.netspeedindicator.models.DataPoint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkPermissionAndLoadData(context)
    }

    var showAppBreakdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estatísticas de Uso") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PeriodSelector(selectedPeriod) { viewModel.setPeriod(it, context) }

            when (val state = uiState) {
                is StatsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is StatsUiState.NoPermission -> {
                    PermissionRequestUI {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
                is StatsUiState.Success -> {
                    StatsContent(
                        stats = state.stats,
                        showAppBreakdown = showAppBreakdown,
                        onShowAppBreakdownChange = { showAppBreakdown = it }
                    )
                }
                is StatsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Erro: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodSelector(selected: StatsPeriod, onSelected: (StatsPeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatsPeriod.values().forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelected(period) },
                label = {
                    Text(when(period) {
                        StatsPeriod.TODAY -> "Hoje"
                        StatsPeriod.LAST_7_DAYS -> "7 dias"
                        StatsPeriod.LAST_30_DAYS -> "30 dias"
                    })
                }
            )
        }
    }
}

@Composable
fun StatsContent(
    stats: TimePeriodStats,
    showAppBreakdown: Boolean,
    onShowAppBreakdownChange: (Boolean) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            UsageSummary(stats)
        }
        item {
            if (showAppBreakdown) {
                AppBreakdownChart(stats)
            } else {
                UsageChart(stats)
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ver consumo detalhado por app no gráfico",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showAppBreakdown,
                    onCheckedChange = onShowAppBreakdownChange
                )
            }
        }
        item {
            Text(
                "Aplicativos que mais consumiram",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        items(stats.topApps) { appInfo ->
            AppUsageItem(appInfo)
        }
    }
}

@Composable
fun UsageSummary(stats: TimePeriodStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Dados Móveis", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatBytes(stats.totalMobile), 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Wi-Fi", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatBytes(stats.totalWifi), 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun UsageChart(stats: TimePeriodStats) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(320.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Gráfico de Consumo (MB)", 
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            val mobileEntries = stats.dataPoints.mapIndexed { index, data -> 
                entryOf(index.toFloat(), data.mobileData.toFloat() / (1024 * 1024)) 
            }
            val wifiEntries = stats.dataPoints.mapIndexed { index, data -> 
                entryOf(index.toFloat(), data.wifiData.toFloat() / (1024 * 1024)) 
            }
            
            val model = entryModelOf(mobileEntries, wifiEntries)
            
            val horizontalAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                stats.dataPoints.getOrNull(value.toInt())?.label ?: ""
            }

            val chartColors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )

            Chart(
                chart = columnChart(
                    columns = chartColors.map { color ->
                        LineComponent(
                            color = color.toArgb(),
                            thicknessDp = 8f,
                            shape = Shapes.roundedCornerShape(20)
                        )
                    }
                ),
                model = model,
                startAxis = rememberStartAxis(
                    valueFormatter = { value, _ -> "${value.toInt()} MB" }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = horizontalAxisValueFormatter
                ),
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                LegendItem("Dados Móveis", MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem("Wi-Fi", MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AppUsageItem(appInfo: AppUsageInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        appInfo.icon?.let {
            Image(
                bitmap = it.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(appInfo.appName, fontWeight = FontWeight.Bold)
            Text(
                "M: ${formatBytes(appInfo.mobileData)} | W: ${formatBytes(appInfo.wifiData)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(formatBytes(appInfo.totalData), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PermissionRequestUI(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info, 
            contentDescription = null, 
            modifier = Modifier.size(64.dp), 
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Permissão Necessária",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Para mostrar quais aplicativos estão consumindo sua internet, precisamos da permissão de 'Acesso ao Uso'. Seus dados não saem do dispositivo.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGrant) {
            Text("Conceder Acesso nas Configurações")
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format("%d KB", bytes / 1024)
        else -> "$bytes B"
    }
}

@Composable
fun AppBreakdownChart(stats: TimePeriodStats) {
    val maxData = stats.dataPoints.maxOfOrNull { it.mobileData + it.wifiData }?.toFloat() ?: 1f
    var selectedDataPoint by remember { mutableStateOf<DataPoint?>(null) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(320.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Consumo Detalhado por Aplicativo (MB)", 
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                YAxisLabels(maxData = maxData)
                
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 12.dp)
                        .padding(bottom = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    stats.dataPoints.forEach { dataPoint ->
                        BarColumn(
                            dataPoint = dataPoint,
                            maxData = maxData,
                            onClick = { selectedDataPoint = dataPoint }
                        )
                    }
                }
            }
        }
    }
    
    selectedDataPoint?.let { dataPoint ->
        ZoomColumnDialog(
            dataPoint = dataPoint,
            onDismiss = { selectedDataPoint = null }
        )
    }
}

@Composable
fun YAxisLabels(maxData: Float) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        val maxMb = maxData / (1024 * 1024)
        Text(formatMb(maxMb), style = MaterialTheme.typography.bodySmall)
        Text(formatMb(maxMb / 2), style = MaterialTheme.typography.bodySmall)
        Text("0 MB", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatMb(mb: Float): String {
    return if (mb >= 1024f) {
        String.format("%.1f GB", mb / 1024f)
    } else {
        String.format("%.0f MB", mb)
    }
}

@Composable
fun BarColumn(dataPoint: DataPoint, maxData: Float, onClick: () -> Unit) {
    val segmentsSum = dataPoint.appSegments.sumOf { it.bytes }.toFloat()
    
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(44.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val emptyWeight = (maxData - segmentsSum).coerceAtLeast(0f)
                if (emptyWeight > 0f) {
                    Spacer(modifier = Modifier.weight(emptyWeight))
                }
                
                dataPoint.appSegments.forEach { segment ->
                    val segmentWeight = segment.bytes.toFloat()
                    if (segmentWeight > 0f) {
                        AppSegmentBox(segment = segment, modifier = Modifier.weight(segmentWeight))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = dataPoint.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AppSegmentBox(segment: AppUsageSegment, modifier: Modifier) {
    val color = Color(segment.color)
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(color)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (maxHeight >= 20.dp && segment.icon != null) {
            Image(
                bitmap = segment.icon.toBitmap().asImageBitmap(),
                contentDescription = segment.appName,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoomColumnDialog(dataPoint: DataPoint, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        title = {
            Text(
                text = "Detalhes: ${dataPoint.label}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        dataPoint.appSegments.forEach { segment ->
                            val weight = segment.bytes.toFloat()
                            if (weight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxWidth()
                                        .background(Color(segment.color))
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (maxHeight >= 20.dp && segment.icon != null) {
                                            Image(
                                                bitmap = segment.icon.toBitmap().asImageBitmap(),
                                                contentDescription = segment.appName,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dataPoint.appSegments) { segment ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(segment.color), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            segment.icon?.let {
                                Image(
                                    bitmap = it.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = segment.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatBytes(segment.bytes),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
