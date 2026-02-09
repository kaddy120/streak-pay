package com.workpointstracker.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workpointstracker.BuildConfig
import com.workpointstracker.data.local.database.WorkPointsDatabase
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.model.SessionType
import com.workpointstracker.data.model.WishItem
import com.workpointstracker.data.remote.ApiClient
import com.workpointstracker.data.remote.ApiService
import com.workpointstracker.data.remote.CreateSessionRequest
import com.workpointstracker.data.remote.SessionResponse
import com.workpointstracker.data.remote.UpdateSessionRequest
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
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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

    // API sync: tracks the API-assigned ID for the current local session
    private val _apiSessionId = MutableStateFlow<Long?>(null)

    // Remote session state
    private val apiService: ApiService by lazy {
        ApiClient.getService(BuildConfig.API_BASE_URL, BuildConfig.API_KEY)
    }
    private val _remoteSession = MutableStateFlow<SessionResponse?>(null)
    val remoteSession: StateFlow<SessionResponse?> = _remoteSession
    private val _remoteElapsedSeconds = MutableStateFlow(0L)
    val remoteElapsedSeconds: StateFlow<Long> = _remoteElapsedSeconds
    private var remotePollingJob: Job? = null
    private var remoteTickJob: Job? = null

    private val _totalPoints = MutableStateFlow<Double?>(0.0)
    val totalPoints: StateFlow<Double?> = _totalPoints

    private val _currentStreak = MutableStateFlow(0)

    private val _recentSessions = MutableStateFlow<List<Session>>(emptyList())
    val recentSessions: StateFlow<List<Session>> = _recentSessions

    private val _userName = MutableStateFlow("Kaddy")

    val appSettings = settingsRepository.getAppSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState = combine(
        totalPoints,
        _currentStreak,
        _userName
    ) { points, streak, name ->
        HomeUiState(
            totalPoints = points ?: 0.0,
            currentStreak = streak,
            userName = name
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
                            startRemotePolling()
                        }
                        is TimerService.TimerState.Running -> {
                            stopRemotePolling()
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
                            stopRemotePolling()
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
        startRemotePolling()
        viewModelScope.launch {
            fetchTotalPoints()
            fetchStreak()
            fetchSettings()
            fetchRecentSessions()
        }
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

    private suspend fun fetchTotalPoints() {
        try {
            val response = apiService.getTotalPoints()
            _totalPoints.value = response.totalPoints
        } catch (e: Exception) {
            Log.w("HomeVM", "API points fetch failed, falling back to Room", e)
            val roomPoints = sessionRepository.getTotalPoints().first()
            _totalPoints.value = roomPoints
        }
    }

    private suspend fun fetchStreak() {
        try {
            val response = apiService.getStreakInfo()
            _currentStreak.value = response.currentStreak
        } catch (e: Exception) {
            Log.w("HomeVM", "API streak fetch failed, falling back to Room", e)
            val settings = settingsRepository.getAppSettingsOnce()
            _currentStreak.value = settings?.currentStreak ?: 0
        }
    }

    private suspend fun fetchSettings() {
        try {
            val response = apiService.getSettings()
            _userName.value = response.userName.ifEmpty { "Kaddy" }
        } catch (e: Exception) {
            Log.w("HomeVM", "API settings fetch failed, falling back to Room", e)
            val settings = settingsRepository.getAppSettingsOnce()
            _userName.value = settings?.userName ?: "Kaddy"
        }
    }

    private suspend fun fetchRecentSessions() {
        try {
            val responses = apiService.getSessions()
            val sessions = responses
                .filter { it.endTime != null }
                .sortedByDescending { it.startTime }
                .take(10)
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
            _recentSessions.value = sessions
        } catch (e: Exception) {
            Log.w("HomeVM", "API sessions fetch failed, falling back to Room", e)
            val roomSessions = sessionRepository.getRecentSessions().first()
            _recentSessions.value = roomSessions
        }
    }

    fun refreshEncouragementData() {
        viewModelScope.launch {
            val streakInfo = streakManager.getStreakInfo()
            val points = totalPoints.first() ?: 0.0

            // Fetch all completed sessions from API for badge calculation
            val allSessions = try {
                apiService.getSessions()
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
                Log.w("HomeVM", "API sessions fetch failed for badges, falling back to Room", e)
                sessionRepository.getAllCompletedSessions().first()
            }

            val badges = badgeCalculator.calculateEarnedBadges(allSessions, streakInfo.currentStreak, points)
            val highlightedBadges = badgeCalculator.getHighlightedBadges(badges)
            val message = streakManager.getMotivationalMessage(streakInfo, badges)

            // Get next affordable wish item from Room (wish items are local with device images)
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

            // Sync to API (fire-and-forget)
            try {
                val sharedType = com.workpointstracker.shared.models.SessionType.valueOf(sessionType.name)
                val apiResponse = apiService.createSession(
                    CreateSessionRequest(deviceId = "android", startTime = startTime, type = sharedType)
                )
                _apiSessionId.value = apiResponse.id
            } catch (_: Exception) { }
        }
    }

    fun pauseTimer() {
        timerService?.pauseTimer()
        val pausedAt = LocalDateTime.now()
        viewModelScope.launch {
            val sessionId = _currentSessionId.value ?: return@launch
            val session = sessionRepository.getSessionById(sessionId) ?: return@launch
            val updatedSession = session.copy(
                isPaused = true,
                pausedAt = pausedAt
            )
            sessionRepository.updateSession(updatedSession)

            // Sync to API (fire-and-forget)
            val apiId = _apiSessionId.value
            if (apiId != null) {
                try {
                    apiService.updateSession(apiId, UpdateSessionRequest(
                        isPaused = true,
                        pausedAt = pausedAt
                    ))
                } catch (_: Exception) { }
            }
        }
    }

    fun resumeTimer() {
        timerService?.resumeTimer()
        viewModelScope.launch {
            val sessionId = _currentSessionId.value ?: return@launch
            val session = sessionRepository.getSessionById(sessionId) ?: return@launch
            val pausedAt = session.pausedAt
            val additionalPausedSeconds = if (pausedAt != null) {
                ChronoUnit.SECONDS.between(pausedAt, LocalDateTime.now())
            } else 0L
            val newTotalPausedSeconds = session.totalPausedSeconds + additionalPausedSeconds
            val updatedSession = session.copy(
                isPaused = false,
                pausedAt = null,
                totalPausedSeconds = newTotalPausedSeconds
            )
            sessionRepository.updateSession(updatedSession)

            // Sync to API (fire-and-forget)
            val apiId = _apiSessionId.value
            if (apiId != null) {
                try {
                    apiService.updateSession(apiId, UpdateSessionRequest(
                        isPaused = false,
                        totalPausedSeconds = newTotalPausedSeconds
                    ))
                } catch (_: Exception) { }
            }
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
        val endTime = LocalDateTime.now()

        // Minimum 15 minutes - discard shorter sessions
        if (durationMinutes < 15) {
            sessionId?.let { id ->
                sessionRepository.getSessionById(id)?.let { session ->
                    sessionRepository.deleteSession(session)
                }
            }
            // Also delete from API if synced
            val apiId = _apiSessionId.value
            _apiSessionId.value = null
            if (apiId != null) {
                try { apiService.deleteSession(apiId) } catch (_: Exception) { }
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
                    totalPausedSeconds = totalPausedSeconds
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
                totalPausedSeconds = totalPausedSeconds
            )
            sessionRepository.insertSession(session)
        }

        // Sync end to API (fire-and-forget)
        val apiId = _apiSessionId.value
        _apiSessionId.value = null
        if (apiId != null) {
            try {
                apiService.updateSession(apiId, UpdateSessionRequest(
                    endTime = endTime,
                    isPaused = false,
                    durationMinutes = durationMinutes,
                    totalPausedSeconds = totalPausedSeconds
                ))
            } catch (_: Exception) { }
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

        // Refresh data from API after session completion
        fetchTotalPoints()
        fetchStreak()
        fetchRecentSessions()
        refreshEncouragementData()
    }

    private fun updateCanStopTimer(elapsedSeconds: Long) {
        // Stop button is always enabled - sessions under 15 minutes will be discarded
        _canStopTimer.value = true
    }

    // Remote session polling and control

    private fun startRemotePolling() {
        if (remotePollingJob?.isActive == true) return
        remotePollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val sessions = apiService.getActiveSessions()
                    Log.d("RemotePoll", "Active sessions: ${sessions.size}, devices: ${sessions.map { it.deviceId }}")
                    // Filter out this device's own sessions
                    val active = sessions.firstOrNull { it.deviceId != "android" }
                    _remoteSession.value = active
                    if (active != null) {
                        Log.d("RemotePoll", "Remote session found: id=${active.id}, device=${active.deviceId}, elapsed=${active.activeElapsedSeconds}")
                        _remoteElapsedSeconds.value = active.activeElapsedSeconds
                        ensureRemoteTickRunning(active)
                    } else {
                        stopRemoteTick()
                        _remoteElapsedSeconds.value = 0
                    }
                } catch (e: Exception) {
                    Log.e("RemotePoll", "Polling failed: ${e.message}", e)
                }
                delay(5000)
            }
        }
    }

    private fun stopRemotePolling() {
        remotePollingJob?.cancel()
        remotePollingJob = null
        stopRemoteTick()
        _remoteSession.value = null
        _remoteElapsedSeconds.value = 0
    }

    private fun ensureRemoteTickRunning(session: SessionResponse) {
        if (session.isPaused) {
            stopRemoteTick()
            return
        }
        if (remoteTickJob?.isActive == true) return
        remoteTickJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _remoteElapsedSeconds.value += 1
            }
        }
    }

    private fun stopRemoteTick() {
        remoteTickJob?.cancel()
        remoteTickJob = null
    }

    fun pauseRemoteSession() {
        val session = _remoteSession.value ?: return
        viewModelScope.launch {
            try {
                // Let the API set pausedAt using server time (avoids timezone mismatch)
                val updated = apiService.updateSession(session.id, UpdateSessionRequest(
                    isPaused = true
                ))
                _remoteSession.value = updated
                _remoteElapsedSeconds.value = updated.activeElapsedSeconds
                stopRemoteTick()
            } catch (_: Exception) { }
        }
    }

    fun resumeRemoteSession() {
        val session = _remoteSession.value ?: return
        viewModelScope.launch {
            try {
                // Let the API compute totalPausedSeconds using server time
                val updated = apiService.updateSession(session.id, UpdateSessionRequest(
                    isPaused = false
                ))
                _remoteSession.value = updated
                _remoteElapsedSeconds.value = updated.activeElapsedSeconds
                ensureRemoteTickRunning(updated)
            } catch (_: Exception) { }
        }
    }

    fun stopRemoteSession() {
        val session = _remoteSession.value ?: return
        viewModelScope.launch {
            try {
                apiService.updateSession(session.id, UpdateSessionRequest(
                    endTime = LocalDateTime.now(),
                    isPaused = false
                ))
                _remoteSession.value = null
                _remoteElapsedSeconds.value = 0
                stopRemoteTick()
                fetchTotalPoints()
                fetchStreak()
                fetchRecentSessions()
                refreshEncouragementData()
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRemotePolling()
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
