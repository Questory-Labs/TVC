package com.questorylabs.tvc.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSessionsTest {
    private val dump = """
        MEDIA SESSION SERVICE (dumpsys media_session)
          Sessions Stack - have 2 sessions:
            TvLauncher com.google.android.tvlauncher/TvLauncher (userId=0)
              ownerPid=1200, ownerUid=10050, userId=0
              package=com.google.android.tvlauncher
              active=false
              state=PlaybackState {state=0, position=0, buffered position=0, speed=0.0, updated=0, actions=0}
              metadata: size=0, description=null
            YouTube com.google.android.youtube.tv/YouTube (userId=0)
              ownerPid=2200, ownerUid=10080, userId=0
              package=com.google.android.youtube.tv
              active=true
              state=PlaybackState {state=3, position=125000, buffered position=0, speed=1.0, updated=1, actions=0}
              metadata: size=7, description=Big Buck Bunny, Blender Studio, null
    """.trimIndent()

    @Test
    fun picksPlayingSession() {
        val info = MediaSessions.parse(dump)
        assertEquals("com.google.android.youtube.tv", info.packageName)
        assertEquals("Big Buck Bunny", info.title)
        assertEquals("Blender Studio", info.subtitle)
        assertEquals(PlaybackState.PLAYING, info.state)
        assertEquals(125000L, info.positionMs)
    }

    @Test
    fun pausedWithTitleIsKept() {
        val info = MediaSessions.parse(dump.replace("state=3,", "state=2,"))
        assertEquals(PlaybackState.PAUSED, info.state)
        assertEquals("Big Buck Bunny", info.title)
    }

    @Test
    fun parsesNamedStateFormat() {
        val modern = dump
            .replace("{state=0,", "{state=NONE(0),")
            .replace("{state=3,", "{state=PLAYING(3),")
        val info = MediaSessions.parse(modern)
        assertEquals(PlaybackState.PLAYING, info.state)
        assertEquals("com.google.android.youtube.tv", info.packageName)
        assertEquals(125000L, info.positionMs)
        assertEquals(PlaybackState.PAUSED, MediaSessions.parse(modern.replace("PLAYING(3)", "PAUSED(2)")).state)
    }

    @Test
    fun emptyDumpGivesNone() {
        assertEquals(NowPlayingInfo(), MediaSessions.parse(""))
        assertEquals(NowPlayingInfo(), MediaSessions.parse(dump.replace("state=3,", "state=0,").replace("active=true", "active=false")))
    }
}
