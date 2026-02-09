package com.workpointstracker

import android.app.Application
import com.workpointstracker.data.remote.ApiClient
import com.workpointstracker.data.remote.ApiService
import com.workpointstracker.data.remote.SseClient

class WorkPointsApplication : Application() {

    val apiService: ApiService by lazy {
        ApiClient.getService(BuildConfig.API_BASE_URL, BuildConfig.API_KEY)
    }

    val sseClient: SseClient by lazy {
        SseClient(BuildConfig.API_BASE_URL, BuildConfig.API_KEY)
    }

    override fun onCreate() {
        super.onCreate()
        sseClient.connect()
    }
}
