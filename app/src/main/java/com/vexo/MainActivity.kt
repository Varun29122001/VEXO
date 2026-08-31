package com.vexo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.launch

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
        vexo.assistantManager.reset()
    }

    private fun startSession() {
        lifecycleScope.launch {
            val result = vexo.assistantSession.run()
            vexo.textToSpeech.speak(result.spoken)
            when (result) {
                is ActionResult.Performed -> Unit
                is ActionResult.NotUnderstood -> toast("Heard: \"${result.heard}\"")
                is ActionResult.Failed -> toast(result.diagnostic ?: result.spoken)
            }
            dismiss = true
        }
    }

    private fun respond(message: String) {
        vexo.textToSpeech.speak(message)
        toast(message)
        dismiss = true
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
