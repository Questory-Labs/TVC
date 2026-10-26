package com.questorylabs.tvc.agent.control

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Base64
import android.view.KeyEvent
import com.questorylabs.tvc.shared.AppEntry
import com.questorylabs.tvc.shared.MediaAction
import com.questorylabs.tvc.shared.TvInput
import com.questorylabs.tvc.shared.VolumeAction
import java.io.ByteArrayOutputStream

class InputSwitcher(private val context: Context) {
    private val tvInputManager: TvInputManager? = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    fun list(): List<TvInput> {
        val inputs = runCatching { tvInputManager?.tvInputList }.getOrNull().orEmpty()
        return inputs
            .filter { it.isPassthroughInput }
            .map { TvInput(it.id, it.loadLabel(context)?.toString() ?: it.id, hdmiPort(it)) }
            .sortedWith(compareBy({ it.hdmiPort ?: Int.MAX_VALUE }, { it.label }))
    }

    fun launchIntent(inputId: String): Intent? {
        val known = list().any { it.id == inputId }
        if (!known) return null
        return Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun hdmiPort(info: TvInputInfo): Int? {
        if (info.type != TvInputInfo.TYPE_HDMI) return null
        val label = info.loadLabel(context)?.toString().orEmpty()
        return Regex("HDMI\\s*(\\d)", RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("HW(\\d+)").find(info.id)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..8 }
    }
}

class AppLauncher(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    @Volatile private var cache: List<AppEntry>? = null

    fun list(): List<AppEntry> {
        cache?.let { return it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val entries = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    iconPng = runCatching { encodeIcon(it.loadIcon(pm)) }.getOrNull(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        cache = entries
        return entries
    }

    fun label(packageName: String): String =
        list().firstOrNull { it.packageName == packageName }?.label ?: packageName

    fun launchIntent(packageName: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun encodeIcon(drawable: Drawable): String {
        val size = 96
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
        } else {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
            }
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

class MediaVolume(context: Context) {
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    fun media(action: MediaAction) {
        val keycode = when (action) {
            MediaAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaAction.FAST_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            MediaAction.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
    }

    val level: Int get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    val max: Int get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun volume(action: VolumeAction, showSystemUi: Boolean) {
        val direction = when (action) {
            VolumeAction.UP -> AudioManager.ADJUST_RAISE
            VolumeAction.DOWN -> AudioManager.ADJUST_LOWER
            VolumeAction.MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
        }
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0)
    }

    fun set(level: Int, showSystemUi: Boolean) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, max), if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0)
    }
}
