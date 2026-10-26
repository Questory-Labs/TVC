package com.questorylabs.tvc.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Protocol {
    const val PORT = 7887
    const val NSD_SERVICE_TYPE = "_tvcompanion._tcp."
    const val WS_PATH = "/ws"
    const val VERSION = 2
    const val SCREENSHOT_PATH = "/screenshot"
    const val INSTALL_PATH = "/install"
    const val CLIENT_HEADER = "X-Client-Name"
    const val MAX_APK_BYTES = 1L shl 30

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }
}

enum class KeyCode { DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER, BACK, HOME, RECENTS, MENU, POWER_DIALOG }

enum class MediaAction { PLAY_PAUSE, PLAY, PAUSE, STOP, NEXT, PREVIOUS, FAST_FORWARD, REWIND }

enum class VolumeAction { UP, DOWN, MUTE }

enum class Requirement { ACCESSIBILITY, ADB }

@Serializable
sealed class Command {
    @Serializable @SerialName("ping") data object Ping : Command()
    @Serializable @SerialName("key") data class Key(val code: KeyCode) : Command()
    @Serializable @SerialName("media") data class Media(val action: MediaAction) : Command()
    @Serializable @SerialName("volume") data class Volume(val action: VolumeAction) : Command()
    @Serializable @SerialName("text") data class Text(val text: String) : Command()
    @Serializable @SerialName("launchApp") data class LaunchApp(val packageName: String) : Command()
    @Serializable @SerialName("switchInput") data class SwitchInput(val inputId: String) : Command()
    @Serializable @SerialName("rawKey") data class RawKey(val keycode: Int) : Command()
    @Serializable @SerialName("debloat") data class Debloat(val packageName: String, val disable: Boolean) : Command()
    @Serializable @SerialName("disabledPackages") data object DisabledPackages : Command()
    @Serializable @SerialName("setVolume") data class SetVolume(val level: Int) : Command()
    @Serializable @SerialName("sleepTimer") data class SleepTimer(val minutes: Int) : Command()
    @Serializable @SerialName("reboot") data object Reboot : Command()
    @Serializable @SerialName("privateDns") data class PrivateDns(val hostname: String?) : Command()
    @Serializable @SerialName("privateDnsStatus") data object PrivateDnsStatus : Command()
    @Serializable @SerialName("memory") data object Memory : Command()
    @Serializable @SerialName("freeMemory") data object FreeMemory : Command()
    @Serializable @SerialName("forceStop") data class ForceStop(val packageName: String) : Command()
    @Serializable @SerialName("nowPlaying") data object NowPlaying : Command()
    @Serializable @SerialName("notify") data class Notify(val title: String, val subtitle: String? = null, val progress: Float? = null) : Command()
}

@Serializable
data class Request(val id: Int, val command: Command)

object Timeouts {
    const val DEFAULT_MS = 3_000L
    const val PACKAGE_MS = 20_000L
    const val SCREENSHOT_MS = 15_000L
    const val INSTALL_MS = 300_000L

    fun forCommand(command: Command): Long = when (command) {
        is Command.Debloat, Command.DisabledPackages, Command.FreeMemory, is Command.ForceStop, Command.NowPlaying -> PACKAGE_MS
        else -> DEFAULT_MS
    }
}

@Serializable
data class Reply(
    val id: Int,
    val ok: Boolean,
    val error: String? = null,
    val requires: Requirement? = null,
    val output: String? = null,
) {
    companion object {
        fun ok(id: Int, output: String? = null) = Reply(id, true, output = output)
        fun fail(id: Int, error: String, requires: Requirement? = null) = Reply(id, false, error = error, requires = requires)
    }
}

@Serializable
data class DeviceInfo(
    val name: String,
    val model: String,
    val sdk: Int,
    val agentVersion: String,
    val protocolVersion: Int = Protocol.VERSION,
    val accessibilityEnabled: Boolean,
    val adbConnected: Boolean,
    val hasTvInputs: Boolean,
    val volume: Int? = null,
    val maxVolume: Int? = null,
    val sleepRemainingSec: Int? = null,
    val overlayAvailable: Boolean = false,
)

@Serializable
data class MemoryStats(val totalBytes: Long, val availBytes: Long)

@Serializable
data class PrivateDnsState(val mode: String, val hostname: String? = null)

enum class PlaybackState { PLAYING, PAUSED, STOPPED, NONE }

@Serializable
data class NowPlayingInfo(
    val packageName: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val state: PlaybackState = PlaybackState.NONE,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
)

@Serializable
data class TvInput(val id: String, val label: String, val hdmiPort: Int? = null)

@Serializable
data class AppEntry(val packageName: String, val label: String, val iconPng: String? = null)

@Serializable
data class SavedDevice(val name: String, val host: String, val token: String, val port: Int = Protocol.PORT)
