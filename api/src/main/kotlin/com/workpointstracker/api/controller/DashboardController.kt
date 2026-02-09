package com.workpointstracker.api.controller

import com.workpointstracker.api.dto.*
import com.workpointstracker.api.entity.DailyGoalEntity
import com.workpointstracker.api.repository.DailyGoalRepository
import com.workpointstracker.api.repository.SessionRepository
import com.workpointstracker.api.service.SessionService
import com.workpointstracker.api.service.StreakService
import com.workpointstracker.shared.BadgeCalculator
import com.workpointstracker.shared.StreakManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class DashboardController(
    private val sessionService: SessionService,
    private val sessionRepository: SessionRepository,
    private val streakService: StreakService,
    private val dailyGoalRepository: DailyGoalRepository
) {
    private val badgeCalculator = BadgeCalculator()

    @GetMapping("/dashboard")
    fun getDashboard(): ResponseEntity<DashboardResponse> {
        val totalPoints = sessionRepository.getTotalPoints()
        val streakResponse = streakService.getStreakInfo()
        val settings = streakService.getSettings()
        val recentSessions = sessionService.getSessions(null, null)
        val activeSessions = sessionService.getAllActiveSessions()
        val goal = dailyGoalRepository.findById(1L).orElse(DailyGoalEntity())

        val completedSessions = sessionRepository.findAllCompleted().map { it.toShared() }
        val currentStreak = streakService.getCurrentStreak()
        val streakInfo = streakService.getStreakInfoShared()
        val badges = badgeCalculator.calculateEarnedBadges(completedSessions, currentStreak, totalPoints)
        val highlightedBadges = badgeCalculator.getHighlightedBadges(badges)
        val motivationalMessage = streakService.streakManager.getMotivationalMessage(streakInfo, badges)

        val badgesResponse = BadgesResponse(
            badges = badges.map { BadgesResponse.BadgeDto(it.name, it.displayName, it.description, it.icon, it.isPermanent) },
            highlightedBadges = highlightedBadges.map { BadgesResponse.BadgeDto(it.name, it.displayName, it.description, it.icon, it.isPermanent) },
            motivationalMessage = motivationalMessage
        )

        return ResponseEntity.ok(
            DashboardResponse(
                totalPoints = totalPoints,
                streak = streakResponse,
                userName = settings.userName,
                recentSessions = recentSessions,
                badges = badgesResponse,
                activeSessions = activeSessions,
                goals = DailyGoalResponse(dayJobHours = goal.dayJobHours, sideWorkHours = goal.sideWorkHours)
            )
        )
    }
}
