package com.example.silentoapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCallDetectorService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val notification = sbn.notification ?: return
        val packageName = sbn.packageName.orEmpty()
        val extras = notification.extras
        val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras?.getCharSequence("android.text")?.toString().orEmpty()

        if (!isCallLikeNotification(packageName, notification.category, title, text)) {
            return
        }

        sendBroadcast(
            Intent(SilentAssistantService.ACTION_VOIP_CALL_BROADCAST).apply {
                `package` = packageName()
                putExtra(SilentAssistantService.EXTRA_CALL_SOURCE, packageName)
                putExtra(SilentAssistantService.EXTRA_CALL_TITLE, title)
                putExtra(SilentAssistantService.EXTRA_CALL_TEXT, text)
            },
        )
    }

    private fun packageName(): String = applicationContext.packageName

    private fun isCallLikeNotification(
        packageName: String,
        category: String?,
        title: String,
        text: String,
    ): Boolean {
        if (category == android.app.Notification.CATEGORY_CALL) {
            return true
        }

        val normalized = "$title $text".lowercase()
        val knownVoipPackage = packageName in knownVoipPackages
        val looksLikeCall = callPatterns.any { pattern -> normalized.contains(pattern) }
        return knownVoipPackage && looksLikeCall
    }

    companion object {
        private val knownVoipPackages = setOf(
            "com.whatsapp",
            "com.google.android.dialer",
            "com.google.android.apps.tachyon",
            "com.facebook.orca",
            "org.telegram.messenger",
            "com.skype.raider",
        )

        private val callPatterns = setOf(
            "incoming call",
            "calling",
            "voice call",
            "video call",
            "ringing",
            "call",
        )

        fun isEnabled(context: Context): Boolean {
            val flattened = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val expected = ComponentName(context, NotificationCallDetectorService::class.java)
                .flattenToString()
            return flattened.contains(expected)
        }
    }
}
