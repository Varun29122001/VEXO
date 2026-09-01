package com.vexo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.vexo.ui.settings.IconVariant

/**
 * Manages app icon variant selection at runtime using activity-alias.
 * Enables/disables activity aliases to change the launcher icon.
 */
object IconManager {

    private const val PREFS_NAME = "vexo_settings"
    private const val KEY_SELECTED_ICON = "selected_icon"

    fun getSelectedIcon(context: Context): IconVariant {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val aliasClassName = prefs.getString(KEY_SELECTED_ICON, IconVariant.LIQUID_GOLD.aliasClassName)
            ?: IconVariant.LIQUID_GOLD.aliasClassName
        return IconVariant.fromAliasClassName(aliasClassName)
    }

    fun setSelectedIcon(context: Context, variant: IconVariant) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_ICON, variant.aliasClassName).apply()

        // Enable the selected icon alias and disable all others
        switchIcon(context, variant)
    }

    private fun switchIcon(context: Context, enabledVariant: IconVariant) {
        val packageManager = context.packageManager

        IconVariant.values().forEach { variant ->
            val componentName = ComponentName(context.packageName, variant.aliasClassName)
            val newState = if (variant == enabledVariant) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            packageManager.setComponentEnabledSetting(
                componentName,
                newState,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
