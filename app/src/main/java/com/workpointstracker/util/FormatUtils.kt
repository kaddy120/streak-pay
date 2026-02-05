package com.workpointstracker.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FormatUtils {

    // Currency to points conversion rate
    // 1 point = 9.5 currency units
    const val CURRENCY_TO_POINTS_RATE = 9.5

    fun priceToPoints(price: Double): Double {
        return price / CURRENCY_TO_POINTS_RATE
    }

    fun formatPriceAsPoints(price: Double): String {
        val points = priceToPoints(price)
        return String.format("%.1f pts", points)
    }

    fun formatPrice(price: Double): String {
        return String.format("R%.2f", price)
    }

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
