package com.perdonus.ruclaw.android.core.util

import android.util.Log
import java.time.Instant

object AppDiagnostics {
    private const val maxEntries = 160
    private const val tag = "RuClawAndroid"
    private val entries = ArrayDeque<String>()

    @Synchronized
    fun log(message: String) {
        val entry = "[${Instant.now()}] $message"
        Log.d(tag, message)
        while (entries.size >= maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()
}
