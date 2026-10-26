package com.questorylabs.tvc.agent.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.questorylabs.tvc.agent.ActivityKind
import com.questorylabs.tvc.agent.AgentApp
import com.questorylabs.tvc.agent.AgentService
import com.questorylabs.tvc.agent.AgentState
import com.questorylabs.tvc.agent.control.ControlAccessibilityService
import com.questorylabs.tvc.shared.Protocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentService.start(this)
        val app = application as AgentApp
        setContent {
            MaterialTheme(colorScheme = tvColors) {
                Surface(modifier = Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(containerColor = Tvc.Surface, contentColor = Tvc.OnSurface)) {
                    AgentScreen(app)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AgentState.accessibilityEnabled.value = ControlAccessibilityService.instance != null
        AgentState.adbConnected.value = (application as AgentApp).adb.isConnected
    }
}

private val tvColors = darkColorScheme(
    primary = Tvc.Primary,
    onPrimary = Tvc.OnPrimary,
    primaryContainer = Tvc.PrimaryContainer,
    onPrimaryContainer = Tvc.OnPrimaryContainer,
    secondaryContainer = Tvc.SecondaryContainer,
    onSecondaryContainer = Tvc.OnSecondaryContainer,
    tertiary = Tvc.Tertiary,
    tertiaryContainer = Tvc.TertiaryContainer,
    onTertiaryContainer = Tvc.OnTertiaryContainer,
    background = Tvc.Surface,
    onBackground = Tvc.OnSurface,
    surface = Tvc.Surface,
    onSurface = Tvc.OnSurface,
    surfaceVariant = Tvc.ContainerHigh,
    onSurfaceVariant = Tvc.OnSurfaceVariant,
    error = Tvc.Error,
    errorContainer = Tvc.ErrorContainer,
    onErrorContainer = Tvc.OnErrorContainer,
    border = Tvc.Primary,
    borderVariant = Tvc.OutlineVariant,
)

private fun type(size: Int, weight: FontWeight = FontWeight.Normal, wide: Boolean = false) =
    TextStyle(fontFamily = if (wide) RobotoFlexWide else RobotoFlex, fontSize = size.sp, fontWeight = weight)

private enum class Page { HOME, SETUP }

@Composable
private fun AgentScreen(app: AgentApp) {
    var page by remember { mutableStateOf(Page.HOME) }
    BackHandler(enabled = page != Page.HOME) { page = Page.HOME }
    Box(Modifier.fillMaxSize().background(Tvc.Surface)) {
        SpinningStar(Star9, Tvc.Primary.copy(alpha = 0.06f), Modifier.size(620.dp).align(Alignment.BottomEnd).offset(200.dp, 260.dp))
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState == Page.SETUP) 1 else -1
                (slideInHorizontally(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 3 * dir } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally(tween(250)) { -it / 3 * dir } + fadeOut(tween(200)))
            },
            label = "page",
        ) { target ->
            when (target) {
                Page.HOME -> HomePage(app) { page = Page.SETUP }
                Page.SETUP -> SetupPage(app) { page = Page.HOME }
            }
        }
    }
}

