package com.questorylabs.tvc.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun requestRoundTrip() {
        val commands = listOf(
            Command.Ping,
            Command.Key(KeyCode.DPAD_UP),
            Command.Media(MediaAction.PLAY_PAUSE),
            Command.Volume(VolumeAction.MUTE),
            Command.Text("hello world"),
            Command.LaunchApp("com.example"),
            Command.SwitchInput("com.tcl.tvinput/.Service/HW5"),
            Command.RawKey(178),
            Command.Debloat("com.example", true),
            Command.DisabledPackages,
            Command.SetVolume(12),
            Command.SleepTimer(45),
            Command.Reboot,
            Command.PrivateDns("dns.adguard.com"),
            Command.PrivateDns(null),
            Command.PrivateDnsStatus,
            Command.Memory,
            Command.FreeMemory,
            Command.ForceStop("com.example"),
            Command.NowPlaying,
            Command.Notify("Movie night", "Step 1", 0.25f),
        )
        commands.forEachIndexed { i, cmd ->
            val encoded = Protocol.json.encodeToString(Request.serializer(), Request(i, cmd))
            val decoded = Protocol.json.decodeFromString(Request.serializer(), encoded)
            assertEquals(Request(i, cmd), decoded)
        }
    }

    @Test
    fun replyCarriesRequirement() {
        val reply = Reply.fail(3, "no accessibility", Requirement.ACCESSIBILITY)
        val decoded = Protocol.json.decodeFromString(Reply.serializer(), Protocol.json.encodeToString(Reply.serializer(), reply))
        assertEquals(Requirement.ACCESSIBILITY, decoded.requires)
        assertFalse(decoded.ok)
    }

    @Test
    fun rawShellCommandIsNotAccepted() {
        val raw = """{"id":1,"command":{"type":"shell","cmd":"am start -n com.x/.Hidden"}}"""
        assertTrue(runCatching { Protocol.json.decodeFromString(Request.serializer(), raw) }.isFailure)
    }

    @Test
    fun packageCommandsGetLongTimeout() {
        assertEquals(Timeouts.PACKAGE_MS, Timeouts.forCommand(Command.Debloat("com.x", true)))
        assertEquals(Timeouts.PACKAGE_MS, Timeouts.forCommand(Command.DisabledPackages))
        assertEquals(Timeouts.DEFAULT_MS, Timeouts.forCommand(Command.Key(KeyCode.DPAD_UP)))
    }

    @Test
    fun unknownFieldsIgnored() {
        val decoded = Protocol.json.decodeFromString(Reply.serializer(), """{"id":1,"ok":true,"future":"x"}""")
        assertTrue(decoded.ok)
    }

    @Test
    fun oldDeviceInfoStillDecodes() {
        val v1 = """{"name":"x","model":"y","sdk":34,"agentVersion":"0.1.0","protocolVersion":1,"accessibilityEnabled":true,"adbConnected":false,"hasTvInputs":true}"""
        val info = Protocol.json.decodeFromString(DeviceInfo.serializer(), v1)
        assertEquals(null, info.volume)
        assertFalse(info.overlayAvailable)
    }
}
