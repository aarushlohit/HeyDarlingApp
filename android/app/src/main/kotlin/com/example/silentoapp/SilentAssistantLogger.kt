package com.example.silentoapp

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SilentAssistantLogger(
    private val tag: String,
    private val stateStore: ServiceStateStore,
) {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(message: String) {
        val stampedMessage = "[${formatter.format(Date())}] [INFO] $message"
        Log.i(tag, stampedMessage)
        stateStore.appendLog(stampedMessage)
    }

    fun logInfo(message: String) {
        log(message)
    }

    fun logWarning(message: String) {
        val stampedMessage = "[${formatter.format(Date())}] [WARN] $message"
        Log.w(tag, stampedMessage)
        stateStore.appendLog(stampedMessage)
    }

    fun logError(message: String, throwable: Throwable? = null) {
        val detail = throwable?.message?.let { "$message: $it" } ?: message
        val stampedMessage = "[${formatter.format(Date())}] [ERROR] $detail"
        Log.e(tag, stampedMessage, throwable)
        stateStore.appendLog(stampedMessage)
    }
}
