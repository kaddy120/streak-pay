package com.workpointstracker.shared

import com.workpointstracker.shared.models.AppSettings

/**
 * Platform-agnostic interface for accessing app settings.
 * Implemented by Android Room-backed repository and by API-backed client.
 */
interface SettingsProvider {
    suspend fun getAppSettingsOnce(): AppSettings?
    suspend fun saveAppSettings(appSettings: AppSettings)
}
