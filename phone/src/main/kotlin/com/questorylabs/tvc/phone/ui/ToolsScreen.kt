package com.questorylabs.tvc.phone.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.questorylabs.tvc.phone.DeviceUi
import com.questorylabs.tvc.phone.InstallState
import com.questorylabs.tvc.phone.MainViewModel
import com.questorylabs.tvc.shared.AppEntry
import com.questorylabs.tvc.shared.Command
import com.questorylabs.tvc.shared.ShellPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tool { MIRROR, DNS, MEMORY, APPS, INSTALL }

@Composable
fun ToolsScreen(vm: MainViewModel, state: DeviceUi, wide: Boolean, onDebloat: () -> Unit) {
    val adb = state.info?.adbConnected == true
    var tool by rememberSaveable { mutableStateOf<Tool?>(null) }
    var confirmReboot by remember { mutableStateOf(false) }
    val host = state.device.host

    val grid: @Composable () -> Unit = {
        val tiles = listOf<@Composable (Modifier) -> Unit>(
            { m -> ToolTile(Icons.Rounded.ScreenShare, "Mirror", "See the TV screen", m, enabled = adb, iconShape = CircleShape) { tool = Tool.MIRROR } },
            { m -> ToolTile(Icons.Rounded.CleaningServices, "Debloat", "${state.disabledPackages?.size ?: 0} disabled", m, enabled = adb, iconBg = Tvc.TertiaryContainer, iconFg = Tvc.OnTertiaryContainer, onClick = onDebloat) },
            { m -> ToolTile(Icons.Rounded.Security, "Private DNS", state.privateDns?.let { if (it.mode == "hostname") it.hostname ?: "On" else "Off" } ?: "Block ads", m, enabled = adb, iconBg = Tvc.SecondaryContainer, iconFg = Tvc.OnSecondaryContainer) { tool = Tool.DNS } },
            { m -> ToolTile(Icons.Rounded.Memory, "Memory", state.memory?.let { "${pct(it.totalBytes - it.availBytes, it.totalBytes)}% used" } ?: "Free up RAM", m, iconShape = CircleShape) { tool = Tool.MEMORY } },
            { m -> ToolTile(Icons.Rounded.Apps, "Apps", "${state.apps.size} installed", m, iconBg = Tvc.TertiaryContainer, iconFg = Tvc.OnTertiaryContainer) { tool = Tool.APPS } },
            { m -> ToolTile(Icons.Rounded.InstallMobile, "Install APK", "Send from phone", m, enabled = adb, iconBg = Tvc.SecondaryContainer, iconFg = Tvc.OnSecondaryContainer) { tool = Tool.INSTALL } },
            { m -> ToolTile(Icons.Rounded.RestartAlt, "Reboot", "Restart the TV", m, enabled = adb, bg = Tvc.ErrorContainer.copy(alpha = 0.4f), iconBg = Tvc.ErrorContainer, iconFg = Tvc.OnErrorContainer, iconShape = CircleShape) { confirmReboot = true } },
        )
        val columns = 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { it(Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    val detail: @Composable (Tool) -> Unit = { t ->
        when (t) {
            Tool.MIRROR -> MirrorPanel(vm, state)
            Tool.DNS -> DnsPanel(vm, state)
            Tool.MEMORY -> MemoryPanel(vm, state)
            Tool.APPS -> AppsPanel(vm, state)
            Tool.INSTALL -> InstallPanel(vm, state)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (state.connected && !adb) AdbRequired()
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { grid() }
                Box(Modifier.weight(1f)) {
                    AnimatedContent(tool, transitionSpec = { (fadeIn() + scaleIn(spring(Spring.DampingRatioLowBouncy), initialScale = 0.92f)).togetherWith(fadeOut()) }, label = "tool") { t ->
                        if (t != null) detail(t)
                        else SectionCard { Text("Pick a tool.", color = Tvc.OnSurfaceVariant) }
                    }
                }
            }
        } else {
            AnimatedContent(tool, transitionSpec = { (fadeIn() + scaleIn(spring(Spring.DampingRatioLowBouncy), initialScale = 0.92f)).togetherWith(fadeOut()) }, label = "tool") { t ->
                if (t == null) grid()
                else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { tool = null }) { Text("‹ All tools") }
                    detail(t)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            title = { Text("Reboot ${state.device.name}?") },
            text = { Text("The TV restarts now. The agent reconnects after boot.") },
            confirmButton = { TextButton(onClick = { vm.reboot(host); confirmReboot = false }) { Text("Reboot", color = Tvc.Error) } },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Cancel") } },
        )
    }
}

private fun pct(part: Long, total: Long) = if (total <= 0) 0 else (part * 100 / total).toInt()

@Composable
private fun MirrorPanel(vm: MainViewModel, state: DeviceUi) {
    val host = state.device.host
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun grab() {
        busy = true
        vm.screenshot(host)
            .onSuccess { bytes ->
                frame = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                error = if (frame == null) "Could not decode screenshot" else null
            }
            .onFailure { error = it.message ?: "Screenshot failed" }
        busy = false
    }

    LaunchedEffect(live, host) {
        while (live) {
            grab()
            if (error != null) live = false
            delay(800)
        }
    }

    SectionCard {
        SectionTitle("Mirror") { if (live) StatusChip("Live", Tvc.Error) }
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(Tvc.ContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            frame?.let { Image(it, contentDescription = "TV screen", modifier = Modifier.fillMaxSize()) }
            if (frame == null) {
                if (busy) MorphingStar(Star9, Tvc.Primary, Modifier.size(64.dp))
                else Text("Take a snapshot to see the TV screen", color = Tvc.Outline, style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = Tvc.Error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled = !busy && !live, onClick = { scope.launch { grab() } }, modifier = Modifier.weight(1f)) { Text("Snapshot") }
            Button(onClick = { live = !live }, modifier = Modifier.weight(1f)) { Text(if (live) "Stop live" else "Go live") }
        }
        Text("Live mode refreshes about once a second over ADB. DRM video shows black.", style = MaterialTheme.typography.bodySmall, color = Tvc.Outline)
    }
}

private val dnsPresets = listOf("dns.adguard-dns.com", "one.one.one.one", "dns.google")

@Composable
private fun DnsPanel(vm: MainViewModel, state: DeviceUi) {
    val host = state.device.host
    LaunchedEffect(host) { vm.refreshPrivateDns(host) }
    val dns = state.privateDns
    var hostname by remember(dns?.hostname) { mutableStateOf(dns?.hostname ?: dnsPresets.first()) }
    val valid = ShellPolicy.isHostname(hostname)
    val on = dns?.mode == "hostname"

    SectionCard {
        SectionTitle("Private DNS") {
            Switch(
                checked = on,
                enabled = dns != null && (on || valid),
                onCheckedChange = { vm.setPrivateDns(host, if (it) hostname else null) },
                colors = SwitchDefaults.colors(checkedTrackColor = Tvc.Primary, checkedThumbColor = Tvc.OnPrimary),
            )
        }
        Text(
            when {
                dns == null -> "Reading current setting..."
                on -> "Encrypted DNS via ${dns.hostname}"
                else -> "Mode: ${dns.mode}. Turn on to block ads and trackers network-wide on the TV."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Tvc.OnSurfaceVariant,
        )
        OutlinedTextField(
            value = hostname,
            onValueChange = { hostname = it.trim().lowercase() },
            label = { Text("Provider hostname") },
            isError = !valid,
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dnsPresets.forEach { preset ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (preset == hostname) Tvc.SecondaryContainer else Tvc.ContainerHigh)
                        .springPress({ hostname = preset })
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(preset.substringBefore('.').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
        }
        if (on && dns?.hostname != hostname) {
            Button(enabled = valid, onClick = { vm.setPrivateDns(host, hostname) }, modifier = Modifier.fillMaxWidth()) { Text("Apply") }
        }
    }
}

@Composable
private fun MemoryPanel(vm: MainViewModel, state: DeviceUi) {
    val host = state.device.host
    LaunchedEffect(host) { vm.refreshMemory(host) }
    val mem = state.memory
    val used = mem?.let { (it.totalBytes - it.availBytes).toFloat() / it.totalBytes.coerceAtLeast(1) } ?: 0f
    val sweep by animateFloatAsState(used, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow), label = "ring")

    SectionCard {
        SectionTitle("Memory")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension / 9
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(Tvc.ContainerHighest, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(if (used > 0.85f) Tvc.Error else Tvc.Primary, -90f, 360f * sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (mem != null) "${(sweep * 100).toInt()}%" else "--", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("used", style = MaterialTheme.typography.labelSmall, color = Tvc.Outline)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                mem?.let {
                    Text("${gb(it.availBytes)} free", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("of ${gb(it.totalBytes)}", style = MaterialTheme.typography.bodySmall, color = Tvc.OnSurfaceVariant)
                } ?: Text("Reading...", color = Tvc.Outline)
            }
        }
        Button(
            enabled = state.info?.adbConnected == true,
            onClick = { vm.freeMemory(host) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Free up memory", fontWeight = FontWeight.Bold) }
        Text("Stops background apps. Needs the ADB tier.", style = MaterialTheme.typography.bodySmall, color = Tvc.Outline)
    }
}

private fun gb(bytes: Long) = "%.1f GB".format(bytes / 1_073_741_824.0)

@Composable
private fun AppsPanel(vm: MainViewModel, state: DeviceUi) {
    val host = state.device.host
    val adb = state.info?.adbConnected == true
    var disabling by remember { mutableStateOf<AppEntry?>(null) }
    SectionCard {
        SectionTitle("App manager")
        if (state.apps.isEmpty()) Text("No apps reported yet.", color = Tvc.Outline)
        state.apps.forEach { app ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tvc.ContainerHigh).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(app, 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = Tvc.Outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { vm.fire(host, Command.LaunchApp(app.packageName)) }) { Text("Open") }
                if (adb) {
                    TextButton(onClick = { vm.forceStop(host, app.packageName) }) { Text("Stop") }
                    TextButton(onClick = { disabling = app }) { Text("Disable", color = Tvc.Error) }
                }
            }
        }
    }
    disabling?.let { app ->
        AlertDialog(
            onDismissRequest = { disabling = null },
            title = { Text("Disable ${app.label}?") },
            text = { Text("It disappears from the TV until you enable it again from Debloat.") },
            confirmButton = { TextButton(onClick = { vm.setDebloat(host, app.packageName, true); disabling = null }) { Text("Disable", color = Tvc.Error) } },
            dismissButton = { TextButton(onClick = { disabling = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InstallPanel(vm: MainViewModel, state: DeviceUi) {
    val host = state.device.host
    val install by vm.install.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.installApk(host, it) } }
    SectionCard {
        SectionTitle("Install APK")
        Text("Pick an APK on this phone. It streams to the TV and installs over ADB.", style = MaterialTheme.typography.bodyMedium, color = Tvc.OnSurfaceVariant)
        when (val s = install) {
            is InstallState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MorphingStar(Star9, Tvc.Primary, Modifier.size(40.dp))
                    Text("Installing ${s.name}...", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                WavyProgress(0.6f, Modifier.fillMaxWidth().height(12.dp))
            }
            is InstallState.Finished -> {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (s.ok) Tvc.OnlineContainer else Tvc.ErrorContainer).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${s.name}: ${s.message}", color = if (s.ok) Tvc.OnOnline else Tvc.OnErrorContainer, modifier = Modifier.weight(1f), maxLines = 3)
                    TextButton(onClick = { vm.clearInstall() }) { Text("OK") }
                }
            }
            InstallState.Idle -> {}
        }
        Button(
            enabled = install !is InstallState.Running && state.info?.adbConnected == true,
            onClick = { picker.launch(arrayOf("application/vnd.android.package-archive")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Choose APK", fontWeight = FontWeight.Bold) }
    }
}
