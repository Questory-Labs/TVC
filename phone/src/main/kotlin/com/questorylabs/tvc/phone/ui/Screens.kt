package com.questorylabs.tvc.phone.ui

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.questorylabs.tvc.phone.DeviceUi
import com.questorylabs.tvc.phone.MainViewModel
import com.questorylabs.tvc.shared.AppEntry
import com.questorylabs.tvc.shared.Command
import com.questorylabs.tvc.shared.KeyCode
import com.questorylabs.tvc.shared.MediaAction
import com.questorylabs.tvc.shared.PlaybackState
import com.questorylabs.tvc.shared.SavedDevice
import com.questorylabs.tvc.shared.VolumeAction
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DevicesScreen(vm: MainViewModel, wide: Boolean) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val discovered by vm.discovered.collectAsStateWithLifecycle()
    val discovering by vm.discovering.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<SavedDevice?>(null) }
    var removing by remember { mutableStateOf<SavedDevice?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        vm.startDiscovery()
        onDispose { vm.stopDiscovery() }
    }

    val scan = {
        scanError = null
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                val device = MainViewModel.parsePayload(code.rawValue.orEmpty())
                if (device != null) vm.incomingLink.value = device else scanError = "Not a TV Companion code"
            }
            .addOnFailureListener { scanError = it.message ?: "Scanner unavailable" }
        Unit
    }

    val saved: @Composable ColumnScope.() -> Unit = {
        SectionTitle("Saved")
        if (devices.isEmpty()) {
            Text("None yet. Install the agent on the TV and the Streamer, then scan the code it shows.", style = MaterialTheme.typography.bodyMedium, color = Tvc.OnSurfaceVariant)
        }
        devices.forEach { device ->
            val state = states[device.host]
            SectionCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBlob(
                        if (state?.info?.hasTvInputs == false) Icons.Rounded.Cast else Icons.Rounded.Tv,
                        if (state?.connected == true) Tvc.PrimaryContainer else Tvc.ContainerHigh,
                        if (state?.connected == true) Tvc.OnPrimaryContainer else Tvc.Outline,
                        52.dp,
                        if (state?.info?.hasTvInputs == false) CircleShape else RoundedCornerShape(18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodySmall, color = Tvc.Outline)
                    }
                    IconButton(onClick = { vm.ping(device.host); vm.refresh(device.host) }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                    IconButton(onClick = { removing = device }) { Icon(Icons.Rounded.Delete, contentDescription = "Remove") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state?.connected == true) StatusChip("Online", Tvc.Online, Tvc.OnlineContainer, Tvc.OnOnline)
                    else StatusChip("Offline", Tvc.Error)
                    state?.latencyMs?.let { StatusChip("$it ms", if (it < 80) Tvc.Online else Tvc.Primary) }
                    state?.info?.let {
                        StatusChip("Nav", if (it.accessibilityEnabled) Tvc.Online else Tvc.Error)
                        StatusChip("ADB", if (it.adbConnected) Tvc.Online else Tvc.Primary)
                    }
                }
                if (state?.connected == true && state.info?.adbConnected == false) AdbRequired()
                state?.lastError?.let { Text(it, color = Tvc.Error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    val add: @Composable ColumnScope.() -> Unit = {
        SectionCard(color = Tvc.PrimaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp)) { SpinningStar(Star9, Tvc.Primary, Modifier.fillMaxSize(), periodMs = 9_000) }
                Column(Modifier.weight(1f)) {
                    Text("Scan the TV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Tvc.OnPrimaryContainer)
                    Text("Open TV Companion on the TV. It shows a code.", style = MaterialTheme.typography.bodyMedium, color = Tvc.OnPrimaryContainer)
                }
            }
            Button(onClick = scan, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR code", fontWeight = FontWeight.Bold)
            }
            scanError?.let { Text(it, color = Tvc.Error, style = MaterialTheme.typography.bodySmall) }
        }

        SectionCard {
            SectionTitle("On this network")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Radar(discovering, Modifier.size(72.dp))
                Text(
                    if (discovered.isEmpty()) if (discovering) "Searching for agents..." else "Nothing found" else "${discovered.size} found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tvc.OnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!discovering) TextButton(onClick = { vm.startDiscovery() }) { Text("Search again") }
            }
            discovered.forEach { found ->
                val known = devices.any { it.host == found.host }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tvc.ContainerHigh).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(found.model ?: found.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${found.host}:${found.port}", style = MaterialTheme.typography.bodySmall, color = Tvc.Outline)
                    }
                    if (known) StatusChip("Saved", Tvc.Online)
                    else FilledTonalButton(onClick = { pending = SavedDevice(found.model ?: found.name, found.host, "", found.port) }) { Text("Add") }
                }
            }
        }

        ManualAdd(vm)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Devices", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = saved)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = add)
            }
        } else {
            saved()
            add()
        }
    }

    pending?.let { device ->
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Token for ${device.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open TV Companion on that device and type the token it shows.")
                    OutlinedTextField(value = token, onValueChange = { token = it.trim() }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = token.isNotBlank(), onClick = { vm.addDevice(device.copy(token = token)); pending = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    removing?.let { device ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${device.name}?") },
            text = { Text("You will need to scan its code again to reconnect.") },
            confirmButton = { TextButton(onClick = { vm.removeDevice(device.host); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ManualAdd(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    var manualHost by remember { mutableStateOf("") }
    var manualToken by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    SectionCard {
        SectionTitle("Add manually") {
            IconButton(onClick = { open = !open }) {
                val turn by animateFloatAsState(if (open) 45f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "turn")
                Icon(Icons.Rounded.Add, contentDescription = if (open) "Hide" else "Show", modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
        }
        AnimatedVisibility(open, enter = expandVertically(spring(Spring.DampingRatioLowBouncy)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = manualName, onValueChange = { manualName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = manualHost, onValueChange = { manualHost = it.trim() }, label = { Text("IP address or tvc:// link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = manualToken, onValueChange = { manualToken = it.trim() }, label = { Text("Token (shown on the TV)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = manualHost.isNotBlank(),
                    onClick = {
                        val parsed = MainViewModel.parsePayload(manualHost)
                        val device = parsed?.let { if (manualName.isBlank()) it else it.copy(name = manualName) }
                            ?: SavedDevice(manualName.ifBlank { manualHost }, manualHost, manualToken)
                        if (device.token.isNotBlank()) {
                            vm.addDevice(device)
                            manualHost = ""; manualToken = ""; manualName = ""
                        }
                    },
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun Radar(active: Boolean, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "t")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (active) {
                for (k in 0 until 3) {
                    val p = (t + k / 3f) % 1f
                    drawCircle(Tvc.Primary.copy(alpha = 0.6f * (1 - p)), radius = size.minDimension / 2 * p, style = Stroke(width = 3f))
                }
            } else {
                drawCircle(Tvc.OutlineVariant, radius = size.minDimension / 2.4f, style = Stroke(width = 3f))
            }
        }
        StarBlob(Star9, if (active) Tvc.Primary else Tvc.Outline, Modifier.size(26.dp), rotation = if (active) t * 360f else 0f)
    }
}

@Composable
fun RemoteScreen(vm: MainViewModel, state: DeviceUi, wide: Boolean) {
    val host = state.device.host
    val view = LocalView.current
    val adb = state.info?.adbConnected == true
    fun tap(command: Command) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        vm.fire(host, command)
    }

    LaunchedEffect(host, state.connected, adb) {
        if (!state.connected || !adb) return@LaunchedEffect
        while (true) {
            vm.refreshNowPlaying(host)
            delay(5_000)
        }
    }

    val primary: @Composable ColumnScope.() -> Unit = {
        if (!state.connected) OfflineBanner(state) { vm.refresh(host) }
        NowPlayingCard(state, adb, ::tap)
        DPad(onKey = { tap(Command.Key(it)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { tap(Command.Key(KeyCode.BACK)) }
            RoundKey(Icons.Rounded.Home, "Home") { tap(Command.Key(KeyCode.HOME)) }
            RoundKey(Icons.Rounded.Menu, "Recents") { tap(Command.Key(KeyCode.RECENTS)) }
            RoundKey(Icons.Rounded.PowerSettingsNew, "Power", Tvc.ErrorContainer, Tvc.OnErrorContainer) { tap(Command.Key(KeyCode.POWER_DIALOG)) }
        }
    }
    val secondary: @Composable ColumnScope.() -> Unit = {
        if (state.inputs.isNotEmpty()) Inputs(vm, state, ::tap)
        VolumeCard(state, onSet = { vm.setVolume(host, it) }, onTap = ::tap)
        TextCard { tap(Command.Text(it)) }
        if (state.apps.isNotEmpty()) {
            SectionCard {
                SectionTitle("Apps")
                AppGrid(state.apps, if (wide) 5 else 4) { tap(Command.LaunchApp(it.packageName)) }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = secondary)
            }
        } else {
            primary()
            secondary()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OfflineBanner(state: DeviceUi, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tvc.ErrorContainer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${state.device.name} is offline. Reconnecting...", color = Tvc.OnErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry", color = Tvc.OnErrorContainer) }
    }
}

@Composable
private fun NowPlayingCard(state: DeviceUi, adb: Boolean, tap: (Command) -> Unit) {
    val np = state.nowPlaying
    val playing = np?.state == PlaybackState.PLAYING
    val app = state.apps.firstOrNull { it.packageName == np?.packageName }
    var position by remember { mutableLongStateOf(np?.positionMs ?: 0L) }
    LaunchedEffect(np) {
        position = np?.positionMs ?: 0L
        while (playing) {
            delay(1_000)
            position += 1_000
        }
    }
    val duration = np?.durationMs?.takeIf { it > 0 }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Tvc.PrimaryContainer)) {
        SpinningStar(Star9, Tvc.Primary.copy(alpha = 0.18f), Modifier.size(220.dp).align(Alignment.TopEnd).offset(60.dp, (-60).dp), periodMs = if (playing) 9_000 else 30_000)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(app, 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (playing) "NOW PLAYING" else if (np?.state == PlaybackState.PAUSED) "PAUSED" else "MEDIA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Tvc.Primary,
                    )
                    Text(
                        np?.title ?: if (adb) "Nothing playing" else "Media controls",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Tvc.OnPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (np?.subtitle ?: app?.label ?: if (!adb) "Connect ADB on the TV for titles" else null)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Tvc.OnPrimaryContainer.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (duration != null) {
                WavyProgress((position.toFloat() / duration).coerceIn(0f, 1f), Modifier.fillMaxWidth().height(12.dp), animate = playing, track = Tvc.Primary.copy(alpha = 0.25f))
                Row {
                    Text(clock(position), style = MaterialTheme.typography.labelSmall, color = Tvc.OnPrimaryContainer, modifier = Modifier.weight(1f))
                    Text(clock(duration), style = MaterialTheme.typography.labelSmall, color = Tvc.OnPrimaryContainer)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                MediaKey(Icons.Rounded.SkipPrevious, "Previous") { tap(Command.Media(MediaAction.PREVIOUS)) }
                MediaKey(Icons.Rounded.FastRewind, "Rewind") { tap(Command.Media(MediaAction.REWIND)) }
                PlayButton(playing) { tap(Command.Media(MediaAction.PLAY_PAUSE)) }
                MediaKey(Icons.Rounded.FastForward, "Fast forward") { tap(Command.Media(MediaAction.FAST_FORWARD)) }
                MediaKey(Icons.Rounded.SkipNext, "Next") { tap(Command.Media(MediaAction.NEXT)) }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(if (playing) 22.dp else 36.dp, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "corner")
    val width by animateDpAsState(if (playing) 88.dp else 72.dp, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "width")
    Box(
        Modifier.size(width, 72.dp).clip(RoundedCornerShape(corner)).background(Tvc.Primary).springPress(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Tvc.OnPrimary, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun MediaKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).springPress(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Tvc.OnPrimaryContainer)
    }
}

@Composable
private fun RoundKey(icon: ImageVector, description: String, bg: Color = Tvc.ContainerHigh, fg: Color = Tvc.OnSurface, onClick: () -> Unit) {
    Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(bg).springPress(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = fg)
    }
}

@Composable
private fun DPad(onKey: (KeyCode) -> Unit) {
    var spin by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(spin, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "ok")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(Tvc.ContainerLow)) {
            ArrowZone(Icons.Rounded.KeyboardArrowUp, "Up", Modifier.align(Alignment.TopCenter)) { onKey(KeyCode.DPAD_UP) }
            ArrowZone(Icons.Rounded.KeyboardArrowDown, "Down", Modifier.align(Alignment.BottomCenter)) { onKey(KeyCode.DPAD_DOWN) }
            ArrowZone(Icons.Rounded.KeyboardArrowLeft, "Left", Modifier.align(Alignment.CenterStart)) { onKey(KeyCode.DPAD_LEFT) }
            ArrowZone(Icons.Rounded.KeyboardArrowRight, "Right", Modifier.align(Alignment.CenterEnd)) { onKey(KeyCode.DPAD_RIGHT) }
            Box(
                Modifier.fillMaxSize(0.42f).align(Alignment.Center).clip(CircleShape).springPress({ spin += 60f; onKey(KeyCode.DPAD_CENTER) }, pressedScale = 0.85f),
                contentAlignment = Alignment.Center,
            ) {
                StarBlob(Star12, Tvc.Primary, Modifier.fillMaxSize(), rotation = rotation)
                Text("OK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Tvc.OnPrimary)
            }
        }
    }
}

@Composable
private fun ArrowZone(icon: ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.padding(6.dp).size(84.dp).clip(CircleShape).springPress(onClick, pressedScale = 0.85f), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Tvc.OnSurfaceVariant, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun Inputs(vm: MainViewModel, state: DeviceUi, tap: (Command) -> Unit) {
    val host = state.device.host
    SectionCard {
        SectionTitle("Inputs")
        Text("Long-press an input to mark it as the Streamer.", style = MaterialTheme.typography.bodySmall, color = Tvc.Outline)
        state.inputs.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { input ->
                    val isStreamer = input.id == state.streamerInputId
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isStreamer) Tvc.SecondaryContainer else Tvc.ContainerHigh)
                            .combinedClickable(onClick = { tap(Command.SwitchInput(input.id)) }, onLongClick = { vm.setStreamerInput(host, if (isStreamer) null else input.id) })
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (isStreamer) "${input.label} ★" else input.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (state.streamerInputId != null) {
            FilledTonalButton(onClick = { vm.jumpToStreamer(host) }, modifier = Modifier.fillMaxWidth()) { Text("Switch TV to Streamer and control it") }
        }
    }
}

@Composable
private fun VolumeCard(state: DeviceUi, onSet: (Int) -> Unit, onTap: (Command) -> Unit) {
    val max = state.info?.maxVolume?.takeIf { it > 0 }
    val level = state.info?.volume
    var dragging by remember { mutableStateOf<Float?>(null) }
    SectionCard {
        SectionTitle("Volume") {
            if (max != null && level != null) Text("${(dragging?.roundToInt() ?: level)} / $max", style = MaterialTheme.typography.labelLarge, color = Tvc.Primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundKey(Icons.AutoMirrored.Rounded.VolumeOff, "Mute") { onTap(Command.Volume(VolumeAction.MUTE)) }
            if (max != null && level != null) {
                Slider(
                    value = dragging ?: level.toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { onSet(it.roundToInt()) }; dragging = null },
                    valueRange = 0f..max.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Tvc.Primary, activeTrackColor = Tvc.Primary, inactiveTrackColor = Tvc.ContainerHighest),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            RoundKey(Icons.Rounded.Remove, "Volume down") { onTap(Command.Volume(VolumeAction.DOWN)) }
            RoundKey(Icons.AutoMirrored.Rounded.VolumeUp, "Volume up", Tvc.Primary, Tvc.OnPrimary) { onTap(Command.Volume(VolumeAction.UP)) }
        }
    }
}

@Composable
private fun TextCard(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    SectionCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Type on TV") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = text.isNotEmpty(),
                onClick = { onSend(text); text = "" },
                shape = RoundedCornerShape(20.dp),
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier.height(56.dp),
            ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send text") }
        }
    }
}

@Composable
fun AppIcon(app: AppEntry?, size: Dp) {
    val bitmap = remember(app?.iconPng) {
        app?.iconPng?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = app?.label, modifier = Modifier.size(size).clip(RoundedCornerShape(size / 3.5f)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.5f)).background(Tvc.ContainerHighest), contentAlignment = Alignment.Center) {
            Text(app?.label?.take(1) ?: "▶", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Tvc.Primary)
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, columns: Int, onLaunch: (AppEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        apps.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { app ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).springPress({ onLaunch(app) }).padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIcon(app, 56.dp)
                        Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun EmptyRemote(onOpenDevices: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            MorphingStar(Star9, Tvc.PrimaryContainer, Modifier.fillMaxSize())
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = Tvc.OnPrimaryContainer, modifier = Modifier.size(56.dp))
        }
        Text("No devices yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Install the agent APK on the TV and the Streamer, open it, then scan the code it shows.", textAlign = TextAlign.Center, color = Tvc.OnSurfaceVariant)
        Button(onClick = onOpenDevices, modifier = Modifier.height(56.dp)) { Text("Add devices", fontWeight = FontWeight.Bold) }
    }
}
