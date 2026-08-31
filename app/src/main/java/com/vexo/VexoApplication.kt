package com.vexo

import android.app.Application
import com.vexo.assistant.AssistantManager

class VexoApplication : Application() {

    val assistantManager: AssistantManager by lazy { AssistantManager() }
}
