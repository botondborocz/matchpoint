package org.ttproject.util

actual fun formatMessageTime(isoTimestamp: String, recentPattern: String, olderPattern: String): String {
    return try {
        val instant = java.time.Instant.parse(isoTimestamp)
        val localTime = instant.atZone(java.time.ZoneId.systemDefault())
        val now = java.time.Instant.now().atZone(java.time.ZoneId.systemDefault())
        val diffInMs = java.time.Instant.now().toEpochMilli() - instant.toEpochMilli()
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)

        val isToday = localTime.year == now.year &&
                      localTime.monthValue == now.monthValue &&
                      localTime.dayOfMonth == now.dayOfMonth

        val timeStr = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(localTime)

        if (isToday) {
            timeStr
        } else if (diffInDays < 7) {
            val dateStr = java.time.format.DateTimeFormatter.ofPattern(recentPattern).format(localTime)
            "$dateStr, $timeStr"
        } else {
            val dateStr = java.time.format.DateTimeFormatter.ofPattern(olderPattern).format(localTime)
            "$dateStr, $timeStr"
        }
    } catch (e: Throwable) {
        isoTimestamp
    }
}