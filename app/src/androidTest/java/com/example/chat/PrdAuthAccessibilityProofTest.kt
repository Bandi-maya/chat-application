package com.example.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.auth.*
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated presentational controls, no authentication/network or saved-account mutation. */
class PrdAuthAccessibilityProofTest {
    @get:Rule val compose = createAndroidComposeRule<ChatFixtureActivity>()
    @Test fun narrowDoubleFontAuthTargetsAndEditableLabelRemainUsable() {
        val value = mutableStateOf("")
        var back = 0
        var submit = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                ChatyTheme(ThemePresets.monochromeDark) {
                    Column(Modifier.width(280.dp)) {
                        AuthBackButton { back++ }
                        AuthTextField("Email", "name@example.invalid", ThemePresets.monochromeDark, value.value, { value.value = it })
                        AuthPrimaryButton("Continue with this account", ThemePresets.monochromeDark, { submit++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("auth-back").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("auth-field-email").assertContentDescriptionEquals("Email").performTextInput("test@example.invalid")
        compose.onNodeWithText("Continue with this account").performClick()
        compose.runOnIdle { assertEquals(1, back); assertEquals(1, submit); assertEquals("test@example.invalid", value.value) }
    }
}
