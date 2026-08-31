package com.vexo.actions

/**
 * Turns a spoken phrase into a [Command]. The wake phrase is tolerated but not required, since
 * the recogniser often captures it along with the request.
 */
object CommandParser {

    private val wakePhrases = listOf("hi vexo", "hey vexo", "ok vexo", "hello vexo", "vexo")
    private val openVerbs = listOf("open up", "open", "launch", "start", "go to", "show me", "show")
    private val panels = mapOf(
        "wifi" to SettingsPanel.Wifi,
        "wi-fi" to SettingsPanel.Wifi,
        "wireless" to SettingsPanel.Wifi,
        "bluetooth" to SettingsPanel.Bluetooth,
        "display" to SettingsPanel.Display,
        "brightness" to SettingsPanel.Display,
        "screen" to SettingsPanel.Display,
        "sound" to SettingsPanel.Sound,
        "volume" to SettingsPanel.Sound,
        "battery" to SettingsPanel.Battery,
        "location" to SettingsPanel.Location,
        "gps" to SettingsPanel.Location,
        "apps" to SettingsPanel.Apps,
        "applications" to SettingsPanel.Apps,
    )

    private val punctuation = Regex("[^a-z0-9 -]")
    private val repeatedSpaces = Regex(" +")

    fun parse(spoken: String): Command {
        val request = stripWakePhrase(normalise(spoken))
        val verb = openVerbs.firstOrNull { request == it || request.startsWith("$it ") }
            ?: return Command.Unknown

        val target = request.removePrefix(verb).trim().removeSuffix("please").trim()
        if (target.isEmpty()) return Command.Unknown

        if (target == "settings" || target == "setting") {
            return Command.OpenSettings(SettingsPanel.Root)
        }

        val withoutSuffix = target.removeSuffix("settings").removeSuffix("setting").trim()
        panels[withoutSuffix]?.let { return Command.OpenSettings(it) }

        return Command.OpenApp(target)
    }

    private fun normalise(spoken: String): String = spoken
        .lowercase()
        .replace(punctuation, " ")
        .replace(repeatedSpaces, " ")
        .trim()

    private fun stripWakePhrase(text: String): String {
        val phrase = wakePhrases.firstOrNull { text == it || text.startsWith("$it ") }
            ?: return text
        return text.removePrefix(phrase).trim()
    }
}
