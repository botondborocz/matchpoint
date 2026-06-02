package org.ttproject

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

fun isIosPlatform(): Boolean = getPlatform().name.contains("iOS", ignoreCase = true)