package com.questorylabs.tvc.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class SceneStep(val command: Command, val delayMs: Long = 0)

@Serializable
data class Scene(val id: String, val name: String, val steps: List<SceneStep>)

object Scenes {
    const val MAX_STEPS = 20
    const val MAX_DELAY_MS = 30_000L
    const val MAX_NAME = 32

    val defaults = listOf(
        Scene(
            "movie-night",
            "Movie night",
            listOf(SceneStep(Command.Key(KeyCode.HOME)), SceneStep(Command.SetVolume(15), 500), SceneStep(Command.Media(MediaAction.PLAY), 500)),
        ),
        Scene(
            "bedtime",
            "Bedtime",
            listOf(SceneStep(Command.SetVolume(6)), SceneStep(Command.SleepTimer(30), 300)),
        ),
        Scene(
            "hush",
            "Hush",
            listOf(SceneStep(Command.Media(MediaAction.PAUSE)), SceneStep(Command.Volume(VolumeAction.MUTE), 200)),
        ),
        Scene(
            "tidy-up",
            "Tidy up",
            listOf(SceneStep(Command.FreeMemory), SceneStep(Command.Key(KeyCode.HOME), 500)),
        ),
    )

    private val listSerializer = ListSerializer(Scene.serializer())

    fun encode(scenes: List<Scene>): String = Protocol.json.encodeToString(listSerializer, scenes)

    fun decode(text: String?): List<Scene> {
        if (text.isNullOrBlank()) return defaults
        return runCatching { Protocol.json.decodeFromString(listSerializer, text) }
            .getOrNull()
            ?.filter { validate(it) == null }
            ?: defaults
    }

    fun allowed(command: Command): Boolean = when (command) {
        is Command.Key, is Command.Media, is Command.Volume, is Command.SetVolume, is Command.Text,
        is Command.LaunchApp, is Command.SwitchInput, is Command.SleepTimer, Command.FreeMemory -> true
        else -> false
    }

    fun validate(scene: Scene): String? = when {
        scene.name.isBlank() -> "Name required"
        scene.name.length > MAX_NAME -> "Name too long"
        scene.steps.isEmpty() -> "Add at least one step"
        scene.steps.size > MAX_STEPS -> "Too many steps"
        scene.steps.any { it.delayMs !in 0..MAX_DELAY_MS } -> "Delay out of range"
        scene.steps.any { !allowed(it.command) } -> "Unsupported step"
        else -> null
    }

    fun describe(command: Command): String = when (command) {
        is Command.Key -> when (command.code) {
            KeyCode.HOME -> "Go home"
            KeyCode.BACK -> "Back"
            KeyCode.DPAD_CENTER -> "Press OK"
            else -> "Press ${command.code.name.lowercase().replace('_', ' ')}"
        }
        is Command.Media -> when (command.action) {
            MediaAction.PLAY_PAUSE -> "Play / pause"
            else -> command.action.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        is Command.Volume -> when (command.action) {
            VolumeAction.UP -> "Volume up"
            VolumeAction.DOWN -> "Volume down"
            VolumeAction.MUTE -> "Mute"
        }
        is Command.SetVolume -> "Volume to ${command.level}"
        is Command.Text -> "Type \"${command.text.take(20)}\""
        is Command.LaunchApp -> "Open ${command.packageName.substringAfterLast('.')}"
        is Command.SwitchInput -> "Switch input"
        is Command.SleepTimer -> if (command.minutes == 0) "Cancel sleep timer" else "Sleep in ${command.minutes} min"
        Command.FreeMemory -> "Free memory"
        else -> command::class.simpleName.orEmpty()
    }
}
