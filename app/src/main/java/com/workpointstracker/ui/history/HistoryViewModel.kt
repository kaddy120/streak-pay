package com.workpointstracker.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workpointstracker.data.local.database.WorkPointsDatabase
import com.workpointstracker.data.model.SessionType
import com.workpointstracker.data.repository.SessionRepository
import com.workpointstracker.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WorkPointsDatabase.getDatabase(application)
    private val sessionRepository = SessionRepository(database.sessionDao())
    private val settingsRepository = SettingsRepository(
        database.appSettingsDao(),
        database.dailyGoalDao()
    )

    val totalPoints = sessionRepository.getTotalPoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val appSettings = settingsRepository.getAppSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dailyGoal = settingsRepository.getDailyGoal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedPeriod = MutableStateFlow(TimePeriod.DAY)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod

    private val _statsData = MutableStateFlow<StatsData?>(null)
    val statsData: StateFlow<StatsData?> = _statsData

    init {
        loadStats()
    }

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val (startDate, endDate) = when (_selectedPeriod.value) {
                TimePeriod.DAY -> Pair(today, today)
                TimePeriod.WEEK -> Pair(today.minusDays(6), today)
                TimePeriod.MONTH -> Pair(today.minusDays(29), today)
                TimePeriod.YEAR -> Pair(today.minusDays(364), today)
            }

            val sessions = sessionRepository.getSessionsInDateRange(startDate, endDate)

            val dayJobMinutes = sessions
                .filter { it.type == SessionType.DAY_JOB }
                .sumOf { it.durationMinutes }

            val sideWorkMinutes = sessions
                .filter { it.type != SessionType.DAY_JOB }
                .sumOf { it.durationMinutes }

            val totalPoints = sessions.sumOf { it.pointsEarned }

            _statsData.value = StatsData(
                dayJobHours = dayJobMinutes / 60.0,
                sideWorkHours = sideWorkMinutes / 60.0,
                totalPoints = totalPoints
            )
        }
    }
}

enum class TimePeriod {
    DAY, WEEK, MONTH, YEAR
}

data class StatsData(
    val dayJobHours: Double,
    val sideWorkHours: Double,
    val totalPoints: Double
)
