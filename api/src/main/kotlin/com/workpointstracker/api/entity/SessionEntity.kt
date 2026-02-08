package com.workpointstracker.api.entity

import com.workpointstracker.shared.models.Session
import com.workpointstracker.shared.models.SessionType
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "sessions")
class SessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "device_id", nullable = false, length = 50)
    var deviceId: String = "android",

    @Column(name = "start_time", nullable = false)
    var startTime: LocalDateTime = LocalDateTime.now(),

    @Column(name = "end_time")
    var endTime: LocalDateTime? = null,

    @Column(name = "duration_minutes")
    var durationMinutes: Long = 0,

    @Column(name = "points_earned")
    var pointsEarned: Double = 0.0,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var type: SessionType = SessionType.DAY_JOB,

    @Column(name = "is_paused")
    var isPaused: Boolean = false,

    @Column(name = "paused_at")
    var pausedAt: LocalDateTime? = null,

    @Column(name = "total_paused_seconds")
    var totalPausedSeconds: Long = 0,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toShared(): Session = Session(
        id = id,
        deviceId = deviceId,
        startTime = startTime,
        endTime = endTime,
        durationMinutes = durationMinutes,
        pointsEarned = pointsEarned,
        type = type,
        isPaused = isPaused,
        pausedAt = pausedAt,
        totalPausedSeconds = totalPausedSeconds
    )

    companion object {
        fun fromShared(session: Session): SessionEntity = SessionEntity(
            id = session.id,
            deviceId = session.deviceId,
            startTime = session.startTime,
            endTime = session.endTime,
            durationMinutes = session.durationMinutes,
            pointsEarned = session.pointsEarned,
            type = session.type,
            isPaused = session.isPaused,
            pausedAt = session.pausedAt,
            totalPausedSeconds = session.totalPausedSeconds
        )
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