@Composable
private fun HomePage(app: AgentApp, onSetup: () -> Unit) {
    val context = LocalContext.current
    val running by AgentState.serverRunning.collectAsStateWithLifecycle()
    val serverError by AgentState.serverError.collectAsStateWithLifecycle()
    val address by AgentState.localAddress.collectAsStateWithLifecycle()
    val a11y by AgentState.accessibilityEnabled.collectAsStateWithLifecycle()
    val adb by AgentState.adbConnected.collectAsStateWithLifecycle()
    val clients by AgentState.clients.collectAsStateWithLifecycle()
    val activity by AgentState.activity.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf(app.tokenStore.token) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val payload = "tvc://${address ?: "0.0.0.0"}:${Protocol.PORT}?token=$token&name=${android.net.Uri.encode(Build.MODEL)}"
    val qr = remember(payload) { qrBitmap(payload, 320) }

    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvcLogo(Modifier.width(96.dp))
            Box(Modifier.padding(horizontal = 20.dp).width(1.dp).height(32.dp).background(Tvc.OutlineVariant))
            Text("Companion agent", style = type(30, FontWeight.SemiBold, wide = true), color = Tvc.OnSurface, modifier = Modifier.weight(1f))
            StatusPill(
                text = if (running) "Running · port ${Protocol.PORT}" else serverError ?: "Starting",
                ok = running,
            )
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TierTile(
                        icon = Icons.Rounded.Accessibility,
                        title = "Navigation",
                        body = if (a11y) "Accessibility service on. Keys and text work." else "Off. Select to open accessibility settings.",
                        on = a11y,
                        container = Tvc.PrimaryContainer,
                        content = Tvc.OnPrimaryContainer,
                        badge = Tvc.Primary,
                        onBadge = Tvc.OnPrimary,
                        modifier = Modifier.weight(1f).focusRequester(first),
                        badgeShape = RoundedCornerShape(18.dp),
                    ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    TierTile(
                        icon = Icons.Rounded.Terminal,
                        title = "ADB tier",
                        body = if (adb) "Connected. Debloat, mirror, installs, raw keys." else "Not connected. Select to set it up.",
                        on = adb,
                        container = Tvc.TertiaryContainer,
                        content = Tvc.OnTertiaryContainer,
                        badge = Tvc.Tertiary,
                        onBadge = Tvc.OnTertiary,
                        modifier = Modifier.weight(1f),
                        badgeShape = CircleShape,
                        onClick = onSetup,
                    )
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Panel("Paired phones", Modifier.weight(1f)) {
                        if (clients.isEmpty()) {
                            Text("No phone connected. Scan the code to pair.", style = type(16), color = Tvc.OnSurfaceVariant)
                        }
                        clients.take(3).forEachIndexed { i, client ->
                            Row(
                                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(22.dp)).background(Tvc.ContainerHigh).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                IconBadge(Icons.Rounded.Smartphone, Tvc.SecondaryContainer, Tvc.OnSecondaryContainer, 44.dp, if (i % 2 == 0) RoundedCornerShape(14.dp) else CircleShape)
                                Column(Modifier.weight(1f)) {
                                    Text(client.name, style = type(18, FontWeight.SemiBold), color = Tvc.OnSurface)
                                    Text("Connected ${relative(client.connectedAtMs)}", style = type(14), color = Tvc.OnSurfaceVariant)
                                }
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Tvc.Online))
                            }
                        }
                    }
                    Panel("Activity", Modifier.weight(1f)) {
                        if (activity.isEmpty()) {
                            Text("Commands from your phone show up here.", style = type(16), color = Tvc.OnSurfaceVariant)
                        }
                        activity.take(4).forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                IconBadge(activityIcon(entry.kind), Tvc.ContainerHigh, Tvc.Primary, 36.dp, RoundedCornerShape(12.dp))
                                Text(entry.text, style = type(17), color = Tvc.OnSurface, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(relative(entry.atMs), style = type(14), color = Tvc.Outline)
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.width(360.dp).fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(Tvc.Container).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Pair a phone", style = type(22, FontWeight.Bold), color = Tvc.OnSurface, modifier = Modifier.align(Alignment.Start))
                Box(Modifier.size(232.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(12.dp)) {
                    qr?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "Pairing QR code", modifier = Modifier.fillMaxSize()) }
                }
                Text(token, style = type(18, FontWeight.Bold), color = Tvc.Primary)
                Text("${Build.MODEL} · ${address ?: "no network"}", style = type(15), color = Tvc.Outline, textAlign = TextAlign.Center)
                PillButton("New token", Icons.Rounded.Refresh, Tvc.ContainerHigh, Tvc.OnSurface) { token = app.tokenStore.regenerate() }
            }
        }
    }
}

