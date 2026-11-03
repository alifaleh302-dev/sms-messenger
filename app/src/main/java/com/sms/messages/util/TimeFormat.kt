package com.sms.messages.util

import java.util.concurrent.TimeUnit

object TimeFormat {

    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
            else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        }
    }

    fun dayLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val zone = java.util.TimeZone.getDefault()
        val day = TimeUnit.MILLISECONDS.toDays(timestamp + zone.getOffset(timestamp))
        val today = TimeUnit.MILLISECONDS.toDays(now + zone.getOffset(now))
        return when (today - day) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        }
    }

    fun clock(timestamp: Long): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))

    fun full(timestamp: Long): String =
        java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
}
