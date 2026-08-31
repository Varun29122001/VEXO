package com.vexo

import android.app.Application
import com.vexo.actions.ActionManager
import com.vexo.assistant.AssistantManager
import com.vexo.assistant.AssistantSession
import com.vexo.voice.SpeechRecognitionManager

class VexoApplication : Application() {

    val assistantManager: AssistantManager by lazy { AssistantManager() }

    val assistantSession: AssistantSession by lazy {
        AssistantSession(
            speech = SpeechRecognitionManager(this),
            actions = ActionManager(this),
            assistant = assistantManager,
        )
    }
}
