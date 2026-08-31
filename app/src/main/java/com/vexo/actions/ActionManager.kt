package com.vexo.actions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Outcome of one request. [spoken] is what VEXO says out loud; [diagnostic] carries extra detail
 * that is useful on screen but not worth speaking.
 */
sealed interface ActionResult {
    val spoken: String

    data class Performed(override val spoken: String) : ActionResult

    data class NotUnderstood(override val spoken: String, val heard: String) : ActionResult

    data class Failed(override val spoken: String, val diagnostic: String? = null) : ActionResult
}

class ActionManager(private val context: Context) {

    fun execute(command: Command, heard: String): ActionResult = when (command) {
        is Command.OpenSettings -> openSettings(command.panel)
        is Command.OpenApp -> openApp(command.query)
        Command.Unknown -> ActionResult.NotUnderstood("I can't do that yet", heard)
    }

    private fun openSettings(panel: SettingsPanel): ActionResult =
        start(Intent(actionFor(panel)), "Opening ${label(panel)}")

    private fun actionFor(panel: SettingsPanel): String = when (panel) {
        SettingsPanel.Root -> Settings.ACTION_SETTINGS
        SettingsPanel.Wifi -> Settings.ACTION_WIFI_SETTINGS
        SettingsPanel.Bluetooth -> Settings.ACTION_BLUETOOTH_SETTINGS
        SettingsPanel.Display -> Settings.ACTION_DISPLAY_SETTINGS
        SettingsPanel.Sound -> Settings.ACTION_SOUND_SETTINGS
        SettingsPanel.Battery -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        SettingsPanel.Location -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        SettingsPanel.Apps -> Settings.ACTION_APPLICATION_SETTINGS
    }

    private fun label(panel: SettingsPanel): String = when (panel) {
        SettingsPanel.Root -> "settings"
        SettingsPanel.Wifi -> "Wi-Fi settings"
        SettingsPanel.Bluetooth -> "Bluetooth settings"
        SettingsPanel.Display -> "display settings"
        SettingsPanel.Sound -> "sound settings"
        SettingsPanel.Battery -> "battery settings"
        SettingsPanel.Location -> "location settings"
        SettingsPanel.Apps -> "app settings"
    }

    private fun openApp(query: String): ActionResult {
        val apps = launcherActivities()
        val match = apps.firstOrNull { it.first.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.first.contains(query, ignoreCase = true) }
            ?: return ActionResult.Failed("I couldn't find an app called $query")

        return start(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(match.second),
            "Opening ${match.first}",
        )
    }

    private fun launcherActivities(): List<Pair<String, ComponentName>> {
        val packageManager = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager
            .queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { resolved ->
                resolved.loadLabel(packageManager).toString() to
                    ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name)
            }
    }

    private fun start(intent: Intent, confirmation: String): ActionResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Performed(confirmation)
    } catch (_: ActivityNotFoundException) {
        ActionResult.Failed("Nothing on this device can handle that")
    }
}
