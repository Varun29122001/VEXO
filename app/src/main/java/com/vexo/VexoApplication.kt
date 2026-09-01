package com.vexo

import android.app.Application
import com.vexo.actions.ActionManager
import com.vexo.assistant.AssistantManager
import com.vexo.assistant.AssistantSession
import com.vexo.models.ModelStore
import com.vexo.settings.VexoSettings
import com.vexo.speaker.SpeakerGate
import com.vexo.speaker.VoiceProfileStore
import com.vexo.voice.SpeechRecognitionManager
import com.vexo.voice.TextToSpeechManager

class VexoApplication : Application() {

    val assistantManager: AssistantManager by lazy { AssistantManager() }

    val settings: VexoSettings by lazy { VexoSettings(this) }

    /** Shared by all three downloaded models: the voice, the wake word spotter, the speaker model. */
    val modelStore: ModelStore by lazy { ModelStore(this) }

    val speakerGate: SpeakerGate by lazy {
        SpeakerGate(modelStore = modelStore, profileStore = VoiceProfileStore(this))
    }

    val textToSpeech: TextToSpeechManager by lazy {
        TextToSpeechManager(this, modelStore, settings)
    }

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
