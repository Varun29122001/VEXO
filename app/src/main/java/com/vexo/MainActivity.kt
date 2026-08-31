package com.vexo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.vexo.actions.ActionResult
import com.vexo.ui.assistant.AssistantSurface
import com.vexo.wake.WakeWordService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "VexoMain"

/**
 * Ceiling on how long the surface waits for speech to finish. Longer than any utterance VEXO can
 * produce (about 2.5 s of audio plus synthesis), short enough to never look stuck.
 */
private const val SPEECH_TIMEOUT_MILLIS = 8_000L

/**
 * The assistant surface. VEXO has no home screen: this activity listens for one request,
 * performs it, answers out loud, then finishes.
 */
class MainActivity : ComponentActivity() {

    private val vexo by lazy { application as VexoApplication }
    private var dismiss by mutableStateOf(false)

    private val requestMicrophone = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSession() else respond("I need microphone access to listen")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Claim the microphone before anything starts recording, so the wake word listener stands
        // down rather than competing with the recogniser for it.
        vexo.assistantManager.setSessionActive(true)

        setContent {
            val audioLevel by vexo.assistantManager.audioLevel.collectAsState()
            AssistantSurface(
                audioLevel = audioLevel,
                dismiss = dismiss,
                onClosed = { finish() },
            )
        }

        if (hasMicrophonePermission()) {
            startSession()
        } else {
            requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clears state and releases the microphone claim.
        vexo.assistantManager.reset()
        reviveWakeWordListener()
    }

    /**
     * The wake service is `START_NOT_STICKY`, so a crash or force-stop leaves it dead until the next
     * reboot. Starting it here means any use of VEXO brings it back. It is safe to call when already
     * running — the service ignores a duplicate start — and the listener stays paused until this
     * session's microphone claim is cleared.
     */
    private fun reviveWakeWordListener() {
        if (!vexo.settings.wakeWordEnabled.value) return
        if (!hasMicrophonePermission()) return
        runCatching { WakeWordService.start(this) }
            .onFailure { Log.w(TAG, "Could not revive the wake word listener", it) }
    }

    private fun startSession() {
        lifecycleScope.launch {
            val result = vexo.assistantSession.run()
            when (result) {
                is ActionResult.Performed -> Unit
                is ActionResult.NotUnderstood -> toast("Heard: \"${result.heard}\"")
                is ActionResult.Failed -> toast(result.diagnostic ?: result.spoken)
            }
            await(vexo.textToSpeech.speak(result.spoken))
            dismiss = true
        }
    }

    private fun respond(message: String) {
        lifecycleScope.launch {
            toast(message)
            await(vexo.textToSpeech.speak(message))
            dismiss = true
        }
    }

    /**
     * Keeps the surface — and therefore a foreground process — alive until VEXO has finished
     * talking, because neural audio is rendered by this process rather than a system service.
     * Capped so a wedged engine cannot leave the overlay on screen indefinitely.
     */
    private suspend fun await(speaking: Job) {
        withTimeoutOrNull(SPEECH_TIMEOUT_MILLIS) { speaking.join() }
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
