package com.workpointstracker.api.repository

import com.workpointstracker.api.entity.DailyGoalEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DailyGoalRepository : JpaRepository<DailyGoalEntity, Long>
