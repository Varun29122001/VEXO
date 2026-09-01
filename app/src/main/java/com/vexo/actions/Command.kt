package com.vexo.actions

enum class SettingsPanel {
    Root,
    Wifi,
    Bluetooth,
    Display,
    Sound,
    Battery,
    Location,
    Apps
}

sealed interface Command {

    data class OpenSettings(val panel: SettingsPanel) : Command

    data class OpenApp(val query: String) : Command

    data object Unknown : Command
}
