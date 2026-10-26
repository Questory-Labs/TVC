package com.questorylabs.tvc.phone.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.questorylabs.tvc.phone.R
import kotlin.math.PI
import kotlin.math.sin

object Tvc {
    val Surface = Color(0xFF1A1414)
    val ContainerLowest = Color(0xFF120E0E)
    val ContainerLow = Color(0xFF221B1B)
    val Container = Color(0xFF2A2222)
    val ContainerHigh = Color(0xFF352C2C)
    val ContainerHighest = Color(0xFF403636)
    val OnSurface = Color(0xFFF1E4E1)
    val OnSurfaceVariant = Color(0xFFD8C2BC)
    val Outline = Color(0xFFA08C87)
    val OutlineVariant = Color(0xFF53433F)
    val Primary = Color(0xFFFF8A3D)
    val OnPrimary = Color(0xFF3A1400)
    val PrimaryContainer = Color(0xFF7A3000)
    val OnPrimaryContainer = Color(0xFFFFDBCB)
    val SecondaryContainer = Color(0xFF5C4033)
    val OnSecondaryContainer = Color(0xFFFFDBCB)
    val WarningContainer = Color(0xFF4A2A1C)
    val Tertiary = Color(0xFFBCCE9D)
    val TertiaryContainer = Color(0xFF3D4C2A)
    val OnTertiaryContainer = Color(0xFFD8EBB7)
    val OnTertiary = Color(0xFF283417)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFDAD6)
    val Error = Color(0xFFFFB4AB)
    val Online = Color(0xFF8CD9A0)
    val OnOnline = Color(0xFFA8E2B6)
    val OnlineContainer = Color(0xFF1E2A1F)
    val LogoOrange = Color(0xFFFF6B00)
    val LogoShade = Color(0xFFB84300)
    val LogoCharcoal = Color(0xFF4A3F3F)
}

private fun flex(weight: Int, width: Float) = Font(
    R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

val RobotoFlex = FontFamily(flex(400, 100f), flex(500, 100f), flex(600, 100f), flex(700, 100f))
val RobotoFlexWide = FontFamily(flex(500, 118f), flex(600, 118f), flex(700, 118f))

private fun points(vararg v: Float): List<Offset> = v.toList().chunked(2).map { Offset(it[0] / 120f, it[1] / 120f) }

val Star9 = points(
    60.0f, 10.0f, 74.4f, 20.5f, 92.1f, 21.7f, 96.4f, 39.0f, 109.2f, 51.3f, 101.4f, 67.3f, 103.3f, 85.0f, 87.0f, 92.2f, 77.1f, 107.0f,
    60.0f, 102.0f, 42.9f, 107.0f, 33.0f, 92.2f, 16.7f, 85.0f, 18.6f, 67.3f, 10.8f, 51.3f, 23.6f, 39.0f, 27.9f, 21.7f, 45.6f, 20.5f,
)

val Star12 = points(
    60.0f, 10.0f, 71.4f, 17.5f, 85.0f, 16.7f, 91.1f, 28.9f, 103.3f, 35.0f, 102.5f, 48.6f, 110.0f, 60.0f, 102.5f, 71.4f, 103.3f, 85.0f,
    91.1f, 91.1f, 85.0f, 103.3f, 71.4f, 102.5f, 60.0f, 110.0f, 48.6f, 102.5f, 35.0f, 103.3f, 28.9f, 91.1f, 16.7f, 85.0f, 17.5f, 71.4f,
    10.0f, 60.0f, 17.5f, 48.6f, 16.7f, 35.0f, 28.9f, 28.9f, 35.0f, 16.7f, 48.6f, 17.5f,
)

@Composable
fun StarBlob(points: List<Offset>, color: Color, modifier: Modifier = Modifier, rotation: Float = 0f, scale: Float = 1f) {
    Canvas(modifier.aspectRatio(1f)) {
        val path = Path().apply {
            points.forEachIndexed { i, p ->
                val x = p.x * size.width
                val y = p.y * size.height
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        rotate(rotation) {
            scale(scale) {
                drawPath(path, color, style = Fill)
                drawPath(path, color, style = Stroke(width = size.width / 12f, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun SpinningStar(points: List<Offset>, color: Color, modifier: Modifier = Modifier, periodMs: Int = 18_000) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "angle")
    StarBlob(points, color, modifier, rotation = angle)
}

@Composable
fun MorphingStar(points: List<Offset>, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "morph")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "angle")
    val scale by transition.animateFloat(1f, 0.75f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    StarBlob(points, color, modifier, rotation = angle, scale = scale)
}

@Composable
fun WavyProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Tvc.Primary, track: Color = Tvc.ContainerHighest, animate: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        val mid = size.height / 2f
        val stroke = size.height / 3f
        val end = size.width * progress.coerceIn(0f, 1f)
        drawLine(track, Offset(end + stroke * 2, mid), Offset(size.width, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        if (end <= 0f) return@Canvas
        val wavelength = size.height * 2.4f
        val amplitude = (size.height - stroke) / 2f
        val shift = if (animate) phase * wavelength else 0f
        val path = Path()
        var x = 0f
        while (x <= end) {
            val y = mid + amplitude * sin(((x + shift) / wavelength) * 2 * PI).toFloat()
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 2f
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val logoPolygons = listOf(
    listOf(0f, 0f, 42f, 0f, 0f, 70f) to false,
    listOf(47f, 0f, 102f, 0f, 102f, 157f, 47f, 157f) to false,
    listOf(107f, 0f, 205f, 0f, 190f, 157f) to false,
    listOf(262f, 0f, 300f, 0f, 228f, 157f, 190f, 157f) to true,
    listOf(300f, 0f, 395f, 0f, 375f, 44f, 335f, 44f, 303f, 113f, 345f, 113f, 325f, 157f, 228f, 157f) to false,
)

@Composable
fun TvcLogo(modifier: Modifier = Modifier) {
    val polygons = remember { logoPolygons.map { (v, shade) -> v.chunked(2).map { Offset(it[0], it[1]) } to shade } }
    Canvas(modifier.aspectRatio(395f / 157f)) {
        val s = size.width / 395f
        polygons.forEach { (pts, shade) ->
            val path = Path().apply {
                pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * s, p.y * s) else lineTo(p.x * s, p.y * s) }
                close()
            }
            drawPath(path, if (shade) Tvc.LogoShade else Tvc.LogoOrange)
        }
    }
}
