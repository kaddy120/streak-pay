package com.workpointstracker.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workpointstracker.WorkPointsApplication
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.model.SessionType
import com.workpointstracker.data.model.WishItem
import com.workpointstracker.data.remote.*
import com.workpointstracker.domain.service.TimerService
import com.workpointstracker.util.FormatUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WorkPointsApplication
    private val apiService = app.apiService
    private val sseClient = app.sseClient

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

    // API session ID (same as currentSessionId now - no local Room ID)
    private val _apiSessionId = MutableStateFlow<Long?>(null)

    // Remote session state
    private val _remoteSession = MutableStateFlow<SessionResponse?>(null)
    val remoteSession: StateFlow<SessionResponse?> = _remoteSession
    private val _remoteElapsedSeconds = MutableStateFlow(0L)
    val remoteElapsedSeconds: StateFlow<Long> = _remoteElapsedSeconds
    private var remoteTickJob: Job? = null

    private val _totalPoints = MutableStateFlow<Double?>(0.0)
    val totalPoints: StateFlow<Double?> = _totalPoints

    private val _currentStreak = MutableStateFlow(0)

    private val _recentSessions = MutableStateFlow<List<Session>>(emptyList())
    val recentSessions: StateFlow<List<Session>> = _recentSessions

    private val _userName = MutableStateFlow("Kaddy")

    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting

    val connectionState = sseClient.connectionState

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
                        }
                        is TimerService.TimerState.Running -> {
                            _timerRunning.value = true
                            _timerPaused.value = false
                            _timerElapsedSeconds.value = state.elapsedSeconds
                            syncSessionId()
                            _canStopTimer.value = true
                        }
                        is TimerService.TimerState.Paused -> {
                            _timerRunning.value = false
                            _timerPaused.value = true
                            _timerElapsedSeconds.value = state.elapsedSeconds
                            syncSessionId()
                            _canStopTimer.value = true
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
        recoverSession()
        viewModelScope.launch {
            fetchDashboard()
        }
        subscribeSseEvents()
    }

    private fun syncSessionId() {
        val serviceSessionId = timerService?.getCurrentSessionId()
        if (_currentSessionId.value != null && serviceSessionId == null) {
            timerService?.setCurrentSessionId(_currentSessionId.value!!)
        } else if (serviceSessionId != null) {
            _currentSessionId.value = serviceSessionId
            _apiSessionId.value = serviceSessionId
        }
    }

    private fun recoverSession() {
        val prefs = getApplication<Application>().getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        val savedSessionId = prefs.getLong("active_session_id", -1L)
        if (savedSessionId > 0) {
            viewModelScope.launch {
                try {
                    val session = apiService.getSession(savedSessionId)
                    if (session.endTime == null) {
                        _apiSessionId.value = session.id
                        _currentSessionId.value = session.id
                    } else {
                        // Session already ended, clear prefs
                        prefs.edit().remove("active_session_id").apply()
                    }
                } catch (e: Exception) {
                    prefs.edit().remove("active_session_id").apply()
                }
            }
        }
    }

    fun syncTimerStartTimeFromDatabase() {
        viewModelScope.launch {
            val service = timerService ?: return@launch
            val sessionId = _apiSessionId.value ?: return@launch
            try {
                val session = apiService.getSession(sessionId)
                val serviceStartTime = service.getStartTime() ?: return@launch
                if (session.startTime != serviceStartTime) {
                    service.setStartTime(session.startTime)
                }
                // Sync pause/resume state from server
                if (!session.isPaused && _timerPaused.value) {
                    service.resumeTimer()
                }
            } catch (_: Exception) { }
        }
    }

    private suspend fun fetchDashboard() {
        try {
            val dashboard = apiService.getDashboard()
            _totalPoints.value = dashboard.totalPoints
            _currentStreak.value = dashboard.streak.currentStreak
            _userName.value = dashboard.userName.ifEmpty { "Kaddy" }
            _recentSessions.value = dashboard.recentSessions.map { it.toLocalSession() }

            // Update encouragement data from badges
            val badges = dashboard.badges
            val availableItems = try {
                apiService.getWishItems("available").map { it.toLocalWishItem() }
            } catch (_: Exception) { emptyList() }
            val nextWishItem = findNextAffordableWishItem(availableItems, dashboard.totalPoints)

            _encouragementData.value = EncouragementData(
                streakInfo = dashboard.streak,
                motivationalMessage = badges.motivationalMessage,
                badges = badges.badges,
                highlightedBadges = badges.highlightedBadges,
                nextWishItem = nextWishItem,
                pointsToNextWishItem = nextWishItem?.let {
                    (FormatUtils.priceToPoints(it.price) - dashboard.totalPoints).coerceAtLeast(0.0)
                }
            )

            // Check for remote sessions
            val remoteSessions = dashboard.activeSessions.filter { it.deviceId != "android" }
            val active = remoteSessions.firstOrNull()
            _remoteSession.value = active
            if (active != null) {
                _remoteElapsedSeconds.value = active.activeElapsedSeconds
                ensureRemoteTickRunning(active)
            } else {
                stopRemoteTick()
                _remoteElapsedSeconds.value = 0
            }
        } catch (e: Exception) {
            Log.w("HomeVM", "Dashboard fetch failed", e)
            // Fallback: individual calls
            try { _totalPoints.value = apiService.getTotalPoints().totalPoints } catch (_: Exception) {}
            try { _currentStreak.value = apiService.getStreakInfo().currentStreak } catch (_: Exception) {}
            try { _userName.value = apiService.getSettings().userName.ifEmpty { "Kaddy" } } catch (_: Exception) {}
            try {
                _recentSessions.value = apiService.getSessions()
                    .filter { it.endTime != null }
                    .sortedByDescending { it.startTime }
                    .take(10)
                    .map { it.toLocalSession() }
            } catch (_: Exception) {}
        }
    }

    fun refreshEncouragementData() {
        viewModelScope.launch {
            try {
                val badges = apiService.getBadges()
                val streak = try { apiService.getStreakInfo() } catch (_: Exception) { null }
                val points = totalPoints.value ?: 0.0
                val availableItems = try {
                    apiService.getWishItems("available").map { it.toLocalWishItem() }
                } catch (_: Exception) { emptyList() }
                val nextWishItem = findNextAffordableWishItem(availableItems, points)

                _encouragementData.value = EncouragementData(
                    streakInfo = streak ?: _encouragementData.value.streakInfo,
                    motivationalMessage = badges.motivationalMessage,
                    badges = badges.badges,
                    highlightedBadges = badges.highlightedBadges,
                    nextWishItem = nextWishItem,
                    pointsToNextWishItem = nextWishItem?.let {
                        (FormatUtils.priceToPoints(it.price) - points).coerceAtLeast(0.0)
                    }
                )
            } catch (e: Exception) {
                Log.w("HomeVM", "Badges fetch failed", e)
            }
        }
    }

    private fun findNextAffordableWishItem(items: List<WishItem>, currentPoints: Double): WishItem? {
        return items
            .filter { !it.isRedeemed }
            .minByOrNull { FormatUtils.priceToPoints(it.price) - currentPoints }
    }

    private fun subscribeSseEvents() {
        viewModelScope.launch {
            sseClient.events.collect { event ->
                when (event) {
                    is SseEvent.SessionCreated -> {
                        if (event.session.deviceId != "android") {
                            _remoteSession.value = event.session
                            _remoteElapsedSeconds.value = event.session.activeElapsedSeconds
                            ensureRemoteTickRunning(event.session)
                        }
                    }
                    is SseEvent.SessionUpdated -> {
                        if (event.session.deviceId != "android") {
                            _remoteSession.value = event.session
                            _remoteElapsedSeconds.value = event.session.activeElapsedSeconds
                            if (event.session.endTime != null) {
                                _remoteSession.value = null
                                _remoteElapsedSeconds.value = 0
                                stopRemoteTick()
                            } else {
                                ensureRemoteTickRunning(event.session)
                            }
                        }
                        // Refresh recent sessions on any update
                        refreshRecentSessions()
                    }
                    is SseEvent.SessionDeleted -> {
                        val remote = _remoteSession.value
                        if (remote != null && remote.id == event.id) {
                            _remoteSession.value = null
                            _remoteElapsedSeconds.value = 0
                            stopRemoteTick()
                        }
                        refreshRecentSessions()
                    }
                    is SseEvent.StatsUpdated -> {
                        _totalPoints.value = event.totalPoints
                        _currentStreak.value = event.streak.currentStreak
                        refreshEncouragementData()
                    }
                    is SseEvent.SettingsUpdated -> {
                        try { _userName.value = apiService.getSettings().userName.ifEmpty { "Kaddy" } } catch (_: Exception) {}
                    }
                    is SseEvent.Heartbeat -> { /* keepalive */ }
                    is SseEvent.WishItemCreated, is SseEvent.WishItemUpdated, is SseEvent.WishItemDeleted -> {
                        // Wish list changes may affect encouragement data
                        refreshEncouragementData()
                    }
                }
            }
        }

        // Full refresh on SSE reconnect
        viewModelScope.launch {
            sseClient.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    fetchDashboard()
                }
            }
        }
    }

    private suspend fun refreshRecentSessions() {
        try {
            _recentSessions.value = apiService.getSessions()
                .filter { it.endTime != null }
                .sortedByDescending { it.startTime }
                .take(10)
                .map { it.toLocalSession() }
        } catch (_: Exception) {}
    }

    private fun bindTimerService() {
        val intent = Intent(getApplication(), TimerService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun startTimer() {
        if (_isStarting.value) return
        _isStarting.value = true

        viewModelScope.launch {
            try {
                val startTime = LocalDateTime.now()
                val pointsCalculator = com.workpointstracker.shared.PointsCalculator()
                val sharedType = pointsCalculator.determineSessionType(startTime)

                val apiResponse = apiService.createSession(
                    CreateSessionRequest(deviceId = "android", startTime = startTime, type = sharedType)
                )
                _apiSessionId.value = apiResponse.id
                _currentSessionId.value = apiResponse.id

                // Persist session ID to SharedPreferences
                getApplication<Application>().getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
                    .edit().putLong("active_session_id", apiResponse.id).apply()

                // Start the timer service
                val intent = Intent(getApplication(), TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                }
                getApplication<Application>().startService(intent)
                timerService?.setCurrentSessionId(apiResponse.id)
            } catch (e: Exception) {
                Log.e("HomeVM", "Failed to create session", e)
            } finally {
                _isStarting.value = false
            }
        }
    }

    fun pauseTimer() {
        timerService?.pauseTimer()
        val pausedAt = LocalDateTime.now()
        viewModelScope.launch {
            val apiId = _apiSessionId.value ?: return@launch
            try {
                apiService.updateSession(apiId, UpdateSessionRequest(
                    isPaused = true,
                    pausedAt = pausedAt
                ))
            } catch (_: Exception) { }
        }
    }

    fun resumeTimer() {
        timerService?.resumeTimer()
        viewModelScope.launch {
            val apiId = _apiSessionId.value ?: return@launch
            try {
                apiService.updateSession(apiId, UpdateSessionRequest(
                    isPaused = false
                ))
            } catch (_: Exception) { }
        }
    }

    fun stopTimer() {
        val service = timerService ?: return
        val serviceStartTime = service.getStartTime() ?: return
        val totalPausedSeconds = service.getTotalPausedSeconds()
        service.stopTimer()

        viewModelScope.launch {
            val apiId = _apiSessionId.value
            _apiSessionId.value = null

            // Clear saved session ID
            getApplication<Application>().getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
                .edit().remove("active_session_id").apply()

            if (apiId != null) {
                val now = LocalDateTime.now()
                val actualElapsedSeconds = ChronoUnit.SECONDS.between(serviceStartTime, now) - totalPausedSeconds
                val durationMinutes = actualElapsedSeconds / 60
                try {
                    apiService.updateSession(apiId, UpdateSessionRequest(
                        endTime = now,
                        isPaused = false,
                        durationMinutes = durationMinutes,
                        totalPausedSeconds = totalPausedSeconds
                    ))
                } catch (e: Exception) {
                    Log.w("HomeVM", "API end session failed", e)
                }
            }

            // SSE will trigger stats/session refresh, but also fetch proactively
            fetchDashboard()
            refreshEncouragementData()
        }
    }

    // Remote session control

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
                val updated = apiService.updateSession(session.id, UpdateSessionRequest(isPaused = true))
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
                val updated = apiService.updateSession(session.id, UpdateSessionRequest(isPaused = false))
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
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRemoteTick()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun SessionResponse.toLocalSession() = Session(
        id = id,
        startTime = startTime,
        endTime = endTime,
        durationMinutes = durationMinutes,
        pointsEarned = pointsEarned,
        type = SessionType.valueOf(type.name),
        isPaused = isPaused,
        pausedAt = pausedAt,
        totalPausedSeconds = totalPausedSeconds
    )

    private fun WishItemResponse.toLocalWishItem() = WishItem(
        id = id,
        name = name,
        price = price,
        imageUrl = imageUrl,
        isRedeemed = isRedeemed,
        redeemedDate = redeemedDate?.atStartOfDay()
    )
}

data class HomeUiState(
    val totalPoints: Double = 0.0,
    val currentStreak: Int = 0,
    val userName: String = "User"
)

data class EncouragementData(
    val streakInfo: StreakResponse? = null,
    val motivationalMessage: String = "",
    val badges: List<BadgesResponse.BadgeDto> = emptyList(),
    val highlightedBadges: List<BadgesResponse.BadgeDto> = emptyList(),
    val nextWishItem: WishItem? = null,
    val pointsToNextWishItem: Double? = null
)
