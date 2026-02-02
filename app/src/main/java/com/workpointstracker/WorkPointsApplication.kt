package com.workpointstracker

import android.app.Application
import com.workpointstracker.data.local.database.WorkPointsDatabase

class WorkPointsApplication : Application() {

    val database: WorkPointsDatabase by lazy {
        WorkPointsDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
