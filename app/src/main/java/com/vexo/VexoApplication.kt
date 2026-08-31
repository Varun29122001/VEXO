package com.vexo

import android.app.Application
import com.vexo.actions.ActionManager
import com.vexo.assistant.AssistantManager
import com.vexo.assistant.AssistantSession
import com.vexo.voice.SpeechRecognitionManager
import com.vexo.voice.TextToSpeechManager

class VexoApplication : Application() {

    val assistantManager: AssistantManager by lazy { AssistantManager() }

    val textToSpeech: TextToSpeechManager by lazy { TextToSpeechManager(this) }

    val assistantSession: AssistantSession by lazy {
        AssistantSession(
            speech = SpeechRecognitionManager(this),
            actions = ActionManager(this),
            assistant = assistantManager,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // The engine takes a moment to bind; start it while the user is still speaking.
        textToSpeech
    }
}
