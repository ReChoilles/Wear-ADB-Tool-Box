package com.fwork.adbtools.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class LogLevel { INFO, OK, WARN, ERROR }

data class LogEntry(val timeMs: Long, val level: LogLevel, val message: String) {
    fun render(): String = String.format(
        "%1\$tH:%1\$tM:%1\$tS  %2\$s",
        timeMs,
        message,
    )
}

object LogBus {
    val entries = mutableStateListOf<LogEntry>()

    private fun push(level: LogLevel, message: String) {
        entries.add(LogEntry(System.currentTimeMillis(), level, message))
        while (entries.size > 500) entries.removeAt(0)
    }

    fun info(msg: String) = push(LogLevel.INFO, msg)
    fun ok(msg: String) = push(LogLevel.OK, msg)
    fun warn(msg: String) = push(LogLevel.WARN, msg)
    fun error(msg: String) = push(LogLevel.ERROR, msg)
}

object Toast {
    var message by mutableStateOf<String?>(null)
    var tick by mutableIntStateOf(0)

    fun show(text: String) {
        message = text
        tick += 1
    }
}
