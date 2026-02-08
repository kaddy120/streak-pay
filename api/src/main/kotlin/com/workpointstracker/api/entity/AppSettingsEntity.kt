package com.workpointstracker.api.entity

import com.workpointstracker.shared.models.AppSettings
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "app_settings")
class AppSettingsEntity(
    @Id
    var id: Long = 1,

    @Column(name = "user_name", length = 100)
    var userName: String = "Kaddy",

    @Column(name = "current_streak")
    var currentStreak: Int = 0,

    @Column(name = "last_work_date")
    var lastWorkDate: LocalDate? = null,

    @Column(name = "last_session_end_time")
    var lastSessionEndTime: LocalDateTime? = null,

    @Column(name = "consecutive_work_days")
    var consecutiveWorkDays: Int = 0
) {
    fun toShared(): AppSettings = AppSettings(
        id = id.toInt(),
        userName = userName,
        currentStreak = currentStreak,
        lastWorkDate = lastWorkDate,
        lastSessionEndTime = lastSessionEndTime,
        consecutiveWorkDays = consecutiveWorkDays
    )

    companion object {
        fun fromShared(settings: AppSettings): AppSettingsEntity = AppSettingsEntity(
            id = settings.id.toLong(),
            userName = settings.userName,
            currentStreak = settings.currentStreak,
            lastWorkDate = settings.lastWorkDate,
            lastSessionEndTime = settings.lastSessionEndTime,
            consecutiveWorkDays = settings.consecutiveWorkDays
        )
    }
}
