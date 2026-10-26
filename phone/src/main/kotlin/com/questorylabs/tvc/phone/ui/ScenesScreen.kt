package com.questorylabs.tvc.phone.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.questorylabs.tvc.phone.DeviceUi
import com.questorylabs.tvc.phone.MainViewModel
import com.questorylabs.tvc.phone.SceneRun
import com.questorylabs.tvc.shared.Command
import com.questorylabs.tvc.shared.KeyCode
import com.questorylabs.tvc.shared.MediaAction
import com.questorylabs.tvc.shared.Scene
import com.questorylabs.tvc.shared.SceneStep
import com.questorylabs.tvc.shared.Scenes
import com.questorylabs.tvc.shared.VolumeAction
import java.util.UUID

private val tileColors = listOf(
    Tvc.PrimaryContainer to Tvc.OnPrimaryContainer,
    Tvc.TertiaryContainer to Tvc.OnTertiaryContainer,
    Tvc.SecondaryContainer to Tvc.OnSecondaryContainer,
    Tvc.ContainerHigh to Tvc.OnSurface,
)

@Composable
fun ScenesScreen(vm: MainViewModel, state: DeviceUi, wide: Boolean) {
    val scenes by vm.scenes.collectAsStateWithLifecycle()
    val run by vm.sceneRun.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Scene?>(null) }

    AnimatedContent(
        targetState = editing,
        transitionSpec = {
            val dir = if (targetState != null) 1 else -1
            (slideInHorizontally(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { it / 3 * dir } + fadeIn())
                .togetherWith(slideOutHorizontally { -it / 3 * dir } + fadeOut())
        },
        contentKey = { it?.id },
        label = "scenes",
    ) { scene ->
        if (scene != null) {
            SceneEditor(vm, state, scene, isNew = scenes.none { it.id == scene.id }) { editing = null }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scenes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Button(onClick = { editing = Scene(UUID.randomUUID().toString(), "", emptyList()) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("New")
                    }
                }
                AnimatedVisibility(run != null, enter = expandVertically(spring(Spring.DampingRatioLowBouncy)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    run?.let { RunCard(it, scenes.firstOrNull { s -> s.id == it.sceneId }?.name ?: "Scene") { vm.cancelScene() } }
                }
                val columns = if (wide) 3 else 2
                scenes.chunked(columns).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { c, scene ->
                            val (bg, fg) = tileColors[(r * columns + c) % tileColors.size]
                            SceneTile(
                                scene,
                                bg,
                                fg,
                                running = run?.sceneId == scene.id && run?.done == false,
                                enabled = state.connected && (run == null || run?.done == true),
                                modifier = Modifier.weight(1f),
                                onRun = { vm.runScene(state.device.host, scene) },
                                onEdit = { editing = scene },
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                SleepTimerCard(vm, state)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RunCard(run: SceneRun, name: String, onClose: () -> Unit) {
    SectionCard(color = if (run.error != null) Tvc.ErrorContainer else Tvc.Container) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp)) {
                if (run.done) StarBlob(Star9, if (run.error != null) Tvc.Error else Tvc.Online, Modifier.fillMaxSize())
                else MorphingStar(Star9, Tvc.Primary, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    run.error?.let { "Stopped at ${run.label}: $it" } ?: if (run.done) "Done" else "Step ${run.step + 1} of ${run.total} · ${run.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tvc.OnSurfaceVariant,
                    maxLines = 2,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = if (run.done) "Dismiss" else "Cancel") }
        }
        WavyProgress(
            if (run.done && run.error == null) 1f else run.step.toFloat() / run.total.coerceAtLeast(1),
            Modifier.fillMaxWidth().height(12.dp),
            animate = !run.done,
        )
    }
}

@Composable
private fun SceneTile(scene: Scene, bg: Color, fg: Color, running: Boolean, enabled: Boolean, modifier: Modifier, onRun: () -> Unit, onEdit: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(bg).springPress(onRun, enabled, pressedScale = 0.95f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (running) MorphingStar(Star9, fg.copy(alpha = 0.3f), Modifier.fillMaxSize())
                else StarBlob(Star9, fg.copy(alpha = 0.2f), Modifier.fillMaxSize())
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = fg)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${scene.name}", tint = fg.copy(alpha = 0.8f)) }
        }
        Text(scene.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${scene.steps.size} steps · " + scene.steps.joinToString(" › ") { Scenes.describe(it.command) },
            style = MaterialTheme.typography.bodySmall,
            color = fg.copy(alpha = 0.75f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SleepTimerCard(vm: MainViewModel, state: DeviceUi) {
    val remaining = state.info?.sleepRemainingSec
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBlob(Icons.Rounded.Bedtime, Tvc.TertiaryContainer, Tvc.OnTertiaryContainer, 48.dp, CircleShape)
            Column(Modifier.weight(1f)) {
                Text("Sleep timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (remaining != null && remaining > 0) "TV sleeps in ${(remaining + 59) / 60} min" else "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tvc.OnSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 60, 90).forEach { min ->
                val active = if (min == 0) remaining == null || remaining <= 0 else false
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) Tvc.Tertiary else Tvc.ContainerHigh)
                        .springPress({ vm.sleepTimer(state.device.host, min) }, enabled = state.connected)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (min == 0) "Off" else "$min", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (active) Tvc.OnTertiary else Tvc.OnSurface)
                }
            }
        }
    }
}

private val delays = listOf(0L, 500L, 1_000L, 3_000L, 5_000L, 10_000L)

