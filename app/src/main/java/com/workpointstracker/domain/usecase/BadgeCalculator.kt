package com.workpointstracker.domain.usecase

import com.workpointstracker.data.model.Session
import com.workpointstracker.data.model.SessionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class Badge(
    val displayName: String,
    val description: String,
    val icon: String,
    val isPermanent: Boolean = false
) {
    EARLY_BIRD(
        "Early Bird",
        "5+ early morning sessions in 7 days",
        "\uD83D\uDC26"  // 🐦
    ),
    NIGHT_OWL(
        "Night Owl",
        "5+ sessions after 8 PM in 7 days",
        "\uD83E\uDD89"  // 🦉
    ),
    WEEKEND_WARRIOR(
        "Weekend Warrior",
        "Worked 4+ consecutive weekends",
        "\u2694\uFE0F"  // ⚔️
    ),
    MARATHON_RUNNER(
        "Marathon Runner",
        "Completed a 3+ hour session",
        "\uD83C\uDFC3"  // 🏃
    ),
    CENTURION(
        "Centurion",
        "100 total sessions completed",
        "\uD83D\uDCAF",  // 💯
        isPermanent = true
    ),
    POINT_MASTER_100(
        "Point Collector",
        "Reached 100 points",
        "\u2B50",  // ⭐
        isPermanent = true
    ),
    POINT_MASTER_500(
        "Point Expert",
        "Reached 500 points",
        "\uD83C\uDF1F",  // 🌟
        isPermanent = true
    ),
    POINT_MASTER_1000(
        "Point Master",
        "Reached 1000 points",
        "\uD83C\uDF1F\uD83C\uDF1F",  // 🌟🌟
        isPermanent = true
    ),
    WEEK_STREAK(
        "Week Streak",
        "7+ day streak",
        "\uD83D\uDD25"  // 🔥
    ),
    MONTH_STREAK(
        "Month Streak",
        "30+ day streak",
        "\uD83D\uDD25\uD83D\uDD25"  // 🔥🔥
    ),
    CONSISTENT(
        "Consistent",
        "Same productive session type 5 days in a row",
        "\uD83D\uDCC5"  // 📅
    ),
    DIVERSIFIED(
        "Diversified",
        "All 3 session types in one day",
        "\uD83C\uDFA8"  // 🎨
    )
}

class BadgeCalculator {

    companion object {
        private val EARLY_MORNING_START = LocalTime.of(5, 0)
        private val EARLY_MORNING_END = LocalTime.of(8, 0)
        private val NIGHT_START = LocalTime.of(20, 0)  // 8 PM
        private const val MARATHON_MINUTES = 180  // 3 hours
    }

    fun calculateEarnedBadges(
        sessions: List<Session>,
        currentStreak: Int,
        totalPoints: Double
    ): List<Badge> {
        val earnedBadges = mutableListOf<Badge>()
        val today = LocalDate.now()
        val sevenDaysAgo = today.minusDays(7)

        // Filter to completed sessions only
        val completedSessions = sessions.filter { it.endTime != null }
        val recentSessions = completedSessions.filter {
            it.startTime.toLocalDate().isAfter(sevenDaysAgo) ||
            it.startTime.toLocalDate().isEqual(sevenDaysAgo)
        }

        // Early Bird: 5+ early morning sessions in last 7 days
        val earlyMorningSessions = recentSessions.count { session ->
            val time = session.startTime.toLocalTime()
            time >= EARLY_MORNING_START && time < EARLY_MORNING_END
        }
        if (earlyMorningSessions >= 5) {
            earnedBadges.add(Badge.EARLY_BIRD)
        }

        // Night Owl: 5+ sessions after 8 PM in last 7 days
        val nightSessions = recentSessions.count { session ->
            session.startTime.toLocalTime() >= NIGHT_START
        }
        if (nightSessions >= 5) {
            earnedBadges.add(Badge.NIGHT_OWL)
        }

        // Weekend Warrior: Worked 4+ consecutive weekends
        if (countConsecutiveWeekends(completedSessions) >= 4) {
            earnedBadges.add(Badge.WEEKEND_WARRIOR)
        }

        // Marathon Runner: 3+ hour session in last 7 days
        val hasMarathonSession = recentSessions.any { it.durationMinutes >= MARATHON_MINUTES }
        if (hasMarathonSession) {
            earnedBadges.add(Badge.MARATHON_RUNNER)
        }

        // Centurion: 100 total sessions (permanent)
        if (completedSessions.size >= 100) {
            earnedBadges.add(Badge.CENTURION)
        }

        // Point Master badges (permanent)
        if (totalPoints >= 1000) {
            earnedBadges.add(Badge.POINT_MASTER_1000)
        } else if (totalPoints >= 500) {
            earnedBadges.add(Badge.POINT_MASTER_500)
        } else if (totalPoints >= 100) {
            earnedBadges.add(Badge.POINT_MASTER_100)
        }

        // Week Streak: Currently on 7+ day streak
        if (currentStreak >= 7) {
            earnedBadges.add(Badge.WEEK_STREAK)
        }

        // Month Streak: Currently on 30+ day streak
        if (currentStreak >= 30) {
            earnedBadges.add(Badge.MONTH_STREAK)
        }

        // Consistent: Same productive session type 5 days in a row
        // (Only early morning or side work counts)
        if (hasConsistentProductiveSessions(completedSessions, 5)) {
            earnedBadges.add(Badge.CONSISTENT)
        }

        // Diversified: All 3 session types in one day (in last 7 days)
        if (hasDiversifiedDay(recentSessions)) {
            earnedBadges.add(Badge.DIVERSIFIED)
        }

        return earnedBadges
    }

