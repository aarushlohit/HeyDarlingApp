package com.example.silentoapp

import android.content.Context
import org.json.JSONArray

class ServiceStateStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun update(
        status: String? = null,
        isRunning: Boolean? = null,
        pendingMode: String? = null,
        lastCommand: String? = null,
        lastTranscript: String? = null,
        recognitionMode: String? = null,
        lastCallSource: String? = null,
    ) {
        preferences.edit().apply {
            status?.let { putString(KEY_STATUS, it) }
            isRunning?.let { putBoolean(KEY_IS_RUNNING, it) }
            pendingMode?.let { putString(KEY_PENDING_MODE, it) }
            lastCommand?.let { putString(KEY_LAST_COMMAND, it) }
            lastTranscript?.let { putString(KEY_LAST_TRANSCRIPT, it) }
            recognitionMode?.let { putString(KEY_RECOGNITION_MODE, it) }
            lastCallSource?.let { putString(KEY_LAST_CALL_SOURCE, it) }
        }.apply()
    }

    fun appendLog(message: String) {
        val lines = snapshot().logs.toMutableList()
        lines.add(0, message)
        val boundedLines = lines.take(MAX_LOG_LINES)
        preferences.edit()
            .putString(KEY_LOGS, JSONArray(boundedLines).toString())
            .apply()
    }

    fun snapshot(): ServiceSnapshot {
        val logs = mutableListOf<String>()
        val rawLogs = preferences.getString(KEY_LOGS, "[]").orEmpty()
        try {
            val jsonArray = JSONArray(rawLogs)
            for (index in 0 until jsonArray.length()) {
                logs.add(jsonArray.optString(index))
            }
        } catch (_: Exception) {
            logs.clear()
        }

        return ServiceSnapshot(
            status = preferences.getString(KEY_STATUS, ServiceSnapshot.STATUS_IDLE)
                ?: ServiceSnapshot.STATUS_IDLE,
            isRunning = preferences.getBoolean(KEY_IS_RUNNING, false),
            pendingMode = preferences.getString(KEY_PENDING_MODE, PendingAction.None.storageValue)
                ?: PendingAction.None.storageValue,
            lastCommand = preferences.getString(KEY_LAST_COMMAND, null),
            lastTranscript = preferences.getString(KEY_LAST_TRANSCRIPT, null),
            recognitionMode = preferences.getString(KEY_RECOGNITION_MODE, RecognitionMode.Offline.storageValue)
                ?: RecognitionMode.Offline.storageValue,
            lastCallSource = preferences.getString(KEY_LAST_CALL_SOURCE, null),
            logs = logs,
        )
    }

    companion object {
        private const val PREFS_NAME = "silent_assistant_state"
        private const val KEY_STATUS = "status"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_PENDING_MODE = "pending_mode"
        private const val KEY_LAST_COMMAND = "last_command"
        private const val KEY_LAST_TRANSCRIPT = "last_transcript"
        private const val KEY_RECOGNITION_MODE = "recognition_mode"
        private const val KEY_LAST_CALL_SOURCE = "last_call_source"
        private const val KEY_LOGS = "logs"
        private const val MAX_LOG_LINES = 30
    }
}

data class ServiceSnapshot(
    val status: String,
    val isRunning: Boolean,
    val pendingMode: String,
    val lastCommand: String?,
    val lastTranscript: String?,
    val recognitionMode: String,
    val lastCallSource: String?,
    val logs: List<String>,
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "status" to status,
            "isRunning" to isRunning,
            "pendingMode" to pendingMode,
            "lastCommand" to lastCommand,
            "lastTranscript" to lastTranscript,
            "recognitionMode" to recognitionMode,
            "lastCallSource" to lastCallSource,
            "logs" to logs,
        )
    }

    companion object {
        const val STATUS_IDLE = "idle"
        const val STATUS_STARTING = "starting"
        const val STATUS_LISTENING = "listening"
        const val STATUS_SILENT_TRIGGERED = "silent_triggered"
        const val STATUS_VIBRATE_TRIGGERED = "vibrate_triggered"
        const val STATUS_ERROR = "error"
    }
}
