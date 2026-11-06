package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source guard only. Device semantics, pointer dispatch and pixels need Android proof. */
class CallA11ySourceContractTest {
    private fun source(): String {
        val path = "app/src/main/kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt"
        return listOf(File(path), File("../$path")).first { it.exists() }.readText()
    }
    @Test fun localPreviewSwitchUses48DpButtonWithExistingCallback() {
        val switch = source().substringAfter("// Draggable local preview PiP")
            .substringBefore("if (setupError != null")
        assertTrue(switch.contains("IconButton(\n                        onClick = onSwitchCamera,"))
        assertTrue(switch.contains(".size(48.dp)"))
        assertTrue(switch.contains("contentDescription = \"Switch camera\""))
        assertFalse(switch.contains(".clickable { onSwitchCamera() }"))
    }
    @Test fun missingSessionCloseUsesTextButtonNotClickableText() {
        val close = source().substringBefore("LaunchedEffect(session.callId, session.state)")
        assertTrue(close.contains("import androidx.compose.material3.TextButton"))
        assertTrue(close.contains("TextButton(\n                    onClick = onBack,"))
        assertTrue(close.contains("Modifier.heightIn(min = 48.dp)"))
        assertFalse(close.contains("modifier = Modifier.clickable { onBack() }"))
    }
}
