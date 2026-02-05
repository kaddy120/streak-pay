package com.workpointstracker.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.workpointstracker.data.model.AppSettings
import com.workpointstracker.data.model.DailyGoal
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.model.WishItem

@Database(
    entities = [Session::class, WishItem::class, DailyGoal::class, AppSettings::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WorkPointsDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun wishItemDao(): WishItemDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: WorkPointsDatabase? = null

        fun getDatabase(context: Context): WorkPointsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkPointsDatabase::class.java,
                    "work_points_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
