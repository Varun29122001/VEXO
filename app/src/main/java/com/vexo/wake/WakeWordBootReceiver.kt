package com.vexo.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vexo.settings.VexoSettings

private const val TAG = "VexoBoot"

/**
 * Brings the wake word listener back after a reboot, but only if the user had switched it on.
 *
 * Without this a wake word silently stops working the first time the phone restarts, which is the
 * kind of failure people never report and simply stop trusting.
 */
class WakeWordBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val enabled = VexoSettings(context).wakeWordEnabled.value
        Log.i(TAG, "Boot completed; wake word enabled=$enabled")
        if (enabled) WakeWordService.start(context)
    }
}
