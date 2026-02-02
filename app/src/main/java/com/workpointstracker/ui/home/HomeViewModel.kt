package com.workpointstracker.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workpointstracker.data.local.database.WorkPointsDatabase
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.repository.SessionRepository
import com.workpointstracker.data.repository.SettingsRepository
import com.workpointstracker.domain.service.TimerService
import com.workpointstracker.domain.usecase.PointsCalculator
import com.workpointstracker.domain.usecase.StreakManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WorkPointsDatabase.getDatabase(application)
    private val sessionRepository = SessionRepository(database.sessionDao())
    private val settingsRepository = SettingsRepository(
        database.appSettingsDao(),
        database.dailyGoalDao()
    )

    private val pointsCalculator = PointsCalculator()
    private val streakManager = StreakManager(settingsRepository)

    private var timerService: TimerService? = null
    private var serviceBound = false

    private val _timerElapsedSeconds = MutableStateFlow(0L)
    val timerElapsedSeconds: StateFlow<Long> = _timerElapsedSeconds

    private val _timerRunning = MutableStateFlow(false)
    val timerRunning: StateFlow<Boolean> = _timerRunning

    private val _timerPaused = MutableStateFlow(false)
    val timerPaused: StateFlow<Boolean> = _timerPaused

    private val _canStopTimer = MutableStateFlow(false)
    val canStopTimer: StateFlow<Boolean> = _canStopTimer

    val totalPoints = sessionRepository.getTotalPoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val recentSessions = sessionRepository.getRecentSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings = settingsRepository.getAppSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState = combine(
        totalPoints,
        appSettings
    ) { points, settings ->
        HomeUiState(
            totalPoints = points ?: 0.0,
            currentStreak = settings?.currentStreak ?: 0,
            userName = settings?.userName ?: "User"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            serviceBound = true

            viewModelScope.launch {
                timerService?.timerState?.collect { state ->
                    when (state) {
                        is TimerService.TimerState.Idle -> {
                            _timerRunning.value = false
                            _timerPaused.value = false
                            _timerElapsedSeconds.value = 0
                            _canStopTimer.value = false
                        }
                        is TimerService.TimerState.Running -> {
                            _timerRunning.value = true
                            _timerPaused.value = false
                            _timerElapsedSeconds.value = state.elapsedSeconds
                            updateCanStopTimer(state.elapsedSeconds)
                        }
                        is TimerService.TimerState.Paused -> {
                            _timerRunning.value = false
                            _timerPaused.value = true
                            _timerElapsedSeconds.value = state.elapsedSeconds
                            updateCanStopTimer(state.elapsedSeconds)
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            serviceBound = false
        }
    }

    init {
        bindTimerService()
        initializeDefaultSettings()
    }

    private fun bindTimerService() {
        val intent = Intent(getApplication(), TimerService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initializeDefaultSettings() {
        viewModelScope.launch {
            val settings = settingsRepository.getAppSettingsOnce()
            if (settings == null) {
                settingsRepository.insertAppSettings(
                    com.workpointstracker.data.model.AppSettings(userName = "User")
                )
            }

            val goal = settingsRepository.getDailyGoalOnce()
            if (goal == null) {
                settingsRepository.insertDailyGoal(
                    com.workpointstracker.data.model.DailyGoal()
                )
            }
        }
    }

    fun startTimer() {
        val intent = Intent(getApplication(), TimerService::class.java).apply {
            action = TimerService.ACTION_START
        }
        getApplication<Application>().startService(intent)
    }

    fun pauseTimer() {
        timerService?.pauseTimer()
    }

    fun resumeTimer() {
        timerService?.resumeTimer()
    }

    fun stopTimer() {
        val service = timerService ?: return
        val elapsedSeconds = service.stopTimer()
        val startTime = service.getStartTime() ?: return

        viewModelScope.launch {
            saveSession(startTime, elapsedSeconds)
        }
    }

    private suspend fun saveSession(startTime: LocalDateTime, elapsedSeconds: Long) {
        val durationMinutes = elapsedSeconds / 60
        if (durationMinutes < 1) return

        val today = LocalDate.now()
        val completedSessionsToday = sessionRepository.getCompletedSessionsCountForDate(today)
        val isFirstSessionOfDay = completedSessionsToday == 0

        val currentStreak = streakManager.getCurrentStreak()
        val calculationResult = pointsCalculator.calculatePoints(
            startTime = startTime,
            durationMinutes = durationMinutes,
            streakDays = currentStreak,
            isFirstSessionOfDay = isFirstSessionOfDay
        )

        val session = Session(
            startTime = startTime,
            endTime = LocalDateTime.now(),
            durationMinutes = durationMinutes,
            pointsEarned = calculationResult.points,
            type = calculationResult.sessionType,
            isPaused = false
        )

        sessionRepository.insertSession(session)

        // Update streak only if side work
        if (calculationResult.sessionType != com.workpointstracker.data.model.SessionType.DAY_JOB) {
            streakManager.updateStreak(today)
        }
    }

    private fun updateCanStopTimer(elapsedSeconds: Long) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val completedSessionsToday = sessionRepository.getCompletedSessionsCountForDate(today)
            val isFirstSessionOfDay = completedSessionsToday == 0

            val elapsedMinutes = elapsedSeconds / 60
            _canStopTimer.value = !isFirstSessionOfDay || elapsedMinutes >= 60
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
    }
}

data class HomeUiState(
    val totalPoints: Double = 0.0,
    val currentStreak: Int = 0,
    val userName: String = "User"
)