private fun delayLabel(ms: Long) = when {
    ms == 0L -> "now"
    ms < 1_000 -> "+${ms}ms"
    else -> "+${ms / 1000}s"
}

@Composable
private fun SceneEditor(vm: MainViewModel, state: DeviceUi, initial: Scene, isNew: Boolean, onDone: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var steps by remember { mutableStateOf(initial.steps) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun add(command: Command) {
        if (steps.size < Scenes.MAX_STEPS) steps = steps + SceneStep(command, if (steps.isEmpty()) 0 else 500)
    }

    fun replace(i: Int, step: SceneStep) {
        steps = steps.toMutableList().also { it[i] = step }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(if (isNew) "New scene" else "Edit scene", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(onClick = {
                error = vm.saveScene(Scene(initial.id, name.trim(), steps))
                if (error == null) onDone()
            }) { Text("Save") }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(Scenes.MAX_NAME) },
            label = { Text("Name") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Tvc.Error, style = MaterialTheme.typography.bodyMedium) }

        SectionCard {
            SectionTitle("Steps") { Text("${steps.size} / ${Scenes.MAX_STEPS}", style = MaterialTheme.typography.labelMedium, color = Tvc.Outline) }
            if (steps.isEmpty()) Text("Add steps from the list below. They run in order.", style = MaterialTheme.typography.bodySmall, color = Tvc.OnSurfaceVariant)
            steps.forEachIndexed { i, step ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tvc.ContainerHigh).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Tvc.Primary), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Tvc.OnPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(label(step.command, state), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            delayLabel(step.delayMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Tvc.Primary,
                            modifier = Modifier.clip(CircleShape).springPress({
                                replace(i, step.copy(delayMs = delays[(delays.indexOf(step.delayMs) + 1).mod(delays.size)]))
                            }).padding(vertical = 2.dp),
                        )
                    }
                    when (val c = step.command) {
                        is Command.SetVolume -> Stepper({ replace(i, step.copy(command = Command.SetVolume((c.level - 1).coerceAtLeast(0)))) }, { replace(i, step.copy(command = Command.SetVolume((c.level + 1).coerceAtMost(100)))) })
                        is Command.SleepTimer -> Stepper({ replace(i, step.copy(command = Command.SleepTimer((c.minutes - 15).coerceAtLeast(15)))) }, { replace(i, step.copy(command = Command.SleepTimer((c.minutes + 15).coerceAtMost(600)))) })
                        else -> {}
                    }
                    IconButton(enabled = i > 0, onClick = { steps = steps.toMutableList().also { it.add(i - 1, it.removeAt(i)) } }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(enabled = i < steps.lastIndex, onClick = { steps = steps.toMutableList().also { it.add(i + 1, it.removeAt(i)) } }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    IconButton(onClick = { steps = steps.filterIndexed { k, _ -> k != i } }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove step")
                    }
                }
            }
        }

        SectionCard {
            SectionTitle("Add step")
            val palette = listOf<Pair<String, () -> Unit>>(
                "Home" to { add(Command.Key(KeyCode.HOME)) },
                "Back" to { add(Command.Key(KeyCode.BACK)) },
                "OK" to { add(Command.Key(KeyCode.DPAD_CENTER)) },
                "Play" to { add(Command.Media(MediaAction.PLAY)) },
                "Pause" to { add(Command.Media(MediaAction.PAUSE)) },
                "Play / pause" to { add(Command.Media(MediaAction.PLAY_PAUSE)) },
                "Mute" to { add(Command.Volume(VolumeAction.MUTE)) },
                "Set volume" to { add(Command.SetVolume(state.info?.volume ?: 10)) },
                "Sleep timer" to { add(Command.SleepTimer(30)) },
                "Free memory" to { add(Command.FreeMemory) },
                "Open app" to { picker = "app" },
                "Switch input" to { picker = "input" },
            )
            palette.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (text, action) ->
                        val enabled = steps.size < Scenes.MAX_STEPS && when (text) {
                            "Open app" -> state.apps.isNotEmpty()
                            "Switch input" -> state.inputs.isNotEmpty()
                            else -> true
                        }
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Tvc.ContainerHigh).springPress(action, enabled).padding(vertical = 12.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Tvc.OnSurface else Tvc.Outline, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (!isNew) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete scene", color = Tvc.Error) }
        }
        Spacer(Modifier.height(8.dp))
    }

    when (picker) {
        "app" -> AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text("Open app") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.apps.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).springPress({ add(Command.LaunchApp(app.packageName)); picker = null }).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppIcon(app, 36.dp)
                            Text(app.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
        )
        "input" -> AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text("Switch input") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.inputs.forEach { input ->
                        Text(
                            input.label,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).springPress({ add(Command.SwitchInput(input.id)); picker = null }).padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            confirmButton = { TextButton(onClick = { vm.deleteScene(initial.id); confirmDelete = false; onDone() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stepper(onMinus: () -> Unit, onPlus: () -> Unit) {
    Row {
        IconButton(onClick = onMinus, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Remove, contentDescription = "Less", modifier = Modifier.size(18.dp)) }
        IconButton(onClick = onPlus, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Add, contentDescription = "More", modifier = Modifier.size(18.dp)) }
    }
}

private fun label(command: Command, state: DeviceUi): String = when (command) {
    is Command.LaunchApp -> "Open " + (state.apps.firstOrNull { it.packageName == command.packageName }?.label ?: command.packageName)
    is Command.SwitchInput -> "Switch to " + (state.inputs.firstOrNull { it.id == command.inputId }?.label ?: "input")
    else -> Scenes.describe(command)
}
