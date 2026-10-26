package com.questorylabs.tvc.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private val colors = darkColorScheme(
    primary = Tvc.Primary,
    onPrimary = Tvc.OnPrimary,
    primaryContainer = Tvc.PrimaryContainer,
    onPrimaryContainer = Tvc.OnPrimaryContainer,
    secondary = Tvc.OnSecondaryContainer,
    secondaryContainer = Tvc.SecondaryContainer,
    onSecondaryContainer = Tvc.OnSecondaryContainer,
    tertiary = Tvc.Tertiary,
    onTertiary = Tvc.OnTertiary,
    tertiaryContainer = Tvc.TertiaryContainer,
    onTertiaryContainer = Tvc.OnTertiaryContainer,
    background = Tvc.Surface,
    onBackground = Tvc.OnSurface,
    surface = Tvc.Surface,
    onSurface = Tvc.OnSurface,
    surfaceVariant = Tvc.ContainerHighest,
    onSurfaceVariant = Tvc.OnSurfaceVariant,
    surfaceContainerLowest = Tvc.ContainerLowest,
    surfaceContainerLow = Tvc.ContainerLow,
    surfaceContainer = Tvc.Container,
    surfaceContainerHigh = Tvc.ContainerHigh,
    surfaceContainerHighest = Tvc.ContainerHighest,
    outline = Tvc.Outline,
    outlineVariant = Tvc.OutlineVariant,
    error = Tvc.Error,
    errorContainer = Tvc.ErrorContainer,
    onErrorContainer = Tvc.OnErrorContainer,
)

private fun TextStyle.flex() = copy(fontFamily = RobotoFlex)
private fun TextStyle.wide() = copy(fontFamily = RobotoFlexWide)

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.wide(),
        displayMedium = displayMedium.wide(),
        displaySmall = displaySmall.wide(),
        headlineLarge = headlineLarge.wide(),
        headlineMedium = headlineMedium.wide(),
        headlineSmall = headlineSmall.wide(),
        titleLarge = titleLarge.flex(),
        titleMedium = titleMedium.flex(),
        titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.flex(),
        bodyMedium = bodyMedium.flex(),
        bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(),
        labelMedium = labelMedium.flex(),
        labelSmall = labelSmall.flex(),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun TvcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
