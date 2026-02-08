package com.workpointstracker.api.entity

import com.workpointstracker.shared.models.DailyGoal
import jakarta.persistence.*

@Entity
@Table(name = "daily_goals")
class DailyGoalEntity(
    @Id
    var id: Long = 1,

    @Column(name = "day_job_hours")
    var dayJobHours: Double = 7.5,

    @Column(name = "side_work_hours")
    var sideWorkHours: Double = 4.0
) {
    fun toShared(): DailyGoal = DailyGoal(
        id = id.toInt(),
        dayJobHours = dayJobHours,
        sideWorkHours = sideWorkHours
    )

    companion object {
        fun fromShared(goal: DailyGoal): DailyGoalEntity = DailyGoalEntity(
            id = goal.id.toLong(),
            dayJobHours = goal.dayJobHours,
            sideWorkHours = goal.sideWorkHours
        )
    }
}
