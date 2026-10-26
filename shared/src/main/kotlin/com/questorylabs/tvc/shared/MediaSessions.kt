package com.questorylabs.tvc.shared

object MediaSessions {
    private val packageLine = Regex("^\\s*package=([A-Za-z0-9_.]+)\\s*$")
    private val stateLine = Regex("state=PlaybackState \\{state=(?:[A-Z_]+\\()?(\\d+)\\)?, position=(-?\\d+)")
    private val descriptionLine = Regex("^\\s*metadata:.*description=(.*)$")

    fun parse(dump: String): NowPlayingInfo {
        val sessions = mutableListOf<NowPlayingInfo>()
        var current: NowPlayingInfo? = null
        var active = false
        val actives = mutableListOf<Boolean>()

        fun flush() {
            current?.let { sessions += it; actives += active }
            current = null
            active = false
        }

        for (line in dump.lineSequence()) {
            val pkg = packageLine.find(line)
            if (pkg != null) {
                flush()
                current = NowPlayingInfo(packageName = pkg.groupValues[1])
                continue
            }
            val session = current ?: continue
            if (line.trim() == "active=true") active = true
            val state = stateLine.find(line)
            if (state != null) {
                current = session.copy(state = playbackState(state.groupValues[1].toInt()), positionMs = state.groupValues[2].toLong().takeIf { it >= 0 })
                continue
            }
            val description = descriptionLine.find(line)
            if (description != null) {
                val parts = description.groupValues[1].split(", ").map { it.trim() }.map { if (it == "null" || it.isEmpty()) null else it }
                current = session.copy(title = parts.getOrNull(0), subtitle = parts.getOrNull(1))
            }
        }
        flush()

        val candidates = sessions.indices.filter { actives[it] || sessions[it].state != PlaybackState.NONE }
        val best = candidates.firstOrNull { sessions[it].state == PlaybackState.PLAYING }
            ?: candidates.firstOrNull { sessions[it].state == PlaybackState.PAUSED && sessions[it].title != null }
            ?: return NowPlayingInfo()
        return sessions[best]
    }

    private fun playbackState(code: Int): PlaybackState = when (code) {
        3, 4, 5, 6, 8 -> PlaybackState.PLAYING
        2 -> PlaybackState.PAUSED
        1 -> PlaybackState.STOPPED
        else -> PlaybackState.NONE
    }
}
