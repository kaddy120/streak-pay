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
import com.workpointstracker.data.model.WishItem
import com.workpointstracker.data.repository.SessionRepository
import com.workpointstracker.data.repository.SettingsRepository
import com.workpointstracker.data.repository.WishItemRepository
import com.workpointstracker.domain.service.TimerService
import com.workpointstracker.domain.usecase.Badge
import com.workpointstracker.domain.usecase.BadgeCalculator
import com.workpointstracker.domain.usecase.PointsCalculator
import com.workpointstracker.domain.usecase.StreakInfo
import com.workpointstracker.domain.usecase.StreakManager
import com.workpointstracker.util.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val wishItemRepository = WishItemRepository(database.wishItemDao())

    private val pointsCalculator = PointsCalculator()
    private val streakManager = StreakManager(settingsRepository)
    private val badgeCalculator = BadgeCalculator()

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

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId

    private val _encouragementData = MutableStateFlow(EncouragementData())
    val encouragementData: StateFlow<EncouragementData> = _encouragementData

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
            userName = settings?.userName ?: "Kaddy"
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
                            _currentSessionId.value = null
                        }
                        is TimerService.TimerState.Running -> {
                            _timerRunning.value = true
                            _timerPaused.value = false
                            _timerElapsedSeconds.value = state.elapsedSeconds
                            // Sync session ID: prefer local value, fallback to service value
                            val serviceSessionId = timerService?.getCurrentSessionId()
                            if (_currentSessionId.value != null && serviceSessionId == null) {
                                timerService?.setCurrentSessionId(_currentSessionId.value!!)
                            } else if (serviceSessionId != null) {
                                _currentSessionId.value = serviceSessionId
                            }
                            updateCanStopTimer(state.elapsedSeconds)
                        }
                        is TimerService.TimerState.Paused -> {
                            _timerRunning.value = false
                            _timerPaused.value = true
                            _timerElapsedSeconds.value = state.elapsedSeconds
                            // Sync session ID: prefer local value, fallback to service value
                            val serviceSessionId = timerService?.getCurrentSessionId()
                            if (_currentSessionId.value != null && serviceSessionId == null) {
                                timerService?.setCurrentSessionId(_currentSessionId.value!!)
                            } else if (serviceSessionId != null) {
                                _currentSessionId.value = serviceSessionId
                            }
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
        refreshEncouragementData()
    }

    fun syncTimerStartTimeFromDatabase() {
        viewModelScope.launch {
            val service = timerService ?: return@launch
            val sessionId = service.getCurrentSessionId() ?: return@launch
            val session = sessionRepository.getSessionById(sessionId) ?: return@launch
            val serviceStartTime = service.getStartTime() ?: return@launch

            // If the database has a different start time, update the service
            if (session.startTime != serviceStartTime) {
                service.setStartTime(session.startTime)
            }
        }
    }

    fun refreshEncouragementData() {
        viewModelScope.launch {
            val streakInfo = streakManager.getStreakInfo()
            val points = totalPoints.first() ?: 0.0
            val allSessions = sessionRepository.getAllCompletedSessions().first()
            val badges = badgeCalculator.calculateEarnedBadges(allSessions, streakInfo.currentStreak, points)
            val highlightedBadges = badgeCalculator.getHighlightedBadges(badges)
            val message = streakManager.getMotivationalMessage(streakInfo, badges)

            // Get next affordable wish item
            val availableWishItems = wishItemRepository.getAvailableWishItems().first()
            val nextWishItem = findNextAffordableWishItem(availableWishItems, points)

            _encouragementData.value = EncouragementData(
                streakInfo = streakInfo,
                badges = badges,
                highlightedBadges = highlightedBadges,
                motivationalMessage = message,
                nextWishItem = nextWishItem,
                pointsToNextWishItem = nextWishItem?.let {
                    (FormatUtils.priceToPoints(it.price) - points).coerceAtLeast(0.0)
                }
            )
        }
    }

    private fun findNextAffordableWishItem(items: List<WishItem>, currentPoints: Double): WishItem? {
        // Find the item that requires the least additional points
        return items
            .filter { !it.isRedeemed }
            .minByOrNull { FormatUtils.priceToPoints(it.price) - currentPoints }
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
                    com.workpointstracker.data.model.AppSettings(userName = "Kaddy")
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
        viewModelScope.launch {
            // Create session in database first
            val startTime = LocalDateTime.now()
            val sessionType = pointsCalculator.determineSessionType(startTime)

            val session = Session(
                startTime = startTime,
                endTime = null,
                durationMinutes = 0,
                pointsEarned = 0.0,
                type = sessionType,
                isPaused = false
            )

            val sessionId = sessionRepository.insertSession(session)

            // Start the timer service
            val intent = Intent(getApplication(), TimerService::class.java).apply {
                action = TimerService.ACTION_START
            }
            getApplication<Application>().startService(intent)

            // Set the session ID in the service after it starts
            timerService?.setCurrentSessionId(sessionId)
            _currentSessionId.value = sessionId
        }
    }

    fun pauseTimer() {
        timerService?.pauseTimer()
        viewModelScope.launch {
            val sessionId = _currentSessionId.value ?: return@launch
            val session = sessionRepository.getSessionById(sessionId) ?: return@launch
            val updatedSession = session.copy(
                isPaused = true,
                pausedAt = LocalDateTime.now()
            )
            sessionRepository.updateSession(updatedSession)
        }
    }

    fun resumeTimer() {
        timerService?.resumeTimer()
        viewModelScope.launch {
            val sessionId = _currentSessionId.value ?: return@launch
            val session = sessionRepository.getSessionById(sessionId) ?: return@launch
            val pausedAt = session.pausedAt
            val additionalPausedMinutes = if (pausedAt != null) {
                java.time.temporal.ChronoUnit.MINUTES.between(pausedAt, LocalDateTime.now())
            } else 0L
            val updatedSession = session.copy(
                isPaused = false,
                pausedAt = null,
                totalPausedMinutes = session.totalPausedMinutes + additionalPausedMinutes
            )
            sessionRepository.updateSession(updatedSession)
        }
    }

    fun stopTimer() {
        val service = timerService ?: return
        val sessionId = service.getCurrentSessionId()
        val elapsedSeconds = service.stopTimer()
        val serviceStartTime = service.getStartTime() ?: return
        val totalPausedSeconds = service.getTotalPausedSeconds()

        viewModelScope.launch {
            // Get start time from database (may have been edited) or fall back to service time
            val startTime = if (sessionId != null) {
                sessionRepository.getSessionById(sessionId)?.startTime ?: serviceStartTime
            } else {
                serviceStartTime
            }

            // Recalculate elapsed seconds based on potentially edited start time
            val now = java.time.LocalDateTime.now()
            val actualElapsedSeconds = java.time.temporal.ChronoUnit.SECONDS.between(startTime, now) - totalPausedSeconds

            saveSession(sessionId, startTime, actualElapsedSeconds, totalPausedSeconds)
        }
    }

    private suspend fun saveSession(
        sessionId: Long?,
        startTime: LocalDateTime,
        elapsedSeconds: Long,
        totalPausedSeconds: Long
    ) {
        val durationMinutes = elapsedSeconds / 60

        // Minimum 15 minutes - discard shorter sessions
        if (durationMinutes < 15) {
            sessionId?.let { id ->
                sessionRepository.getSessionById(id)?.let { session ->
                    sessionRepository.deleteSession(session)
                }
            }
            return
        }

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

        val endTime = LocalDateTime.now()
        val totalPausedMinutes = totalPausedSeconds / 60

        if (sessionId != null) {
            // Update existing session
            val existingSession = sessionRepository.getSessionById(sessionId)
            if (existingSession != null) {
                val updatedSession = existingSession.copy(
                    startTime = startTime,
                    endTime = endTime,
                    durationMinutes = durationMinutes,
                    pointsEarned = calculationResult.points,
                    type = calculationResult.sessionType,
                    isPaused = false,
                    pausedAt = null,
                    totalPausedMinutes = totalPausedMinutes
                )
                sessionRepository.updateSession(updatedSession)
            }
        } else {
            // Fallback: create new session if ID is not available
            val session = Session(
                startTime = startTime,
                endTime = endTime,
                durationMinutes = durationMinutes,
                pointsEarned = calculationResult.points,
                type = calculationResult.sessionType,
                isPaused = false,
                totalPausedMinutes = totalPausedMinutes
            )
            sessionRepository.insertSession(session)
        }

        // Check if streak should be updated (only for qualifying sessions)
        if (calculationResult.sessionType != com.workpointstracker.data.model.SessionType.DAY_JOB) {
            // Get total qualifying minutes for today (session already saved, so it's included)
            val totalQualifyingMinutes = sessionRepository.getTotalQualifyingMinutesForDate(today)

            // Only update streak and grace period if daily threshold (60 min) is met
            if (totalQualifyingMinutes >= 60) {
                streakManager.updateStreak(today, endTime)
            }
        }

        // Refresh encouragement data after session completion
        refreshEncouragementData()
    }

    private fun updateCanStopTimer(elapsedSeconds: Long) {
        // Stop button is always enabled - sessions under 15 minutes will be discarded
        _canStopTimer.value = true
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

data class EncouragementData(
    val streakInfo: StreakInfo? = null,
    val badges: List<Badge> = emptyList(),
    val highlightedBadges: List<Badge> = emptyList(),
    val motivationalMessage: String = "",
    val nextWishItem: WishItem? = null,
    val pointsToNextWishItem: Double? = null
)
