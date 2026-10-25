package com.example.chat

import com.example.chat.ui.settings.appicon.LauncherIconAliasPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the Dart MethodChannel('chaty/launcher_icon') alias semantics:
 *  7 variants (app_icon_controller.dart), exactly one enabled component per
 *  switch, .MainActivity = installed default entry = 'bird'. */
class LauncherIconAliasPlanParityTest {

    private val pkg = "com.example.chat"

    @Test
    fun aliasesMatchDartVariantNamesInOrder() {
        assertEquals(
            listOf("bird", "warm", "outline", "obsidian", "glass", "signal", "fold"),
            LauncherIconAliasPlan.ALIASES,
        )
    }

    @Test
    fun componentNamesFollowLauncherAliasConvention() {
        assertEquals(
            "com.example.chat.launcher.WarmIconAlias",
            LauncherIconAliasPlan.aliasComponent(pkg, "warm"),
        )
        assertEquals(
            "com.example.chat.launcher.BirdIconAlias",
            LauncherIconAliasPlan.aliasComponent(pkg, "bird"),
        )
        assertEquals(
            "com.example.chat.MainActivity",
            LauncherIconAliasPlan.mainActivityComponent(pkg),
        )
    }

    @Test
    fun everyVariantEnablesExactlyOneComponent() {
        for (alias in LauncherIconAliasPlan.ALIASES) {
            val states = LauncherIconAliasPlan.transitions(pkg, alias)
            assertEquals(8, states.size) // 7 aliases + MainActivity
            assertEquals(
                "exactly one enabled component for $alias",
                1, states.values.count { it },
            )
        }
    }

    @Test
    fun birdKeepsInstalledMainActivityEntry() {
        val states = LauncherIconAliasPlan.transitions(pkg, "bird")
        assertEquals(true, states["com.example.chat.MainActivity"])
        assertEquals(false, states["com.example.chat.launcher.BirdIconAlias"])
    }

    @Test
    fun nonDefaultVariantDisablesMainActivity() {
        val states = LauncherIconAliasPlan.transitions(pkg, "obsidian")
        assertEquals(true, states["com.example.chat.launcher.ObsidianIconAlias"])
        assertEquals(false, states["com.example.chat.MainActivity"])
    }

    @Test
    fun currentAliasFallsBackToDartDefaultWhenNoAliasEnabled() {
        assertEquals("bird", LauncherIconAliasPlan.currentAlias(pkg, emptySet()))
        assertEquals(
            "glass",
            LauncherIconAliasPlan.currentAlias(
                pkg, setOf("com.example.chat.launcher.GlassIconAlias"),
            ),
        )
    }

    @Test
    fun invalidAliasRejected() {
        assertFalse(LauncherIconAliasPlan.isValidAlias("swift")) // legacy id, not an alias
        assertTrue(LauncherIconAliasPlan.isValidAlias("fold"))
        var threw = false
        try {
            LauncherIconAliasPlan.transitions(pkg, "nope")
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
