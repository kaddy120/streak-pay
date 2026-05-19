package com.workpointstracker.ui.history

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workpointstracker.WorkPointsApplication
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.model.SessionType
import com.workpointstracker.data.remote.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService: ApiService = (application as WorkPointsApplication).apiService

    private val _totalPoints = MutableStateFlow<Double?>(0.0)
    val totalPoints: StateFlow<Double?> = _totalPoints

    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak

    private val _dayJobGoalHours = MutableStateFlow(7.5)
    val dayJobGoalHours: StateFlow<Double> = _dayJobGoalHours

    private val _sideWorkGoalHours = MutableStateFlow(4.0)
    val sideWorkGoalHours: StateFlow<Double> = _sideWorkGoalHours

    private val _selectedPeriod = MutableStateFlow(TimePeriod.DAY)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod

    private val _statsData = MutableStateFlow<StatsData?>(null)
    val statsData: StateFlow<StatsData?> = _statsData

    init {
        loadStats()
        viewModelScope.launch {
            try { _totalPoints.value = apiService.getTotalPoints().totalPoints } catch (e: Exception) {
                Log.w("HistoryVM", "Points fetch failed", e)
            }
            try { _currentStreak.value = apiService.getStreakInfo().currentStreak } catch (e: Exception) {
                Log.w("HistoryVM", "Streak fetch failed", e)
            }
            try {
                val goals = apiService.getGoals()
                _dayJobGoalHours.value = goals.dayJobHours
                _sideWorkGoalHours.value = goals.sideWorkHours
            } catch (e: Exception) {
                Log.w("HistoryVM", "Goals fetch failed", e)
            }
        }
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

            try {
                val sessions = apiService.getSessions(startDate.toString(), endDate.toString())
                    .filter { it.endTime != null }
                    .map { resp ->
                        Session(
                            id = resp.id,
                            startTime = resp.startTime,
                            endTime = resp.endTime,
                            durationMinutes = resp.durationMinutes,
                            pointsEarned = resp.pointsEarned,
                            type = SessionType.valueOf(resp.type.name),
                            isPaused = resp.isPaused,
                            pausedAt = resp.pausedAt,
                            totalPausedSeconds = resp.totalPausedSeconds
                        )
                    }

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
            } catch (e: Exception) {
                Log.w("HistoryVM", "Sessions fetch failed", e)
            }
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
