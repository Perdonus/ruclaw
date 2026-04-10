package com.perdonus.ruclaw.android.core.util

import java.time.Instant

object AppDiagnostics {
    private const val maxEntries = 160
    private val entries = ArrayDeque<String>()

    @Synchronized
    fun log(message: String) {
        val entry = "[${Instant.now()}] $message"
        while (entries.size >= maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()
}
