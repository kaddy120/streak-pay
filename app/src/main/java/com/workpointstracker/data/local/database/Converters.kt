package com.workpointstracker.data.local.database

import androidx.room.TypeConverter
import com.workpointstracker.data.model.SessionType
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it) }
    }

    @TypeConverter
    fun dateTimeToTimestamp(dateTime: LocalDateTime?): String? {
        return dateTime?.toString()
    }

    @TypeConverter
    fun fromDateString(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    @TypeConverter
    fun dateToString(date: LocalDate?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun fromSessionType(value: SessionType): String {
        return value.name
    }

    @TypeConverter
    fun toSessionType(value: String): SessionType {
        return SessionType.valueOf(value)
    }
}
