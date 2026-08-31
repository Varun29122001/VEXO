package com.vexo.actions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

sealed interface ActionResult {
    data object Performed : ActionResult
    data object NotUnderstood : ActionResult
    data class Failed(val reason: String) : ActionResult
}

class ActionManager(private val context: Context) {

    fun execute(command: Command): ActionResult = when (command) {
        is Command.OpenSettings -> start(Intent(actionFor(command.panel)))
        is Command.OpenApp -> openApp(command.query)
        Command.Unknown -> ActionResult.NotUnderstood
    }

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

    private fun openApp(query: String): ActionResult {
        val apps = launcherActivities()
        val match = apps.firstOrNull { it.first.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.first.contains(query, ignoreCase = true) }
            ?: return ActionResult.Failed("No app called \"$query\"")

        return start(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(match.second)
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

    private fun start(intent: Intent): ActionResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Performed
    } catch (_: ActivityNotFoundException) {
        ActionResult.Failed("Nothing on this device can handle that")
    }
}
