package org.ttproject.util

/**
 * Formats an ISO-8601 timestamp (e.g. "2026-03-28T14:00:00Z") into a local time string.
 * Automatically respects the OS 12h/24h system settings.
 */
expect fun formatMessageTime(isoTimestamp: String, recentPattern: String, olderPattern: String): String