@Composable
private fun SetupPage(app: AgentApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adb by AgentState.adbConnected.collectAsStateWithLifecycle()
    val adbMessage by AgentState.adbMessage.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(if (adb) 3 else 0) }
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val next = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { next.requestFocus() } }
    LaunchedEffect(adb) { if (adb) step = 3 }

    val labels = listOf("Developer options", "Wireless debugging", "Pairing code", "Done")

    Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to agent home", onBack)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Unlock the ADB tier", style = type(44, FontWeight.SemiBold, wide = true), color = Tvc.OnSurface)
                Text("Debloat, mirror, installs and raw keys. One-time pairing.", style = type(18), color = Tvc.OnSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { k, label ->
                    val current = k == step
                    val done = k < step || (k == 3 && adb)
                    val radius by animateFloatAsState(if (current) 12f else 18f, spring(Spring.DampingRatioMediumBouncy), label = "r")
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(if (current) Tvc.SecondaryContainer else Color.Transparent).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(radius.dp)).background(if (done) Tvc.Tertiary else if (current) Tvc.Primary else Tvc.ContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = Tvc.OnTertiary, modifier = Modifier.size(20.dp))
                            else Text("${k + 1}", style = type(16, FontWeight.Bold), color = if (current) Tvc.OnPrimary else Tvc.Outline)
                        }
                        Text(label, style = type(18, if (current) FontWeight.Bold else FontWeight.Medium), color = if (k <= step) Tvc.OnSurface else Tvc.Outline)
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(36.dp)).background(Tvc.Container)) {
            if (busy) MorphingStar(Star9, Tvc.Primary.copy(alpha = 0.14f), Modifier.size(300.dp).align(Alignment.TopEnd).offset(70.dp, (-70).dp))
            else SpinningStar(Star9, Tvc.Primary.copy(alpha = 0.14f), Modifier.size(300.dp).align(Alignment.TopEnd).offset(70.dp, (-70).dp), periodMs = 14_000)
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 4 } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it / 4 } + fadeOut())
                },
                label = "step",
            ) { s ->
                Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("STEP ${s + 1} OF 4", style = type(16, FontWeight.Bold).copy(letterSpacing = 1.5.sp), color = Tvc.Primary)
                    when (s) {
                        0 -> {
                            StepText("Turn on developer options", "Open Settings › System › About and select Android TV OS build seven times.")
                            Spacer(Modifier.weight(1f))
                            StepButtons(next, onBack = null, nextLabel = "Next", onNext = { step = 1 }) {
                                PillButton("Open About", Icons.Rounded.Settings, Tvc.ContainerHigh, Tvc.OnSurface) {
                                    context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                                }
                            }
                        }
                        1 -> {
                            StepText("Open wireless debugging", "Turn it on, then choose Pair device with pairing code. Leave that dialog open and come back here.")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("Settings", "System", "Developer options").forEach {
                                    Crumb(it, false)
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Tvc.Outline)
                                }
                                Crumb("Wireless debugging", true)
                            }
                            Spacer(Modifier.weight(1f))
                            StepButtons(next, onBack = { step = 0 }, nextLabel = "Next", onNext = { step = 2 }) {
                                PillButton("Developer options", Icons.Rounded.Settings, Tvc.ContainerHigh, Tvc.OnSurface) {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                }
                            }
                        }
                        2 -> {
                            StepText("Enter the pairing code", "Type the port and 6-digit code from the pairing dialog. You can type from the phone app's keyboard.")
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TvField("Pairing port", pairPort, 180.dp) { pairPort = it.filter(Char::isDigit).take(5) }
                                TvField("Pairing code", pairCode, 240.dp) { pairCode = it.filter(Char::isDigit).take(6) }
                            }
                            adbMessage?.let { Text(it, style = type(17), color = Tvc.OnSurfaceVariant) }
                            Spacer(Modifier.weight(1f))
                            StepButtons(
                                next,
                                onBack = { step = 1 },
                                nextLabel = if (busy) "Pairing…" else "Pair",
                                enabled = !busy && pairPort.isNotEmpty() && pairCode.length == 6,
                                onNext = {
                                    busy = true
                                    AgentState.adbMessage.value = "Pairing..."
                                    scope.launch {
                                        val paired = app.adb.pairWith(pairPort.toInt(), pairCode).isSuccess
                                        if (paired) app.adb.connectAuto()
                                        busy = false
                                        if (paired) step = 3
                                    }
                                },
                            )
                        }
                        else -> {
                            StepText(
                                if (adb) "All set" else "Connect",
                                if (adb) "Debloat, mirror, APK installs and raw keys are now available from your phone and tablet." else "Paired. Connect now, or enter the port shown under Wireless debugging.",
                            )
                            if (adb) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(24.dp)).background(Tvc.TertiaryContainer).padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Icon(Icons.Rounded.Verified, contentDescription = null, tint = Tvc.OnTertiaryContainer, modifier = Modifier.size(36.dp))
                                    Text("ADB tier active. Reconnects automatically after sleep.", style = type(20), color = Tvc.OnTertiaryContainer)
                                }
                            } else {
                                TvField("Connect port", connectPort, 180.dp) { connectPort = it.filter(Char::isDigit).take(5) }
                                adbMessage?.let { Text(it, style = type(17), color = Tvc.OnSurfaceVariant) }
                            }
                            Spacer(Modifier.weight(1f))
                            if (adb) {
                                StepButtons(next, onBack = { step = 2 }, nextLabel = "Finish", onNext = onBack)
                            } else {
                                StepButtons(
                                    next,
                                    onBack = { step = 2 },
                                    nextLabel = if (busy) "Connecting…" else "Connect",
                                    enabled = !busy,
                                    onNext = {
                                        busy = true
                                        AgentState.adbMessage.value = "Connecting..."
                                        scope.launch {
                                            val port = connectPort.toIntOrNull()
                                            if (port != null) app.adb.connectPort(port) else app.adb.connectAuto()
                                            busy = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepText(title: String, body: String) {
    Text(title, style = type(34, FontWeight.SemiBold, wide = true), color = Tvc.OnSurface)
    Text(body, style = type(20).copy(lineHeight = 30.sp), color = Tvc.OnSurfaceVariant, modifier = Modifier.width(640.dp))
}

@Composable
private fun StepButtons(
    focus: FocusRequester,
    onBack: (() -> Unit)?,
    nextLabel: String,
    enabled: Boolean = true,
    onNext: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) PillButton("Back", null, Tvc.ContainerHigh, Tvc.OnSurface, onClick = onBack)
        PillButton(nextLabel, Icons.AutoMirrored.Rounded.ArrowForward, Tvc.Primary, Tvc.OnPrimary, Modifier.focusRequester(focus), enabled, onNext)
        extra()
    }
}

@Composable
private fun Crumb(text: String, active: Boolean) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (active) Tvc.PrimaryContainer else Tvc.ContainerHigh).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = type(18, if (active) FontWeight.Bold else FontWeight.Normal), color = if (active) Tvc.OnPrimaryContainer else Tvc.OnSurface) }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(if (ok) Tvc.OnlineContainer else Tvc.WarningContainer).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Pulse(if (ok) Tvc.Online else Tvc.Primary)
        Text(text, style = type(18, FontWeight.Bold), color = if (ok) Tvc.OnOnline else Tvc.OnPrimaryContainer)
    }
}

