package org.ttproject.util

expect object LiveActivityController {
    fun updateProgress(percent: Int, message: String, isComplete: Boolean = false)
}