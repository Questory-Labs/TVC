package com.questorylabs.tvc.shared

object ShellPolicy {
    private val allowedPrefixes = listOf(
        "pm list packages",
        "pm disable-user --user 0 ",
        "pm enable ",
        "input keyevent ",
        "input text ",
        "am start ",
        "am force-stop ",
        "settings put global private_dns_specifier ",
    )
    private val allowedExact = setOf(
        REBOOT,
        KILL_ALL,
        SCREENCAP,
        MEDIA_SESSIONS,
        DNS_OFF,
        DNS_HOSTNAME,
        DNS_GET_MODE,
        DNS_GET_HOST,
    )
    private val installCommand = Regex("^cmd package install -S [1-9][0-9]{0,9}$")
    private val hostname = Regex("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$")
    private val forbidden = Regex("[;&|`$<>\n\r]")
    private val packageName = Regex("^[A-Za-z0-9_.]+$")

    fun isAllowed(cmd: String): Boolean {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty() || forbidden.containsMatchIn(trimmed)) return false
        if (trimmed in allowedExact || installCommand.matches(trimmed)) return true
        return allowedPrefixes.any { trimmed.startsWith(it) }
    }

    fun isHostname(host: String): Boolean = hostname.matches(host)

    fun isPackageName(pkg: String): Boolean = packageName.matches(pkg) && pkg.length <= 200

    fun disableCommand(pkg: String) = "pm disable-user --user 0 $pkg"
    fun enableCommand(pkg: String) = "pm enable $pkg"
    const val LIST_DISABLED = "pm list packages -d"
    fun keyEventCommand(keycode: Int) = "input keyevent $keycode"
    fun forceStopCommand(pkg: String) = "am force-stop $pkg"
    fun dnsHostCommand(host: String) = "settings put global private_dns_specifier $host"
    fun installCommand(sizeBytes: Long) = "cmd package install -S $sizeBytes"
    const val REBOOT = "reboot"
    const val KILL_ALL = "am kill-all"
    const val SCREENCAP = "screencap -p"
    const val MEDIA_SESSIONS = "dumpsys media_session"
    const val DNS_OFF = "settings put global private_dns_mode off"
    const val DNS_HOSTNAME = "settings put global private_dns_mode hostname"
    const val DNS_GET_MODE = "settings get global private_dns_mode"
    const val DNS_GET_HOST = "settings get global private_dns_specifier"

    private val safeText = Regex("[^A-Za-z0-9 .,@_:/=+-]")
    fun textCommand(text: String): String? {
        val cleaned = safeText.replace(text, "").trim().replace(" ", "%s")
        return if (cleaned.isEmpty()) null else "input text $cleaned"
    }
}
