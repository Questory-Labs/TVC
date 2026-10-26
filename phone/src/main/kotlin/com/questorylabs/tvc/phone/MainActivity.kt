package com.questorylabs.tvc.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.questorylabs.tvc.phone.ui.DebloatScreen
import com.questorylabs.tvc.phone.ui.DevicesScreen
import com.questorylabs.tvc.phone.ui.EmptyRemote
import com.questorylabs.tvc.phone.ui.RemoteScreen
import com.questorylabs.tvc.phone.ui.ScenesScreen
import com.questorylabs.tvc.phone.ui.ToolsScreen
import com.questorylabs.tvc.phone.ui.Tvc
import com.questorylabs.tvc.phone.ui.TvcLogo
import com.questorylabs.tvc.phone.ui.TvcTheme
import com.questorylabs.tvc.phone.ui.springPress

private enum class Screen(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    REMOTE("Remote", Icons.Outlined.SettingsRemote, Icons.Rounded.SettingsRemote),
    SCENES("Scenes", Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    TOOLS("Tools", Icons.Outlined.Handyman, Icons.Rounded.Handyman),
    DEVICES("Devices", Icons.Outlined.Devices, Icons.Rounded.Devices),
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            TvcTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Tvc.Surface) { App(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val device = intent.dataString?.let { MainViewModel.parsePayload(it) } ?: return
        vm.incomingLink.value = device
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val selectedHost by vm.selectedHost.collectAsStateWithLifecycle()
    val incoming by vm.incomingLink.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.REMOTE) }
    var debloat by rememberSaveable { mutableStateOf(false) }
    val selected = selectedHost?.let { states[it] }

    BackHandler(enabled = debloat || screen != Screen.REMOTE) {
        if (debloat) debloat = false else screen = Screen.REMOTE
    }

    incoming?.let { device ->
        AlertDialog(
            onDismissRequest = { vm.incomingLink.value = null },
            title = { Text("Add ${device.name}?") },
            text = { Text("${device.host}:${device.port}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.addDevice(device)
                    vm.selectedHost.value = device.host
                    vm.incomingLink.value = null
                    screen = Screen.REMOTE
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { vm.incomingLink.value = null }) { Text("Cancel") } },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val content: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                if (screen != Screen.DEVICES && !debloat) {
                    TopBar(wide, devices.map { it.host to (states[it.host]?.device?.name ?: it.name) }, selectedHost, states.mapValues { it.value.connected }) {
                        vm.selectedHost.value = it
                    }
                }
                AnimatedContent(
                    targetState = if (debloat) null else screen,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), initialScale = 0.94f))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "screen",
                    modifier = Modifier.weight(1f),
                ) { target ->
                    when {
                        target == null -> selected?.let { DebloatScreen(vm, it) { debloat = false } } ?: run { debloat = false }
                        target == Screen.DEVICES -> DevicesScreen(vm, wide)
                        selected == null -> EmptyRemote { screen = Screen.DEVICES }
                        target == Screen.REMOTE -> RemoteScreen(vm, selected, wide)
                        target == Screen.SCENES -> ScenesScreen(vm, selected, wide)
                        else -> ToolsScreen(vm, selected, wide) { debloat = true }
                    }
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = Tvc.ContainerLow, header = {
                    TvcLogo(Modifier.padding(top = 12.dp, bottom = 24.dp).width(56.dp))
                }) {
                    Spacer(Modifier.weight(1f))
                    Screen.entries.forEach { s ->
                        NavigationRailItem(
                            selected = screen == s && !debloat,
                            onClick = { debloat = false; screen = s },
                            icon = { Icon(if (screen == s) s.selectedIcon else s.icon, contentDescription = null) },
                            label = { Text(s.label) },
                            colors = NavigationRailItemDefaults.colors(indicatorColor = Tvc.SecondaryContainer, selectedIconColor = Tvc.OnSecondaryContainer),
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                content(Modifier.weight(1f).fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))
            }
        } else {
            Scaffold(
                containerColor = Tvc.Surface,
                bottomBar = {
                    NavigationBar(containerColor = Tvc.ContainerLow) {
                        Screen.entries.forEach { s ->
                            NavigationBarItem(
                                selected = screen == s && !debloat,
                                onClick = { debloat = false; screen = s },
                                icon = { Icon(if (screen == s) s.selectedIcon else s.icon, contentDescription = null) },
                                label = { Text(s.label) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Tvc.SecondaryContainer, selectedIconColor = Tvc.OnSecondaryContainer),
                            )
                        }
                    }
                },
            ) { padding -> content(Modifier.padding(padding).fillMaxSize()) }
        }
    }
}

@Composable
private fun TopBar(wide: Boolean, devices: List<Pair<String, String>>, selected: String?, online: Map<String, Boolean>, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!wide) TvcLogo(Modifier.width(52.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).clip(CircleShape).background(Tvc.ContainerLow).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (devices.isEmpty()) {
                Text("No devices", style = MaterialTheme.typography.labelLarge, color = Tvc.Outline, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
            devices.forEach { (host, name) ->
                val active = host == selected
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (active) Tvc.Primary else Tvc.ContainerLow)
                        .springPress({ onSelect(host) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (online[host] == true) Tvc.Online else Tvc.Outline))
                    Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (active) Tvc.OnPrimary else Tvc.OnSurface, maxLines = 1)
                }
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