@Composable
private fun Pulse(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000)), label = "t")
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((10 + 12 * t).dp).alpha(0.55f * (1 - t)).clip(CircleShape).background(color))
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun TierTile(
    icon: ImageVector,
    title: String,
    body: String,
    on: Boolean,
    container: Color,
    content: Color,
    badge: Color,
    onBadge: Color,
    modifier: Modifier,
    badgeShape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    FocusSurface(onClick, modifier.height(200.dp), container, RoundedCornerShape(32.dp)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, badge, onBadge, 56.dp, badgeShape)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (on) Tvc.Online else Tvc.Error))
            }
            Text(title, style = type(24, FontWeight.Bold), color = content)
            Text(body, style = type(17), color = content.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(Tvc.ContainerLow).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = type(20, FontWeight.Bold), color = Tvc.OnSurface)
        content()
    }
}

@Composable
private fun IconBadge(icon: ImageVector, bg: Color, fg: Color, size: Dp, shape: androidx.compose.ui.graphics.Shape) {
    Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun FocusSurface(onClick: () -> Unit, modifier: Modifier, container: Color, shape: androidx.compose.ui.graphics.Shape, enabled: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape, pressedShape = RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = container, focusedContainerColor = container, pressedContainerColor = container, disabledContainerColor = container.copy(alpha = 0.4f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f, pressedScale = 0.97f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Tvc.Primary), shape = shape)),
        content = content,
    )
}

@Composable
private fun PillButton(
    text: String,
    icon: ImageVector?,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FocusSurface(onClick, modifier.height(64.dp).alpha(if (enabled) 1f else 0.5f), container, CircleShape, enabled) {
        Row(Modifier.padding(horizontal = 32.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, style = type(20, FontWeight.Bold), color = content)
            if (icon != null) Icon(icon, contentDescription = null, tint = content)
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FocusSurface(onClick, Modifier.size(56.dp), Tvc.Container, CircleShape) {
        Icon(icon, contentDescription = description, tint = Tvc.OnSurface, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TvField(label: String, value: String, width: Dp, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = type(15, FontWeight.Medium), color = Tvc.OnSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = type(36, FontWeight.Bold).copy(color = Tvc.OnSurface, letterSpacing = 4.sp),
            cursorBrush = SolidColor(Tvc.Primary),
            modifier = Modifier
                .width(width)
                .height(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Tvc.ContainerHigh)
                .border(BorderStroke(2.dp, Tvc.OutlineVariant), RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        )
    }
}

private fun activityIcon(kind: ActivityKind): ImageVector = when (kind) {
    ActivityKind.INPUT -> Icons.Rounded.Input
    ActivityKind.APP -> Icons.Rounded.Apps
    ActivityKind.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
    ActivityKind.MEDIA -> Icons.Rounded.PlayArrow
    ActivityKind.TEXT -> Icons.Rounded.Keyboard
    ActivityKind.SCENE -> Icons.Rounded.AutoAwesome
    ActivityKind.SYSTEM -> Icons.Rounded.Settings
    ActivityKind.PACKAGE -> Icons.Rounded.Inventory2
}

@Composable
private fun relative(atMs: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return if (now - atMs < 60_000) "now" else DateUtils.getRelativeTimeSpanString(atMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

private fun qrBitmap(text: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    bitmap
}.getOrNull()
