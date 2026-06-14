package org.ttproject.components

object PlatformNotificationManager {
    private var listener: ((message: String, type: String) -> Unit)? = null

    fun setListener(callback: (String, String) -> Unit) {
        this.listener = callback
    }

    fun showNotification(message: String, type: String) {
        listener?.invoke(message, type)
    }
}
