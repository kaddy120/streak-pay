package com.workpointstracker.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workpointstracker.data.local.database.WorkPointsDatabase
import com.workpointstracker.data.model.SessionType
import com.workpointstracker.data.repository.SessionRepository
import com.workpointstracker.data.repository.SettingsRepository
import android.util.Log
import com.workpointstracker.BuildConfig
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.remote.ApiClient
import com.workpointstracker.data.remote.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    private val apiService: ApiService by lazy {
        ApiClient.getService(BuildConfig.API_BASE_URL, BuildConfig.API_KEY)
    }

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
            fetchTotalPoints()
            fetchStreak()
            fetchGoals()
        }
    }

    private suspend fun fetchTotalPoints() {
        try {
            val response = apiService.getTotalPoints()
            _totalPoints.value = response.totalPoints
        } catch (e: Exception) {
            Log.w("HistoryVM", "API points fetch failed, falling back to Room", e)
            val roomPoints = sessionRepository.getTotalPoints().first()
            _totalPoints.value = roomPoints
        }
    }

    private suspend fun fetchStreak() {
        try {
            val response = apiService.getStreakInfo()
            _currentStreak.value = response.currentStreak
        } catch (e: Exception) {
            Log.w("HistoryVM", "API streak fetch failed, falling back to Room", e)
            val settings = settingsRepository.getAppSettingsOnce()
            _currentStreak.value = settings?.currentStreak ?: 0
        }
    }

    private suspend fun fetchGoals() {
        try {
            val response = apiService.getGoals()
            _dayJobGoalHours.value = response.dayJobHours
            _sideWorkGoalHours.value = response.sideWorkHours
        } catch (e: Exception) {
            Log.w("HistoryVM", "API goals fetch failed, falling back to Room", e)
            val goal = settingsRepository.getDailyGoalOnce()
            _dayJobGoalHours.value = goal?.dayJobHours ?: 7.5
            _sideWorkGoalHours.value = goal?.sideWorkHours ?: 4.0
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

            val sessions: List<Session> = try {
                apiService.getSessions(startDate.toString(), endDate.toString())
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
            } catch (e: Exception) {
                Log.w("HistoryVM", "API sessions fetch failed, falling back to Room", e)
                sessionRepository.getSessionsInDateRange(startDate, endDate)
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
