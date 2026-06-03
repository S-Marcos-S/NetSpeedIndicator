package com.mss.netspeedindicator.ui.stats

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mss.netspeedindicator.models.TimePeriodStats
import com.mss.netspeedindicator.repository.NetworkStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class StatsPeriod {
    TODAY, LAST_7_DAYS, LAST_30_DAYS
}

sealed class StatsUiState {
    object Loading : StatsUiState()
    object NoPermission : StatsUiState()
    data class Success(val stats: TimePeriodStats) : StatsUiState()
    data class Error(val message: String) : StatsUiState()
}

class StatsViewModel(private val repository: NetworkStatsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState

    private val _selectedPeriod = MutableStateFlow(StatsPeriod.TODAY)
    val selectedPeriod: StateFlow<StatsPeriod> = _selectedPeriod

    fun checkPermissionAndLoadData(context: Context) {
        if (hasPermission(context)) {
            loadData()
        } else {
            _uiState.value = StatsUiState.NoPermission
        }
    }

    fun setPeriod(period: StatsPeriod, context: Context) {
        _selectedPeriod.value = period
        checkPermissionAndLoadData(context)
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = StatsUiState.Loading
            try {
                val (start, end) = getRangeForPeriod(_selectedPeriod.value)
                val stats = repository.getStatsForPeriod(start, end)
                _uiState.value = StatsUiState.Success(stats)
            } catch (e: Exception) {
                _uiState.value = StatsUiState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }

    private fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getRangeForPeriod(period: StatsPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        
        when (period) {
            StatsPeriod.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            StatsPeriod.LAST_7_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
            }
            StatsPeriod.LAST_30_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
            }
        }
        return calendar.timeInMillis to end
    }
}
