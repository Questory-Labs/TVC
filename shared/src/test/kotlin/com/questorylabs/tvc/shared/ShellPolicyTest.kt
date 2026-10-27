package com.questorylabs.tvc.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPolicyTest {
    @Test
    fun allowsWhitelistedCommands() {
        assertTrue(ShellPolicy.isAllowed("pm list packages -d"))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.disableCommand("com.google.android.feedback")))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.enableCommand("com.google.android.feedback")))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.keyEventCommand(178)))
        assertTrue(ShellPolicy.isAllowed("am start -a android.intent.action.VIEW -d content://android.media.tv/passthrough/x"))
    }

    @Test
    fun rejectsEverythingElse() {
        assertFalse(ShellPolicy.isAllowed("pm uninstall com.x"))
        assertFalse(ShellPolicy.isAllowed("pm disable-user --user 0 com.x; reboot"))
        assertFalse(ShellPolicy.isAllowed("input keyevent 26 && reboot"))
        assertFalse(ShellPolicy.isAllowed("pm enable \$(reboot)"))
        assertFalse(ShellPolicy.isAllowed(""))
        assertFalse(ShellPolicy.isAllowed("pm list packages -d\nreboot"))
    }

    @Test
    fun textCommandIsSanitised() {
        assertEquals("input text hello%sworld", ShellPolicy.textCommand("hello world"))
        assertEquals("input text abc", ShellPolicy.textCommand("a;b&c`"))
        assertEquals(null, ShellPolicy.textCommand(";&|"))
        assertEquals("input text what%sis", ShellPolicy.textCommand("what? is*"))
        assertEquals(null, ShellPolicy.textCommand("????"))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.textCommand("hi there")!!))
    }

    @Test
    fun packageNameValidation() {
        assertTrue(ShellPolicy.isPackageName("com.google.android.apps.tv.dreamx"))
        assertFalse(ShellPolicy.isPackageName("com.x; rm"))
        assertFalse(ShellPolicy.isPackageName(""))
    }

    @Test
    fun allowsDesignCommandsExactly() {
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.REBOOT))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.KILL_ALL))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.SCREENCAP))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.MEDIA_SESSIONS))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.DNS_OFF))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.dnsHostCommand("dns.adguard.com")))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.forceStopCommand("com.example")))
        assertTrue(ShellPolicy.isAllowed(ShellPolicy.installCommand(1234567)))
        assertFalse(ShellPolicy.isAllowed("reboot recovery"))
        assertFalse(ShellPolicy.isAllowed("reboot bootloader"))
        assertFalse(ShellPolicy.isAllowed("screencap -p /sdcard/x.png"))
        assertFalse(ShellPolicy.isAllowed("cmd package install -S 0"))
        assertFalse(ShellPolicy.isAllowed("cmd package install -S 12 -r"))
        assertFalse(ShellPolicy.isAllowed("settings put global adb_enabled 0"))
        assertFalse(ShellPolicy.isAllowed("settings put global private_dns_specifier x; reboot"))
    }

    @Test
    fun hostnameValidation() {
        assertTrue(ShellPolicy.isHostname("dns.adguard.com"))
        assertTrue(ShellPolicy.isHostname("one.one.one.one"))
        assertFalse(ShellPolicy.isHostname("localhost"))
        assertFalse(ShellPolicy.isHostname("bad host.com"))
        assertFalse(ShellPolicy.isHostname("-x.com"))
        assertFalse(ShellPolicy.isHostname(""))
    }

    @Test
    fun detectsFailureOutput() {
        assertEquals(
            "Error: Activity not started, unable to resolve Intent",
            ShellPolicy.failure("Starting: Intent { act=android.intent.action.VIEW }\nError: Activity not started, unable to resolve Intent\n"),
        )
        assertEquals(
            "Exception occurred while executing 'disable-user':",
            ShellPolicy.failure("Exception occurred while executing 'disable-user':\njava.lang.IllegalArgumentException: Unknown package: com.x\n"),
        )
        assertEquals("Security exception: Shell cannot change component state", ShellPolicy.failure("Security exception: Shell cannot change component state\n"))
    }

    @Test
    fun acceptsSuccessOutput() {
        assertNull(ShellPolicy.failure("Package com.x new state: disabled-user\n"))
        assertNull(ShellPolicy.failure("Starting: Intent { cmp=com.x/.Main }\nWarning: Activity not started, intent has been delivered to currently running top-most instance.\n"))
        assertNull(ShellPolicy.failure(""))
        assertNull(ShellPolicy.failure("package:com.x\npackage:com.y\n"))
        assertNull(ShellPolicy.failure("  state=PlaybackState {state=3}\n    Error count: 0\n"))
    }
}
