package org.ttproject.util

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RequiresApi(Build.VERSION_CODES.O)
actual fun formatMessageTime(isoTimestamp: String, recentPattern: String, olderPattern: String): String {
    return try {
        // Parse the UTC database string
        val instant = Instant.parse(isoTimestamp)

        // Convert to the user's local timezone
        val localTime = instant.atZone(ZoneId.systemDefault())

        val now = Instant.now().atZone(ZoneId.systemDefault())
        val diffInMs = Instant.now().toEpochMilli() - instant.toEpochMilli()
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)

        val isToday = localTime.year == now.year &&
                      localTime.monthValue == now.monthValue &&
                      localTime.dayOfMonth == now.dayOfMonth

        val timeStr = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(localTime)

        if (isToday) {
            timeStr
        } else if (diffInDays < 7) {
            val dateStr = DateTimeFormatter.ofPattern(recentPattern).format(localTime)
            "$dateStr, $timeStr"
        } else {
            val dateStr = DateTimeFormatter.ofPattern(olderPattern).format(localTime)
            "$dateStr, $timeStr"
        }
    } catch (e: Throwable) {
        // Fallback to the raw string if the database sends a weird format or platform fails
        isoTimestamp
    }
}