package com.questorylabs.tvc.agent.control

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.questorylabs.tvc.agent.AgentState
import com.questorylabs.tvc.agent.ui.OverlayWindow
import com.questorylabs.tvc.shared.KeyCode

class ControlAccessibilityService : AccessibilityService() {
    private var overlay: OverlayWindow? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentState.accessibilityEnabled.value = true
        overlay = OverlayWindow(this).also { it.attach() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        overlay?.detach()
        overlay = null
        instance = null
        AgentState.accessibilityEnabled.value = false
        super.onDestroy()
    }

    fun performKey(code: KeyCode): Boolean = when (code) {
        KeyCode.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        KeyCode.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
        KeyCode.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        KeyCode.POWER_DIALOG -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        KeyCode.MENU -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        KeyCode.DPAD_CENTER -> dpadCenter()
        KeyCode.DPAD_UP -> dpad(GLOBAL_ACTION_DPAD_UP, View.FOCUS_UP)
        KeyCode.DPAD_DOWN -> dpad(GLOBAL_ACTION_DPAD_DOWN, View.FOCUS_DOWN)
        KeyCode.DPAD_LEFT -> dpad(GLOBAL_ACTION_DPAD_LEFT, View.FOCUS_LEFT)
        KeyCode.DPAD_RIGHT -> dpad(GLOBAL_ACTION_DPAD_RIGHT, View.FOCUS_RIGHT)
    }

    private fun dpad(globalAction: Int, focusDirection: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && performGlobalAction(globalAction)) return true
        val focused = focusedNode() ?: return false
        val next = focused.focusSearch(focusDirection) ?: return false
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun dpadCenter(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)) return true
        val focused = focusedNode() ?: return false
        return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun focusedNode(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun setText(text: String): Boolean {
        val node = focusedNode() ?: return false
        if (node.isEditable) {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("tvcompanion", text))
        val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        clipboard.clearPrimaryClip()
        return pasted
    }

    companion object {
        @Volatile
        var instance: ControlAccessibilityService? = null
            private set
    }
}
