package org.ttproject.util

actual fun formatMessageTime(isoTimestamp: String, recentPattern: String, olderPattern: String): String {
    return try {
        val date = kotlin.js.Date(isoTimestamp)
        val now = kotlin.js.Date()
        val diffInMs = now.getTime() - date.getTime()
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)

        val timeOptions = kotlin.js.json(
            "hour" to "2-digit",
            "minute" to "2-digit"
        )
        val timeStr = date.toLocaleTimeString(undefined, timeOptions)

        val isToday = date.toDateString() == now.toDateString()
        if (isToday) {
            timeStr
        } else {
            val pattern = if (diffInDays < 7) recentPattern else olderPattern
            val dateOptions = kotlin.js.json()
            if (pattern.contains("EEEE")) dateOptions["weekday"] = "long"
            else if (pattern.contains("EEE")) dateOptions["weekday"] = "short"

            if (pattern.contains("MMMM")) dateOptions["month"] = "long"
            else if (pattern.contains("MMM")) dateOptions["month"] = "short"

            if (pattern.contains("d")) dateOptions["day"] = "numeric"

            val dateStr = date.toLocaleDateString(undefined, dateOptions)
            "$dateStr, $timeStr"
        }
    } catch (e: Throwable) {
        isoTimestamp
    }
}