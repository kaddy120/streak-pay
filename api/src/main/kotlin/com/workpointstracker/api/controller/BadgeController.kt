package com.workpointstracker.api.controller

import com.workpointstracker.api.dto.BadgesResponse
import com.workpointstracker.api.repository.SessionRepository
import com.workpointstracker.api.service.StreakService
import com.workpointstracker.shared.Badge
import com.workpointstracker.shared.BadgeCalculator
import com.workpointstracker.shared.StreakManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class BadgeController(
    private val sessionRepository: SessionRepository,
    private val streakService: StreakService
) {
    private val badgeCalculator = BadgeCalculator()
    private val streakManager get() = streakService.streakManager

    @GetMapping("/badges")
    fun getBadges(): ResponseEntity<BadgesResponse> {
        val completedSessions = sessionRepository.findAllCompleted().map { it.toShared() }
        val totalPoints = sessionRepository.getTotalPoints()
        val currentStreak = streakService.getCurrentStreak()
        val streakInfo = streakService.getStreakInfoShared()

        val badges = badgeCalculator.calculateEarnedBadges(completedSessions, currentStreak, totalPoints)
        val highlightedBadges = badgeCalculator.getHighlightedBadges(badges)
        val motivationalMessage = streakManager.getMotivationalMessage(streakInfo, badges)

        return ResponseEntity.ok(
            BadgesResponse(
                badges = badges.map { it.toBadgeDto() },
                highlightedBadges = highlightedBadges.map { it.toBadgeDto() },
                motivationalMessage = motivationalMessage
            )
        )
    }

    private fun Badge.toBadgeDto() = BadgesResponse.BadgeDto(
        name = name,
        displayName = displayName,
        description = description,
        icon = icon,
        isPermanent = isPermanent
    )
}
