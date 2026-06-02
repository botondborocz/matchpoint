package org.ttproject.util

actual object LiveActivityController {

    // We will let Swift inject a callback function here!
    var swiftProgressCallback: ((Int, String, Boolean) -> Unit)? = null

    actual fun updateProgress(percent: Int, message: String, isComplete: Boolean) {
        // Send the data over to the Swift side
        swiftProgressCallback?.invoke(percent, message, isComplete)
    }
}