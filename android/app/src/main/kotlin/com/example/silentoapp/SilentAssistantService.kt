package com.example.silentoapp

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService

class SilentAssistantService : Service(), org.vosk.android.RecognitionListener {
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var stateStore: ServiceStateStore
    private lateinit var logger: SilentAssistantLogger
    private lateinit var callStateMonitor: CallStateMonitor
    private lateinit var commandRegistry: CommandRegistry

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var model: Model? = null
    private var fallbackRecognizer: AndroidFallbackRecognizer? = null

    private var pendingAction: PendingAction = PendingAction.None
    private var isStarted = false
    private var lastForegroundUpdateMs = 0L
    private var lastPartialUpdateMs = 0L
    private var lastStableTranscript: String? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val voipReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_VOIP_CALL_BROADCAST) {
                    return
                }
                val source = intent.getStringExtra(EXTRA_CALL_SOURCE).orEmpty()
                val title = intent.getStringExtra(EXTRA_CALL_TITLE).orEmpty()
                val text = intent.getStringExtra(EXTRA_CALL_TEXT).orEmpty()
                handleVoipCallDetected(source, title, text)
            }
        }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        stateStore = ServiceStateStore(this)
        logger = SilentAssistantLogger("SilentAssistant", stateStore)
        callStateMonitor = CallStateMonitor(this, logger, ::handleCallStateChange)
        commandRegistry = CommandRegistry.default()

        createNotificationChannel()
        initializeTts()
        org.vosk.LibVosk.setLogLevel(LogLevel.INFO)

        ContextCompat.registerReceiver(
            this,
            voipReceiver,
            IntentFilter(ACTION_VOIP_CALL_BROADCAST),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopAssistant()
            ACTION_START, null -> startAssistant()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isStarted) {
            scheduleSelfRestart()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(voipReceiver)
        } catch (_: Exception) {
        }

        teardownRecognizer()
        shutdownFallbackRecognizer()
        callStateMonitor.stop()
        shutdownTts()
        worker.shutdownNow()

        updateState(
            status = ServiceSnapshot.STATUS_IDLE,
            isRunning = false,
            pendingAction = PendingAction.None,
        )
        super.onDestroy()
    }

    override fun onPartialResult(hypothesis: String?) {
        processVoskPayload(hypothesis, isPartial = true)
    }

    override fun onResult(hypothesis: String?) {
        processVoskPayload(hypothesis, isPartial = false)
    }

    override fun onFinalResult(hypothesis: String?) {
        processVoskPayload(hypothesis, isPartial = false)
    }

    override fun onError(exception: Exception?) {
        logger.logError("Offline recognition error", exception)
        updateState(status = ServiceSnapshot.STATUS_ERROR)
        restartListeningAfterFailure()
    }

    override fun onTimeout() {
        logger.logWarning("Offline recognizer timeout; attempting restart")
        restartListeningAfterFailure()
    }

    private fun startAssistant() {
        if (isStarted) {
            logger.logInfo("Service already running")
            publishSnapshot()
            return
        }

        if (!hasMicrophonePermission()) {
            logger.logWarning("Cannot start: RECORD_AUDIO permission missing")
            updateState(status = ServiceSnapshot.STATUS_ERROR, isRunning = false)
            return
        }

        isStarted = true
        pendingAction = PendingAction.None
        updateState(
            status = ServiceSnapshot.STATUS_STARTING,
            isRunning = true,
            pendingAction = pendingAction,
        )
        startForeground(NOTIFICATION_ID, buildNotification("Darling 😘 Assistant App running - booting recognizer"))

        worker.execute {
            try {
                callStateMonitor.start()
                initializeRecognizerOfflineFirst()
                updateState(status = ServiceSnapshot.STATUS_LISTENING, isRunning = true)
                updateForegroundNotification("Listening for commands")
                logger.logInfo("Foreground assistant started")
            } catch (error: Exception) {
                logger.logError("Initialization failed", error)
                updateState(status = ServiceSnapshot.STATUS_ERROR, isRunning = false)
                isStarted = false
                stopSelf()
            }
        }
    }

    private fun stopAssistant() {
        logger.logInfo("Stopping foreground listening service")
        isStarted = false
        pendingAction = PendingAction.None

        callStateMonitor.stop()
        teardownRecognizer()
        shutdownFallbackRecognizer()
        updateState(
            status = ServiceSnapshot.STATUS_IDLE,
            isRunning = false,
            pendingAction = pendingAction,
        )

        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    private fun initializeRecognizerOfflineFirst() {
        teardownRecognizer()
        shutdownFallbackRecognizer()

        try {
            initializeOfflineRecognizer()
            logger.logInfo("Speech mode: offline Vosk")
            updateState(recognitionMode = RecognitionMode.Offline.storageValue)
        } catch (error: Exception) {
            logger.logWarning("Offline recognizer unavailable; switching online fallback: ${error.message}")
            initializeOnlineFallbackRecognizer()
            updateState(recognitionMode = RecognitionMode.OnlineFallback.storageValue)
        }
    }

    private fun initializeOfflineRecognizer() {
        val modelDirectory = VoskModelManager.ensureModelAvailable(
            context = this,
            assetPath = MODEL_ASSET_PATH,
            logger = logger,
        )

        model = Model(modelDirectory.absolutePath)
        val loadedModel = model ?: throw IOException("Vosk model failed to load")
        val grammar = commandRegistry.grammarJson()

        recognizer = Recognizer(loadedModel, SAMPLE_RATE, grammar)
        val loadedRecognizer = recognizer ?: throw IOException("Recognizer failed to initialize")

        speechService = SpeechService(loadedRecognizer, SAMPLE_RATE).also { service ->
            service.startListening(this)
        }

        logger.logInfo("Offline recognizer ready with grammar: $grammar")
    }

    private fun initializeOnlineFallbackRecognizer() {
        val recognizer = AndroidFallbackRecognizer(
            context = this,
            logger = logger,
            onPartial = { transcript -> processTranscript(transcript, isPartial = true, source = "online") },
            onFinal = { transcript -> processTranscript(transcript, isPartial = false, source = "online") },
            onError = {
                updateState(status = ServiceSnapshot.STATUS_ERROR)
                restartListeningAfterFailure()
            },
        )
        fallbackRecognizer = recognizer
        recognizer.start()
    }

    private fun processVoskPayload(payload: String?, isPartial: Boolean) {
        if (payload.isNullOrBlank()) {
            return
        }

        val transcript = parseTranscript(payload)
        processTranscript(transcript, isPartial = isPartial, source = "offline")
    }

    private fun processTranscript(transcriptRaw: String, isPartial: Boolean, source: String) {
        val transcript = transcriptRaw.trim().lowercase()
        if (transcript.isBlank()) {
            return
        }

        if (isPartial) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastPartialUpdateMs < PARTIAL_UPDATE_THROTTLE_MS) {
                return
            }
            lastPartialUpdateMs = now
            updateState(lastTranscript = transcript)
            logger.logInfo("Partial($source): $transcript")
            return
        }

        if (transcript == lastStableTranscript) {
            return
        }
        lastStableTranscript = transcript

        logger.logInfo("Recognized($source): $transcript")
        updateState(lastTranscript = transcript)

        val matchedCommand = commandRegistry.findCommand(transcript) ?: return
        pendingAction = matchedCommand.pendingAction

        logger.logInfo("Command detected: ${matchedCommand.keyword} -> ${pendingAction.storageValue}")
        updateState(
            status = ServiceSnapshot.STATUS_LISTENING,
            pendingAction = pendingAction,
            lastCommand = matchedCommand.keyword,
            lastTranscript = transcript,
        )

        updateForegroundNotification("Command armed: ${pendingAction.storageValue}")
        speakAcknowledgement(matchedCommand)
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                logger.logInfo("Incoming GSM call detected")
                applyPendingActionIfAny(source = "gsm")
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (isStarted) {
                    updateState(status = ServiceSnapshot.STATUS_LISTENING)
                    updateForegroundNotification("Listening for commands")
                }
            }
        }
    }

    private fun handleVoipCallDetected(sourcePackage: String, title: String, text: String) {
        logger.logInfo("Incoming VoIP notification from $sourcePackage :: $title $text")
        updateState(lastCallSource = sourcePackage)
        applyPendingActionIfAny(source = sourcePackage)
    }

    private fun applyPendingActionIfAny(source: String) {
        when (pendingAction) {
            PendingAction.None -> {
                logger.logInfo("No pending command for incoming call source: $source")
            }

            PendingAction.Silent -> {
                if (applyRingerMode(AudioManager.RINGER_MODE_SILENT)) {
                    logger.logInfo("Ringer switched to silent for $source")
                    pendingAction = PendingAction.None
                    updateState(
                        status = ServiceSnapshot.STATUS_SILENT_TRIGGERED,
                        pendingAction = pendingAction,
                    )
                    updateForegroundNotification("Silent mode applied to incoming call")
                }
            }

            PendingAction.Vibrate -> {
                if (applyRingerMode(AudioManager.RINGER_MODE_VIBRATE)) {
                    logger.logInfo("Ringer switched to vibrate for $source")
                    pendingAction = PendingAction.None
                    updateState(
                        status = ServiceSnapshot.STATUS_VIBRATE_TRIGGERED,
                        pendingAction = pendingAction,
                    )
                    updateForegroundNotification("Vibrate mode applied to incoming call")
                }
            }
        }
    }

    private fun applyRingerMode(mode: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !notificationManager.isNotificationPolicyAccessGranted
            ) {
                logger.logWarning("Notification policy access missing; cannot change ringer mode")
                false
            } else {
                audioManager.ringerMode = mode
                if (mode == AudioManager.RINGER_MODE_SILENT) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                }
                true
            }
        } catch (error: SecurityException) {
            logger.logError("Security exception while changing ringer mode", error)
            false
        } catch (error: Exception) {
            logger.logError("Failed to change ringer mode", error)
            false
        }
    }

    private fun restartListeningAfterFailure() {
        if (!isStarted) {
            return
        }

        worker.execute {
            SystemClock.sleep(RESTART_DELAY_MS)
            try {
                initializeRecognizerOfflineFirst()
                updateState(status = ServiceSnapshot.STATUS_LISTENING, isRunning = true)
                logger.logInfo("Recognizer pipeline restarted")
            } catch (error: Exception) {
                logger.logError("Recognizer restart failed", error)
                updateState(status = ServiceSnapshot.STATUS_ERROR, isRunning = true)
            }
        }
    }

    private fun parseTranscript(payload: String): String {
        return try {
            val json = JSONObject(payload)
            json.optString("text", json.optString("partial", "")).trim().lowercase()
        } catch (_: Exception) {
            payload.trim().lowercase()
        }
    }

    private fun initializeTts() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                logger.logWarning("TTS initialization failed")
                ttsReady = false
                return@TextToSpeech
            }

            val engine = tts ?: return@TextToSpeech
            val localeResult = engine.setLanguage(Locale.US)
            ttsReady = localeResult >= TextToSpeech.LANG_AVAILABLE
            engine.setPitch(1.18f)
            engine.setSpeechRate(0.96f)

            val preferred = engine.voices
                ?.firstOrNull { voice ->
                    voice.locale?.language == "en" &&
                        !voice.isNetworkConnectionRequired &&
                        voice.name.lowercase().contains("female")
                }

            if (preferred != null) {
                engine.voice = preferred
            }
            logger.logInfo("TTS ready: $ttsReady")
        }
    }

    private fun speakAcknowledgement(command: CommandDefinition) {
        if (!ttsReady) {
            return
        }

        val phrase = when (command.pendingAction) {
            PendingAction.Silent -> "Okay Aarush. Phone silent turned on."
            PendingAction.Vibrate -> "Okay Aarush. Vibrate mode is ready."
            PendingAction.None -> "Okay Aarush."
        }

        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
    }

    private fun shutdownTts() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            ttsReady = false
            tts = null
        }
    }

    private fun teardownRecognizer() {
        try {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            recognizer?.close()
            recognizer = null
            model?.close()
            model = null
        } catch (error: Exception) {
            logger.logError("Recognizer teardown failed", error)
        }
    }

    private fun shutdownFallbackRecognizer() {
        fallbackRecognizer?.stop()
        fallbackRecognizer = null
    }

    private fun updateState(
        status: String? = null,
        isRunning: Boolean? = null,
        pendingAction: PendingAction? = null,
        lastCommand: String? = null,
        lastTranscript: String? = null,
        recognitionMode: String? = null,
        lastCallSource: String? = null,
    ) {
        stateStore.update(
            status = status,
            isRunning = isRunning,
            pendingMode = pendingAction?.storageValue,
            lastCommand = lastCommand,
            lastTranscript = lastTranscript,
            recognitionMode = recognitionMode,
            lastCallSource = lastCallSource,
        )
        publishSnapshot()
    }

    private fun publishSnapshot() {
        sendBroadcast(
            Intent(ACTION_STATUS_BROADCAST).apply {
                `package` = packageName
            },
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Darling 😘 Assistant App",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Background Darling 😘 Assistant App service"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_silent_assistant)
            .setContentTitle("Darling 😘 Assistant App Running")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .addAction(
                0,
                "Stop",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, SilentAssistantService::class.java).apply {
                        action = ACTION_STOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun updateForegroundNotification(contentText: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastForegroundUpdateMs < NOTIFICATION_THROTTLE_MS) {
            return
        }
        lastForegroundUpdateMs = now
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun scheduleSelfRestart() {
        val intent = Intent(this, SilentAssistantService::class.java).apply {
            action = ACTION_START
        }
        val pendingIntent = PendingIntent.getService(
            this,
            37,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + SERVICE_RESTART_DELAY_MS
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_START = "com.example.silentoapp.action.START"
        const val ACTION_STOP = "com.example.silentoapp.action.STOP"
        const val ACTION_STATUS_BROADCAST = "com.example.silentoapp.action.STATUS"
        const val ACTION_VOIP_CALL_BROADCAST = "com.example.silentoapp.action.VOIP_CALL"

        const val EXTRA_CALL_SOURCE = "extra_call_source"
        const val EXTRA_CALL_TITLE = "extra_call_title"
        const val EXTRA_CALL_TEXT = "extra_call_text"

        private const val NOTIFICATION_CHANNEL_ID = "silent_assistant_listener"
        private const val NOTIFICATION_ID = 4107
        private const val SAMPLE_RATE = 16_000.0f
        private const val RESTART_DELAY_MS = 1_400L
        private const val SERVICE_RESTART_DELAY_MS = 2_000L
        private const val NOTIFICATION_THROTTLE_MS = 750L
        private const val PARTIAL_UPDATE_THROTTLE_MS = 900L
        private const val MODEL_ASSET_PATH = "models/vosk-model-small-en-us-0.15"
    }
}

private class AndroidFallbackRecognizer(
    private val context: Context,
    private val logger: SilentAssistantLogger,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: () -> Unit,
) : RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            logger.logWarning("Online fallback SpeechRecognizer is not available")
            onError()
            return
        }

        handler.post {
            if (isRunning) {
                return@post
            }
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    it.setRecognitionListener(this)
                }
                isRunning = true
                startListeningInternal()
            } catch (error: Exception) {
                logger.logError("Failed to start online fallback recognizer", error)
                isRunning = false
                onError()
            }
        }
    }

    fun stop() {
        handler.post {
            isRunning = false
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (error: Exception) {
                logger.logError("Failed stopping fallback recognizer", error)
            } finally {
                speechRecognizer = null
            }
        }
    }

    private fun startListeningInternal() {
        if (!isRunning) {
            return
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        logger.logInfo("Online recognizer ready")
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        if (!isRunning) {
            return
        }
        logger.logWarning("Online recognizer error code: $error")
        handler.postDelayed({ startListeningInternal() }, 700)
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) {
            onFinal(text)
        }
        handler.postDelayed({ startListeningInternal() }, 350)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) {
            onPartial(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}

data class CommandDefinition(
    val keyword: String,
    val pendingAction: PendingAction,
    val aliases: List<String>,
)

enum class PendingAction(val storageValue: String) {
    None("none"),
    Silent("silent"),
    Vibrate("vibrate");

    companion object {
        fun fromStorageValue(value: String?): PendingAction {
            return entries.firstOrNull { it.storageValue == value } ?: None
        }
    }
}

enum class RecognitionMode(val storageValue: String) {
    Offline("offline"),
    OnlineFallback("online_fallback"),
}
