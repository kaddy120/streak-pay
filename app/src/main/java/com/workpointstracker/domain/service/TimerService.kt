package com.workpointstracker.domain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.workpointstracker.MainActivity
import com.workpointstracker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class TimerService : Service() {

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState

    private var timerJob: Job? = null
    private var startTime: LocalDateTime? = null
    private var pausedTime: LocalDateTime? = null
    private var totalPausedSeconds: Long = 0

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
    }

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    fun startTimer() {
        if (_timerState.value !is TimerState.Idle) return

        startTime = LocalDateTime.now()
        totalPausedSeconds = 0
        _timerState.value = TimerState.Running(0)

        startForeground(NOTIFICATION_ID, createNotification("00:00:00"))
        startTimerJob()
    }

    fun pauseTimer() {
        if (_timerState.value !is TimerState.Running) return

        pausedTime = LocalDateTime.now()
        val currentElapsed = getCurrentElapsedSeconds()
        _timerState.value = TimerState.Paused(currentElapsed)

        timerJob?.cancel()
        updateNotification(formatTime(currentElapsed))
    }

    fun resumeTimer() {
        if (_timerState.value !is TimerState.Paused) return

        val pauseStart = pausedTime ?: return
        val pauseDuration = ChronoUnit.SECONDS.between(pauseStart, LocalDateTime.now())
        totalPausedSeconds += pauseDuration

        val currentElapsed = getCurrentElapsedSeconds()
        _timerState.value = TimerState.Running(currentElapsed)
        pausedTime = null

        startTimerJob()
    }

    fun stopTimer(): Long {
        val elapsedSeconds = getCurrentElapsedSeconds()
        _timerState.value = TimerState.Idle

        timerJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        return elapsedSeconds
    }

    private fun startTimerJob() {
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000)
                val elapsed = getCurrentElapsedSeconds()
                _timerState.value = TimerState.Running(elapsed)
                updateNotification(formatTime(elapsed))
            }
        }
    }

    private fun getCurrentElapsedSeconds(): Long {
        val start = startTime ?: return 0
        val totalElapsed = ChronoUnit.SECONDS.between(start, LocalDateTime.now())
        return totalElapsed - totalPausedSeconds
    }

    fun getStartTime(): LocalDateTime? = startTime

    fun getTotalPausedSeconds(): Long = totalPausedSeconds

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.timer_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.timer_notification_channel_description)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(time: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Work Session")
            .setContentText("Time: $time")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(time: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(time))
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    sealed class TimerState {
        object Idle : TimerState()
        data class Running(val elapsedSeconds: Long) : TimerState()
        data class Paused(val elapsedSeconds: Long) : TimerState()
    }
}