    private fun countConsecutiveWeekends(sessions: List<Session>): Int {
        if (sessions.isEmpty()) return 0

        val today = LocalDate.now()
        var consecutiveWeekends = 0
        var checkDate = today

        // Go back week by week and check if there was a weekend session
        for (i in 0 until 8) {  // Check last 8 weeks
            val weekStart = checkDate.minusDays(checkDate.dayOfWeek.value.toLong() - 1)
            val saturday = weekStart.plusDays(5)
            val sunday = weekStart.plusDays(6)

            val hasWeekendSession = sessions.any { session ->
                val sessionDate = session.startTime.toLocalDate()
                sessionDate == saturday || sessionDate == sunday
            }

            if (hasWeekendSession) {
                consecutiveWeekends++
            } else if (i > 0) {  // Don't break on current week if it's not weekend yet
                break
            }

            checkDate = checkDate.minusWeeks(1)
        }

        return consecutiveWeekends
    }

    private fun hasConsistentProductiveSessions(sessions: List<Session>, requiredDays: Int): Boolean {
        if (sessions.isEmpty()) return false

        val today = LocalDate.now()

        // Check for early morning consistency
        var earlyMorningStreak = 0
        for (i in 0 until requiredDays) {
            val checkDate = today.minusDays(i.toLong())
            val hasEarlyMorning = sessions.any { session ->
                session.startTime.toLocalDate() == checkDate &&
                session.type == SessionType.EARLY_MORNING
            }
            if (hasEarlyMorning) {
                earlyMorningStreak++
            } else {
                break
            }
        }
        if (earlyMorningStreak >= requiredDays) return true

        // Check for side work consistency
        var sideWorkStreak = 0
        for (i in 0 until requiredDays) {
            val checkDate = today.minusDays(i.toLong())
            val hasSideWork = sessions.any { session ->
                session.startTime.toLocalDate() == checkDate &&
                session.type == SessionType.SIDE_WORK
            }
            if (hasSideWork) {
                sideWorkStreak++
            } else {
                break
            }
        }
        return sideWorkStreak >= requiredDays
    }

    private fun hasDiversifiedDay(sessions: List<Session>): Boolean {
        // Group sessions by date
        val sessionsByDate = sessions.groupBy { it.startTime.toLocalDate() }

        return sessionsByDate.any { (_, daySessions) ->
            val types = daySessions.map { it.type }.toSet()
            types.containsAll(listOf(SessionType.DAY_JOB, SessionType.SIDE_WORK, SessionType.EARLY_MORNING))
        }
    }

    fun getHighlightedBadges(badges: List<Badge>, maxCount: Int = 3): List<Badge> {
        if (badges.size <= maxCount) return badges

        // Priority: Non-permanent badges first (they're harder to keep), then by rarity
        val priorityOrder = listOf(
            Badge.MONTH_STREAK,      // Hardest to maintain
            Badge.CONSISTENT,        // Requires daily effort
            Badge.WEEK_STREAK,       // Streak badges are important
            Badge.EARLY_BIRD,        // Time-specific
            Badge.NIGHT_OWL,         // Time-specific
            Badge.MARATHON_RUNNER,   // Recent achievement
            Badge.WEEKEND_WARRIOR,   // Requires consistency
            Badge.DIVERSIFIED,       // Variety achievement
            Badge.POINT_MASTER_1000, // Milestone
            Badge.POINT_MASTER_500,
            Badge.POINT_MASTER_100,
            Badge.CENTURION
        )

        return badges.sortedBy { priorityOrder.indexOf(it) }.take(maxCount)
    }
}
