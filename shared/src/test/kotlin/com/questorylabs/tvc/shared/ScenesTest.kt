package com.questorylabs.tvc.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScenesTest {
    @Test
    fun defaultsAreValid() {
        Scenes.defaults.forEach { assertNull(it.name, Scenes.validate(it)) }
    }

    @Test
    fun roundTrip() {
        val scenes = listOf(Scene("a", "Game", listOf(SceneStep(Command.LaunchApp("com.x"), 0), SceneStep(Command.Text("hi"), 1_000))))
        assertEquals(scenes, Scenes.decode(Scenes.encode(scenes)))
    }

    @Test
    fun corruptOrEmptyFallsBackToDefaults() {
        assertEquals(Scenes.defaults, Scenes.decode(null))
        assertEquals(Scenes.defaults, Scenes.decode(""))
        assertEquals(Scenes.defaults, Scenes.decode("{not json"))
    }

    @Test
    fun rejectsDangerousOrMalformedScenes() {
        val ok = SceneStep(Command.Key(KeyCode.HOME))
        assertNotNull(Scenes.validate(Scene("1", "x", listOf(SceneStep(Command.Reboot)))))
        assertNotNull(Scenes.validate(Scene("1", "x", listOf(SceneStep(Command.Debloat("com.x", true))))))
        assertNotNull(Scenes.validate(Scene("1", "x", listOf(SceneStep(Command.PrivateDns(null))))))
        assertNotNull(Scenes.validate(Scene("1", " ", listOf(ok))))
        assertNotNull(Scenes.validate(Scene("1", "x", emptyList())))
        assertNotNull(Scenes.validate(Scene("1", "x", List(Scenes.MAX_STEPS + 1) { ok })))
        assertNotNull(Scenes.validate(Scene("1", "x", listOf(ok.copy(delayMs = -1)))))
        assertNotNull(Scenes.validate(Scene("1", "x", listOf(ok.copy(delayMs = Scenes.MAX_DELAY_MS + 1)))))
    }

    @Test
    fun decodeDropsInvalidScenes() {
        val good = Scene("g", "Good", listOf(SceneStep(Command.Key(KeyCode.BACK))))
        val bad = Scene("b", "Bad", listOf(SceneStep(Command.Reboot)))
        assertEquals(listOf(good), Scenes.decode(Scenes.encode(listOf(good, bad))))
    }
}
