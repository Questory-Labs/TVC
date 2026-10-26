package com.questorylabs.tvc.phone.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.springPress(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
    haptic: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val view = LocalView.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled) {
            if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = Tvc.Container,
    shape: Shape = RoundedCornerShape(28.dp),
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(shape).background(color).animateContentSize(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Tvc.OnSurface, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun IconBlob(icon: ImageVector, bg: Color, fg: Color, size: Dp = 48.dp, shape: Shape = RoundedCornerShape(16.dp)) {
    Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun StatusChip(text: String, dot: Color, bg: Color = Tvc.ContainerHigh, fg: Color = Tvc.OnSurfaceVariant) {
    Row(
        Modifier.height(28.dp).clip(CircleShape).background(bg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun ToolTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    bg: Color = Tvc.Container,
    iconBg: Color = Tvc.PrimaryContainer,
    iconFg: Color = Tvc.OnPrimaryContainer,
    iconShape: Shape = RoundedCornerShape(16.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .springPress(onClick, enabled, pressedScale = 0.96f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBlob(icon, iconBg, iconFg, 44.dp, iconShape)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Tvc.OnSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tvc.OnSurfaceVariant, maxLines = 2)
    }
}

@Composable
fun AdbRequired(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tvc.WarningContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Tvc.Primary)
        Text("Needs the ADB tier. Open the agent on the TV and finish setup.", style = MaterialTheme.typography.bodyMedium, color = Tvc.OnPrimaryContainer)
    }
}
