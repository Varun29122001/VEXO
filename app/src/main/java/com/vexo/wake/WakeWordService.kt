package com.vexo.wake

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.vexo.MainActivity
import com.vexo.R
import com.vexo.SettingsActivity
import com.vexo.VexoApplication
import com.vexo.models.VexoModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "VexoWakeService"
private const val CHANNEL_ID = "vexo.wake"
private const val NOTIFICATION_ID = 1

/**
 * Ignore repeat wake-ups for this long, so one phrase cannot launch several overlays while the
 * first is still on screen.
 */
private const val COOLDOWN_MILLIS = 3_000L

/**
 * Keeps the microphone open and launches the assistant when a wake phrase is heard.
 *
 * This is the one part of VEXO that is always running, and it exists only because a wake word cannot
 * work any other way. It runs as a `microphone`-typed foreground service so the listening state is
 * visible in the shade rather than hidden, and it is off unless the user turns it on in settings.
 *
 * Audio is consumed in-process by [WakeWordDetector] and discarded; nothing is recorded to disk and
 * nothing is transmitted.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listening: Job? = null
    private var lastDetectionAt = 0L

    private val vexo by lazy { application as VexoApplication }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasMicrophonePermission()) {
            Log.w(TAG, "Started without RECORD_AUDIO; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        if (listening == null) {
            listening = scope.launch { runDetection() }
        }
        // Deliberately not sticky. A native fault in the spotter kills the process, and START_STICKY
        // would respawn it forever, burning battery and filling logs. The user re-enables instead.
        return START_NOT_STICKY
    }

    private suspend fun runDetection() {
        val store = vexo.modelStore
        val model = VexoModels.WakeWord

        if (!store.isInstalled(model)) {
            if (!store.isUnmetered()) {
                Log.i(TAG, "Wake word model missing and the network is metered; stopping")
                stopSelf()
                return
            }
            Log.i(TAG, "Downloading the wake word model")
            try {
                store.install(model)
            } catch (cancellation: CancellationException) {
                // The service is shutting down; not a failure worth reporting or reacting to.
                throw cancellation
            } catch (error: Throwable) {
                Log.w(TAG, "Wake word model download failed", error)
                stopSelf()
                return
            }
        }

        val detector = try {
            WakeWordDetector.create(
                directory = store.dir(model),
                keywordsFile = File(filesDir, "wake-keywords.txt"),
                model = model,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.w(TAG, "Could not open the wake word model", error)
            stopSelf()
            return
        }

        try {
            detector.listen(
                sessionActive = { vexo.assistantManager.sessionActive.value },
                onDetection = { phrase, audio -> onWake(phrase, audio) },
            )
        } finally {
            detector.close()
        }
    }

    private suspend fun onWake(phrase: String, audio: FloatArray) {
        val now = System.currentTimeMillis()
        if (now - lastDetectionAt < COOLDOWN_MILLIS) {
            Log.i(TAG, "Ignoring '$phrase' inside the cooldown window")
            return
        }
        lastDetectionAt = now

        if (vexo.settings.requireEnrolledVoice.value) {
            val verdict = vexo.speakerGate.isEnrolledSpeaker(audio)
            if (verdict == false) {
                Log.i(TAG, "Wake phrase did not match the enrolled voice; ignoring")
                return
            }
        }

        Log.i(TAG, "Launching the assistant for '$phrase'")
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onDestroy() {
        listening = null
        scope.cancel()
        super.onDestroy()
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wake_channel_name),
            // Low: this notification is a disclosure, not an interruption.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.wake_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val settings = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wake_notification_title))
            .setContentText(getString(R.string.wake_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(settings)
            .setOngoing(true)
            .build()
    }

    companion object {

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        /** Mirrors the setting: starts the service when enabled, stops it when not. */
        fun sync(context: Context, enabled: Boolean) {
            if (enabled) start(context) else stop(context)
        }
    }
}
