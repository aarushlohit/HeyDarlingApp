package com.example.silentoapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val methodChannelName = "com.example.silentoapp/service"
    private val eventChannelName = "com.example.silentoapp/service_status"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            methodChannelName,
        ).setMethodCallHandler(::handleMethodCall)

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            eventChannelName,
        ).setStreamHandler(ServiceStatusStreamHandler(applicationContext))
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startService" -> {
                try {
                    val intent = Intent(this, SilentAssistantService::class.java).apply {
                        action = SilentAssistantService.ACTION_START
                    }
                    ContextCompat.startForegroundService(this, intent)
                    result.success(null)
                } catch (error: Exception) {
                    result.error(
                        "start_service_failed",
                        "Failed to start listening service: ${error.message}",
                        null,
                    )
                }
            }

            "stopService" -> {
                try {
                    val intent = Intent(this, SilentAssistantService::class.java).apply {
                        action = SilentAssistantService.ACTION_STOP
                    }
                    startService(intent)
                    result.success(null)
                } catch (error: Exception) {
                    result.error(
                        "stop_service_failed",
                        "Failed to stop listening service: ${error.message}",
                        null,
                    )
                }
            }

            "getServiceSnapshot" -> {
                result.success(ServiceStateStore(this).snapshot().toMap())
            }

            "hasNotificationPolicyAccess" -> {
                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val accessGranted =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                        notificationManager.isNotificationPolicyAccessGranted
                result.success(accessGranted)
            }

            "requestNotificationPolicyAccess" -> {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                result.success(null)
            }

            "hasNotificationListenerAccess" -> {
                val enabled = NotificationCallDetectorService.isEnabled(this)
                result.success(enabled)
            }

            "requestNotificationListenerAccess" -> {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                result.success(null)
            }

            "isIgnoringBatteryOptimizations" -> {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                result.success(powerManager.isIgnoringBatteryOptimizations(packageName))
            }

            "requestIgnoreBatteryOptimizations" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    result.success(null)
                    return
                }

                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                result.success(null)
            }

            "sendLogsToFlutter", "updateState" -> {
                result.success(ServiceStateStore(this).snapshot().toMap())
            }

            else -> result.notImplemented()
        }
    }
}

private class ServiceStatusStreamHandler(
    private val context: Context,
) : EventChannel.StreamHandler {
    private var receiver: BroadcastReceiver? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val snapshot = ServiceStateStore(this@ServiceStatusStreamHandler.context).snapshot()
                    events.success(snapshot.toMap())
                }
            }

        val filter = IntentFilter(SilentAssistantService.ACTION_STATUS_BROADCAST)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        events.success(ServiceStateStore(context).snapshot().toMap())
    }

    override fun onCancel(arguments: Any?) {
        receiver?.let {
            context.unregisterReceiver(it)
        }
        receiver = null
    }
}
