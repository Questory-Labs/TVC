package com.questorylabs.tvc.agent.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.questorylabs.tvc.agent.AgentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class ToastKind { PHONE, INPUT, INFO }

data class OverlayToast(val id: Long, val kind: ToastKind, val title: String, val subtitle: String?)

data class OverlayProgress(val title: String, val subtitle: String?, val progress: Float)

data class OverlayVolume(val level: Int, val max: Int)

object OverlayController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ids = AtomicLong(1)
    private var progressJob: Job? = null
    private var volumeJob: Job? = null
    private var sleepJob: Job? = null

    val toasts = MutableStateFlow<List<OverlayToast>>(emptyList())
    val progress = MutableStateFlow<OverlayProgress?>(null)
    val volume = MutableStateFlow<OverlayVolume?>(null)
    val sleepVisible = MutableStateFlow(false)

    fun toast(kind: ToastKind, title: String, subtitle: String? = null) {
        val toast = OverlayToast(ids.getAndIncrement(), kind, title, subtitle)
        toasts.update { (it + toast).takeLast(MAX_TOASTS) }
        scope.launch {
            delay(TOAST_MS)
            toasts.update { list -> list.filterNot { it.id == toast.id } }
        }
    }

    fun progress(title: String, subtitle: String?, value: Float?) {
        if (value == null) {
            toast(ToastKind.INFO, title, subtitle)
            return
        }
        progress.value = OverlayProgress(title, subtitle, value.coerceIn(0f, 1f))
        progressJob?.cancel()
        progressJob = scope.launch {
            delay(if (value >= 1f) DONE_MS else IDLE_MS)
            progress.value = null
        }
    }

    fun volume(level: Int, max: Int) {
        volume.value = OverlayVolume(level, max)
        volumeJob?.cancel()
        volumeJob = scope.launch {
            delay(VOLUME_MS)
            volume.value = null
        }
    }

    fun flashSleep() {
        sleepVisible.value = true
        sleepJob?.cancel()
        sleepJob = scope.launch {
            delay(TOAST_MS)
            sleepVisible.value = false
        }
    }

    private const val MAX_TOASTS = 3
    private const val TOAST_MS = 4_000L
    private const val DONE_MS = 2_500L
    private const val IDLE_MS = 15_000L
    private const val VOLUME_MS = 2_500L
}

class OverlayWindow(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private var view: ComposeView? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun attach() {
        if (view != null) return
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
            setContent { OverlayContent() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { context.getSystemService(WindowManager::class.java).addView(composeView, params) }
            .onSuccess { view = composeView }
    }

    fun detach() {
        val v = view ?: return
        registry.currentState = Lifecycle.State.DESTROYED
        runCatching { context.getSystemService(WindowManager::class.java).removeView(v) }
        view = null
    }
}

@Composable
private fun OverlayContent() {
    val toasts by OverlayController.toasts.collectAsStateWithLifecycle()
    val progress by OverlayController.progress.collectAsStateWithLifecycle()
    val volume by OverlayController.volume.collectAsStateWithLifecycle()
    val sleepFlash by OverlayController.sleepVisible.collectAsStateWithLifecycle()
    val sleepAt by AgentState.sleepAtElapsedMs.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sleepAt) {
        while (sleepAt != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val remainingMin = sleepAt?.let { ((it - now).coerceAtLeast(0) + 59_999) / 60_000 }
    val showSleep = remainingMin != null && (sleepFlash || remainingMin <= 5)

    Box(Modifier.fillMaxSize().padding(40.dp)) {
        Column(Modifier.align(Alignment.TopEnd).width(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            toasts.forEach { toast ->
                key(toast.id) {
                    AnimatedVisibility(
                        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                        enter = slideInHorizontally(spring(Spring.DampingRatioMediumBouncy)) { it / 2 } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut(),
                    ) { ToastCard(toast) }
                }
            }
        }

        AnimatedVisibility(
            visible = showSleep,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Row(
                Modifier.height(52.dp).clip(CircleShape).background(Tvc.PrimaryContainer).padding(start = 14.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = Tvc.OnPrimaryContainer)
                Text("Sleep in ${remainingMin ?: 0} min", style = body(18, FontWeight.SemiBold), color = Tvc.OnPrimaryContainer)
            }
        }

        AnimatedVisibility(
            visible = progress != null,
            modifier = Modifier.align(Alignment.BottomStart),
            enter = slideInVertically(spring(Spring.DampingRatioLowBouncy)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val p = progress ?: OverlayProgress("", null, 0f)
            val animated by animateFloatAsState(p.progress, spring(stiffness = Spring.StiffnessLow), label = "progress")
            Column(
                Modifier.width(520.dp).clip(RoundedCornerShape(32.dp)).background(Tvc.Container.copy(alpha = 0.94f)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        if (p.progress < 1f) MorphingStar(Star9, Tvc.Primary, Modifier.size(56.dp)) else StarBlob(Star9, Tvc.Primary, Modifier.size(56.dp))
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Tvc.OnPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = body(22, FontWeight.Bold), color = Tvc.OnSurface)
                        p.subtitle?.let { Text(it, style = body(16, FontWeight.Normal), color = Tvc.OnSurfaceVariant) }
                    }
                    Text("${(p.progress * 100).toInt()}%", style = body(18, FontWeight.Bold), color = Tvc.Primary)
                }
                WavyProgress(animated, Modifier.fillMaxWidth().height(12.dp), animate = p.progress < 1f)
            }
        }

        AnimatedVisibility(
            visible = volume != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            val v = volume ?: OverlayVolume(0, 1)
            val fraction by animateFloatAsState(v.level.toFloat() / v.max.coerceAtLeast(1), spring(Spring.DampingRatioLowBouncy), label = "vol")
            Column(
                Modifier.width(96.dp).height(340.dp).clip(RoundedCornerShape(40.dp)).background(Tvc.Container.copy(alpha = 0.94f)).padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("${v.level}", style = body(20, FontWeight.Bold), color = Tvc.OnSurface)
                Box(Modifier.weight(1f).width(52.dp).clip(RoundedCornerShape(22.dp)).background(Tvc.ContainerHighest), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(22.dp)).background(Tvc.Primary))
                }
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = Tvc.OnPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ToastCard(toast: OverlayToast) {
    val (icon, bg, fg) = when (toast.kind) {
        ToastKind.PHONE -> Triple(Icons.Rounded.Smartphone, Tvc.SecondaryContainer, Tvc.OnSecondaryContainer)
        ToastKind.INPUT -> Triple(Icons.Rounded.Input, Tvc.TertiaryContainer, Tvc.OnTertiaryContainer)
        ToastKind.INFO -> Triple(Icons.Rounded.Info, Tvc.PrimaryContainer, Tvc.OnPrimaryContainer)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tvc.Container.copy(alpha = 0.92f)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconChip(icon, bg, fg)
        Column {
            Text(toast.title, style = body(18, FontWeight.SemiBold), color = Tvc.OnSurface)
            toast.subtitle?.let { Text(it, style = body(14, FontWeight.Normal), color = Tvc.OnSurfaceVariant) }
        }
    }
}

@Composable
private fun IconChip(icon: ImageVector, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
    }
}

private fun body(size: Int, weight: FontWeight) = TextStyle(fontFamily = RobotoFlex, fontSize = size.sp, fontWeight = weight)
