package com.questorylabs.tvc.agent.control

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import com.questorylabs.tvc.agent.ActivityKind
import com.questorylabs.tvc.agent.AgentState
import com.questorylabs.tvc.agent.adb.AdbManager
import com.questorylabs.tvc.agent.ui.OverlayController
import com.questorylabs.tvc.agent.ui.ToastKind
import com.questorylabs.tvc.shared.Command
import com.questorylabs.tvc.shared.KeyCode
import com.questorylabs.tvc.shared.MediaSessions
import com.questorylabs.tvc.shared.MemoryStats
import com.questorylabs.tvc.shared.NowPlayingInfo
import com.questorylabs.tvc.shared.PrivateDnsState
import com.questorylabs.tvc.shared.Protocol
import com.questorylabs.tvc.shared.Reply
import com.questorylabs.tvc.shared.Request
import com.questorylabs.tvc.shared.Requirement
import com.questorylabs.tvc.shared.ShellPolicy
import com.questorylabs.tvc.shared.Timeouts
import com.questorylabs.tvc.shared.VolumeAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CommandDispatcher(private val context: Context, private val adb: AdbManager) {
    private val inputs = InputSwitcher(context)
    private val apps = AppLauncher(context)
    private val mediaVolume = MediaVolume(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sleepJob: Job? = null

    fun adbConnected(): Boolean = adb.isConnected

    val volumeLevel: Int get() = mediaVolume.level
    val volumeMax: Int get() = mediaVolume.max

    fun sleepRemainingSec(): Int? = AgentState.sleepAtElapsedMs.value?.let { ((it - SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(0) }

    suspend fun handle(request: Request): Reply {
        val id = request.id
        val command = request.command
        AgentState.lastCommand.value = command::class.simpleName
        val a11y = ControlAccessibilityService.instance
        return when (command) {
            Command.Ping -> Reply.ok(id)

            is Command.Key -> when {
                a11y != null && a11y.performKey(command.code) -> Reply.ok(id)
                adb.isConnected -> shell(id, ShellPolicy.keyEventCommand(androidKeycode(command.code)))
                a11y != null -> Reply.fail(id, "action not performed")
                else -> Reply.fail(id, "enable the accessibility service or ADB tier", Requirement.ACCESSIBILITY)
            }

            is Command.Media -> {
                mediaVolume.media(command.action)
                AgentState.log(ActivityKind.MEDIA, command.action.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase))
                Reply.ok(id)
            }

            is Command.Volume -> {
                mediaVolume.volume(command.action, showSystemUi = a11y == null)
                showVolume(a11y != null)
                AgentState.log(ActivityKind.VOLUME, if (command.action == VolumeAction.MUTE) "Mute toggled" else "Volume set to ${mediaVolume.level}")
                Reply.ok(id, mediaVolume.level.toString())
            }

            is Command.SetVolume -> {
                mediaVolume.set(command.level, showSystemUi = a11y == null)
                showVolume(a11y != null)
                AgentState.log(ActivityKind.VOLUME, "Volume set to ${mediaVolume.level}")
                Reply.ok(id, mediaVolume.level.toString())
            }

            is Command.Text -> when {
                a11y != null && a11y.setText(command.text) -> Reply.ok(id).also { AgentState.log(ActivityKind.TEXT, "Text sent") }
                adb.isConnected -> ShellPolicy.textCommand(command.text)?.let { shell(id, it) } ?: Reply.fail(id, "nothing typable")
                a11y != null -> Reply.fail(id, "no text field focused")
                else -> Reply.fail(id, "enable the accessibility service or ADB tier", Requirement.ACCESSIBILITY)
            }

            is Command.LaunchApp -> {
                if (!ShellPolicy.isPackageName(command.packageName)) return Reply.fail(id, "bad package name")
                val intent = apps.launchIntent(command.packageName) ?: return Reply.fail(id, "app has no launcher")
                startActivity(id, intent, a11y != null).also {
                    if (it.ok) AgentState.log(ActivityKind.APP, "Opened ${apps.label(command.packageName)}")
                }
            }

            is Command.SwitchInput -> {
                val intent = inputs.launchIntent(command.inputId) ?: return Reply.fail(id, "unknown input")
                startActivity(id, intent, a11y != null).also {
                    if (it.ok) {
                        val label = inputs.list().firstOrNull { input -> input.id == command.inputId }?.label ?: "input"
                        AgentState.log(ActivityKind.INPUT, "Switched to $label")
                        OverlayController.toast(ToastKind.INPUT, label)
                    }
                }
            }

            is Command.RawKey -> requireAdb(id) { shell(id, ShellPolicy.keyEventCommand(command.keycode)) }

            is Command.Debloat -> requireAdb(id) {
                when {
                    !ShellPolicy.isPackageName(command.packageName) -> Reply.fail(id, "bad package name")
                    command.packageName == context.packageName -> Reply.fail(id, "refusing to disable the agent itself")
                    command.disable -> shell(id, ShellPolicy.disableCommand(command.packageName))
                    else -> shell(id, ShellPolicy.enableCommand(command.packageName))
                }.also {
                    if (it.ok) AgentState.log(ActivityKind.PACKAGE, "${if (command.disable) "Disabled" else "Restored"} ${command.packageName}")
                }
            }

            Command.DisabledPackages -> requireAdb(id) { shell(id, ShellPolicy.LIST_DISABLED) }

            is Command.SleepTimer -> sleepTimer(id, command.minutes, a11y != null)

            Command.Reboot -> requireAdb(id) {
                AgentState.log(ActivityKind.SYSTEM, "Reboot requested")
                scope.launch {
                    delay(REBOOT_DELAY_MS)
                    val reply = shell(id, ShellPolicy.REBOOT)
                    if (!reply.ok) AgentState.log(ActivityKind.SYSTEM, "Reboot failed: ${reply.error}")
                }
                Reply.ok(id)
            }

            is Command.PrivateDns -> requireAdb(id) {
                val host = command.hostname?.trim()
                when {
                    host == null || host.isEmpty() -> shell(id, ShellPolicy.DNS_OFF)
                    !ShellPolicy.isHostname(host) -> Reply.fail(id, "not a valid hostname")
                    else -> shell(id, ShellPolicy.dnsHostCommand(host)).takeIf { !it.ok } ?: shell(id, ShellPolicy.DNS_HOSTNAME)
                }.also {
                    if (it.ok) AgentState.log(ActivityKind.SYSTEM, if (host.isNullOrEmpty()) "Private DNS off" else "Private DNS: $host")
                }.let { if (it.ok) privateDnsStatus(id) else it }
            }

            Command.PrivateDnsStatus -> requireAdb(id) { privateDnsStatus(id) }

            Command.Memory -> Reply.ok(id, memoryJson())

            Command.FreeMemory -> requireAdb(id) {
                val reply = shell(id, ShellPolicy.KILL_ALL)
                if (reply.ok) {
                    AgentState.log(ActivityKind.SYSTEM, "Freed memory")
                    Reply.ok(id, memoryJson())
                } else reply
            }

            is Command.ForceStop -> requireAdb(id) {
                when {
                    !ShellPolicy.isPackageName(command.packageName) -> Reply.fail(id, "bad package name")
                    command.packageName == context.packageName -> Reply.fail(id, "refusing to stop the agent itself")
                    else -> shell(id, ShellPolicy.forceStopCommand(command.packageName)).also {
                        if (it.ok) AgentState.log(ActivityKind.APP, "Stopped ${apps.label(command.packageName)}")
                    }
                }
            }

            Command.NowPlaying -> requireAdb(id) {
                val reply = shell(id, ShellPolicy.MEDIA_SESSIONS, Timeouts.forCommand(command))
                if (reply.ok) Reply.ok(id, Protocol.json.encodeToString(NowPlayingInfo.serializer(), MediaSessions.parse(reply.output.orEmpty()).let { it.copy(subtitle = it.subtitle ?: it.packageName?.let(apps::label)) })) else reply
            }

            is Command.Notify -> {
                val title = command.title.take(MAX_LABEL)
                val subtitle = command.subtitle?.take(MAX_LABEL)
                OverlayController.progress(title, subtitle, command.progress)
                if (command.progress == null || command.progress == 0f) AgentState.log(ActivityKind.SCENE, title)
                Reply.ok(id)
            }
        }
    }

    private fun showVolume(overlay: Boolean) {
        if (overlay) OverlayController.volume(mediaVolume.level, mediaVolume.max)
    }

    private fun sleepTimer(id: Int, minutes: Int, a11yEnabled: Boolean): Reply {
        sleepJob?.cancel()
        if (minutes <= 0) {
            AgentState.sleepAtElapsedMs.value = null
            AgentState.log(ActivityKind.SYSTEM, "Sleep timer off")
            return Reply.ok(id)
        }
        if (minutes > MAX_SLEEP_MIN) return Reply.fail(id, "at most $MAX_SLEEP_MIN minutes")
        if (!a11yEnabled && !adb.isConnected) {
            return Reply.fail(id, "enable the accessibility service or ADB tier", Requirement.ACCESSIBILITY)
        }
        val at = SystemClock.elapsedRealtime() + minutes * 60_000L
        AgentState.sleepAtElapsedMs.value = at
        OverlayController.flashSleep()
        AgentState.log(ActivityKind.SYSTEM, "Sleep in $minutes min")
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            AgentState.sleepAtElapsedMs.value = null
            val slept = adb.isConnected && runCatching { adb.shell(ShellPolicy.keyEventCommand(KeyEvent.KEYCODE_SLEEP)) }.isSuccess
            if (!slept) ControlAccessibilityService.instance?.lockScreen()
        }
        return Reply.ok(id)
    }

    private suspend fun privateDnsStatus(id: Int): Reply {
        val mode = shell(id, ShellPolicy.DNS_GET_MODE)
        if (!mode.ok) return mode
        val host = shell(id, ShellPolicy.DNS_GET_HOST)
        val state = PrivateDnsState(
            mode = mode.output.orEmpty().trim().takeUnless { it.isEmpty() || it == "null" } ?: "off",
            hostname = host.output?.trim()?.takeUnless { it.isEmpty() || it == "null" },
        )
        return Reply.ok(id, Protocol.json.encodeToString(PrivateDnsState.serializer(), state))
    }

    private fun memoryJson(): String {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return Protocol.json.encodeToString(MemoryStats.serializer(), MemoryStats(info.totalMem, info.availMem))
    }

    private suspend fun startActivity(id: Int, intent: Intent, a11yEnabled: Boolean): Reply {
        if (!a11yEnabled && adb.isConnected) {
            val component = intent.component
            val cmd = if (component != null) {
                "am start -n ${component.flattenToShortString()}"
            } else {
                "am start -a ${intent.action} -d ${intent.dataString}"
            }
            return shell(id, cmd)
        }
        if (!a11yEnabled) {
            return Reply.fail(id, "Android blocks background launches; enable the accessibility service or ADB tier", Requirement.ACCESSIBILITY)
        }
        return try {
            context.startActivity(intent)
            Reply.ok(id)
        } catch (e: Exception) {
            Reply.fail(id, e.message ?: "launch failed")
        }
    }

    private inline fun requireAdb(id: Int, block: () -> Reply): Reply =
        if (adb.isConnected) block() else Reply.fail(id, "ADB tier not connected", Requirement.ADB)

    private suspend fun shell(id: Int, cmd: String, timeoutMs: Long = 15_000): Reply {
        if (!ShellPolicy.isAllowed(cmd)) return Reply.fail(id, "command not allowed")
        return runCatching { adb.shell(cmd, timeoutMs) }
            .fold({ Reply.ok(id, it) }, { Reply.fail(id, it.message ?: "shell failed", Requirement.ADB) })
    }

    private fun androidKeycode(code: KeyCode): Int = when (code) {
        KeyCode.DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
        KeyCode.DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        KeyCode.DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        KeyCode.DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        KeyCode.DPAD_CENTER -> KeyEvent.KEYCODE_DPAD_CENTER
        KeyCode.BACK -> KeyEvent.KEYCODE_BACK
        KeyCode.HOME -> KeyEvent.KEYCODE_HOME
        KeyCode.RECENTS -> KeyEvent.KEYCODE_APP_SWITCH
        KeyCode.MENU -> KeyEvent.KEYCODE_MENU
        KeyCode.POWER_DIALOG -> KeyEvent.KEYCODE_POWER
    }

    private companion object {
        const val MAX_SLEEP_MIN = 600
        const val MAX_LABEL = 80
        const val REBOOT_DELAY_MS = 500L
    }
}
