package com.vexo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vexo.assistant.AssistantState
import com.vexo.ui.assistant.AssistantSurface

/**
 * Hosts the assistant surface in a translucent window. VEXO has no home screen: this activity
 * shows the bottom orb, then finishes as soon as the surface is dismissed.
 */
class MainActivity : ComponentActivity() {

    private val assistant by lazy { (application as VexoApplication).assistantManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        assistant.transitionTo(AssistantState.Listening)

        setContent {
            val audioLevel by assistant.audioLevel.collectAsState()
            AssistantSurface(
                audioLevel = audioLevel,
                onClosed = { finish() },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        assistant.reset()
    }
}
