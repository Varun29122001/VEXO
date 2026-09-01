package com.vexo.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {

    @Test
    fun `open settings with wake phrase`() {
        assertEquals(
            Command.OpenSettings(SettingsPanel.Root),
            CommandParser.parse("Hi VEXO open settings"),
        )
    }

    @Test
    fun `open settings without wake phrase`() {
        assertEquals(
            Command.OpenSettings(SettingsPanel.Root),
            CommandParser.parse("open settings"),
        )
    }

    @Test
    fun `trailing punctuation and politeness are ignored`() {
        assertEquals(
            Command.OpenSettings(SettingsPanel.Root),
            CommandParser.parse("Hey VEXO, open settings please."),
        )
    }

    @Test
    fun `settings panels are resolved`() {
        assertEquals(
            Command.OpenSettings(SettingsPanel.Wifi),
            CommandParser.parse("vexo open wifi settings"),
        )
        assertEquals(
            Command.OpenSettings(SettingsPanel.Bluetooth),
            CommandParser.parse("open bluetooth"),
        )
        assertEquals(
            Command.OpenSettings(SettingsPanel.Display),
            CommandParser.parse("open brightness settings"),
        )
    }

    @Test
    fun `other targets become app launches`() {
        assertEquals(Command.OpenApp("chrome"), CommandParser.parse("hi vexo open chrome"))
        assertEquals(Command.OpenApp("youtube"), CommandParser.parse("launch youtube"))
        assertEquals(Command.OpenApp("gmail"), CommandParser.parse("go to gmail"))
    }

    @Test
    fun `unsupported phrases are unknown`() {
        assertEquals(Command.Unknown, CommandParser.parse("what's the weather"))
        assertEquals(Command.Unknown, CommandParser.parse("hi vexo"))
        assertEquals(Command.Unknown, CommandParser.parse("open"))
        assertEquals(Command.Unknown, CommandParser.parse(""))
    }
}
