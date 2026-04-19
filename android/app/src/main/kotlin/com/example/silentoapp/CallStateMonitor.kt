package com.example.silentoapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallStateMonitor(
    context: Context,
    private val logger: SilentAssistantLogger,
    private val onStateChanged: (Int) -> Unit,
) {
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val appContext = context.applicationContext

    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var isRegistered = false

    fun start() {
        if (isRegistered) {
            return
        }

        if (!hasPermission()) {
            logger.log("READ_PHONE_STATE permission missing; call monitoring disabled")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback =
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            logger.log("Call state changed: $state")
                            onStateChanged(state)
                        }
                    }

                telephonyManager.registerTelephonyCallback(
                    appContext.mainExecutor,
                    callback,
                )
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener =
                    object : PhoneStateListener() {
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            logger.log("Call state changed: $state")
                            onStateChanged(state)
                        }
                    }

                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                phoneStateListener = listener
            }
            isRegistered = true
        } catch (error: Exception) {
            logger.logError("Failed to register call state monitor", error)
        }
    }

    fun stop() {
        if (!isRegistered) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
                telephonyCallback = null
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                phoneStateListener = null
            }
        } catch (error: Exception) {
            logger.logError("Failed to unregister call state monitor", error)
        }
        isRegistered = false
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
