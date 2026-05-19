package com.workpointstracker.api.repository

import com.workpointstracker.api.entity.AppSettingsEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AppSettingsRepository : JpaRepository<AppSettingsEntity, Long>
