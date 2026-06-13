package org.ttproject.util

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSISO8601DateFormatWithInternetDateTime
import platform.Foundation.NSISO8601DateFormatWithFractionalSeconds
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

actual fun formatMessageTime(isoTimestamp: String, recentPattern: String, olderPattern: String): String {
    return try {
        val isoFormatter = NSISO8601DateFormatter()

        // 1. Try parsing standard ISO8601 (e.g., 2026-03-29T14:00:00Z)
        isoFormatter.formatOptions = NSISO8601DateFormatWithInternetDateTime
        var date = isoFormatter.dateFromString(isoTimestamp)

        // 2. If it failed, try again WITH fractional seconds (e.g., 2026-03-29T14:00:00.123Z)
        if (date == null) {
            isoFormatter.formatOptions = NSISO8601DateFormatWithInternetDateTime or NSISO8601DateFormatWithFractionalSeconds
            date = isoFormatter.dateFromString(isoTimestamp)
        }

        // 3. If it STILL failed (bad data), just return the raw string
        if (date == null) return isoTimestamp

        val now = platform.Foundation.NSDate()
        val timeInterval = now.timeIntervalSinceDate(date)
        val oneWeekInSeconds = 7.0 * 24.0 * 60.0 * 60.0

        val timeFormatter = NSDateFormatter().apply {
            locale = NSLocale.currentLocale()
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterShortStyle
        }
        val timeStr = timeFormatter.stringFromDate(date)

        val isToday = platform.Foundation.NSCalendar.currentCalendar.isDateInToday(date)
        if (isToday) {
            timeStr
        } else {
            val dateFormatter = NSDateFormatter().apply {
                locale = NSLocale.currentLocale()
                dateFormat = if (timeInterval < oneWeekInSeconds) recentPattern else olderPattern
            }
            val dateStr = dateFormatter.stringFromDate(date)
            "$dateStr, $timeStr"
        }
    } catch (e: Throwable) {
        isoTimestamp
    }
}