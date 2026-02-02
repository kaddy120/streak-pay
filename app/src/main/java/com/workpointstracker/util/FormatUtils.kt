package com.workpointstracker.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FormatUtils {

    fun formatElapsedTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            String.format("%dh %dm", hours, mins)
        } else {
            String.format("%dm", mins)
        }
    }

    fun formatPoints(points: Double): String {
        return String.format("%.2f", points)
    }

    fun formatDateTime(dateTime: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        return dateTime.format(formatter)
    }

    fun formatDate(dateTime: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        return dateTime.format(formatter)
    }

    fun formatTime(dateTime: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return dateTime.format(formatter)
    }
}
