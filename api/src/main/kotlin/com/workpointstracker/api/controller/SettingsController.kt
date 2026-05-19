package com.workpointstracker.api.controller

import com.workpointstracker.api.dto.*
import com.workpointstracker.api.entity.DailyGoalEntity
import com.workpointstracker.api.repository.DailyGoalRepository
import com.workpointstracker.api.service.StreakService
import com.workpointstracker.api.sse.SseConnectionManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class SettingsController(
    private val streakService: StreakService,
    private val dailyGoalRepository: DailyGoalRepository,
    private val sseConnectionManager: SseConnectionManager
) {
    @GetMapping("/settings")
    fun getSettings(): ResponseEntity<AppSettingsResponse> {
        val settings = streakService.getSettings()
        return ResponseEntity.ok(
            AppSettingsResponse(
                userName = settings.userName,
                currentStreak = settings.currentStreak,
                lastWorkDate = settings.lastWorkDate,
                lastSessionEndTime = settings.lastSessionEndTime,
                consecutiveWorkDays = settings.consecutiveWorkDays
            )
        )
    }

    @PutMapping("/settings")
    fun updateSettings(@RequestBody request: UpdateSettingsRequest): ResponseEntity<AppSettingsResponse> {
        val current = streakService.getSettings()
        val updated = current.copy(
            userName = request.userName ?: current.userName
        )
        streakService.updateSettings(updated)
        val settingsResponse = AppSettingsResponse(
            userName = updated.userName,
            currentStreak = updated.currentStreak,
            lastWorkDate = updated.lastWorkDate,
            lastSessionEndTime = updated.lastSessionEndTime,
            consecutiveWorkDays = updated.consecutiveWorkDays
        )
        val goal = dailyGoalRepository.findById(1L).orElse(DailyGoalEntity())
        sseConnectionManager.broadcast("settings.updated", mapOf(
            "settings" to settingsResponse,
            "goals" to DailyGoalResponse(dayJobHours = goal.dayJobHours, sideWorkHours = goal.sideWorkHours)
        ))
        return ResponseEntity.ok(settingsResponse)
    }

    @GetMapping("/stats/streak")
    fun getStreakInfo(): ResponseEntity<StreakResponse> {
        return ResponseEntity.ok(streakService.getStreakInfo())
    }

    @GetMapping("/goals")
    fun getGoals(): ResponseEntity<DailyGoalResponse> {
        val goal = dailyGoalRepository.findById(1L).orElse(DailyGoalEntity())
        return ResponseEntity.ok(
            DailyGoalResponse(
                dayJobHours = goal.dayJobHours,
                sideWorkHours = goal.sideWorkHours
            )
        )
    }

    @PutMapping("/goals")
    fun updateGoals(@RequestBody request: UpdateGoalRequest): ResponseEntity<DailyGoalResponse> {
        val current = dailyGoalRepository.findById(1L).orElse(DailyGoalEntity())
        request.dayJobHours?.let { current.dayJobHours = it }
        request.sideWorkHours?.let { current.sideWorkHours = it }
        val saved = dailyGoalRepository.save(current)
        val goalResponse = DailyGoalResponse(
            dayJobHours = saved.dayJobHours,
            sideWorkHours = saved.sideWorkHours
        )
        val settings = streakService.getSettings()
        sseConnectionManager.broadcast("settings.updated", mapOf(
            "settings" to AppSettingsResponse(
                userName = settings.userName,
                currentStreak = settings.currentStreak,
                lastWorkDate = settings.lastWorkDate,
                lastSessionEndTime = settings.lastSessionEndTime,
                consecutiveWorkDays = settings.consecutiveWorkDays
            ),
            "goals" to goalResponse
        ))
        return ResponseEntity.ok(goalResponse)
    }
}
