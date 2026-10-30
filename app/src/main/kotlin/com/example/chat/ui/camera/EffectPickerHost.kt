package com.example.chat.ui.camera

import androidx.compose.ui.unit.dp

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.chat.ServiceLocator

/** Dart EffectPickerSheet.show: transparent modal bottom sheet over the shared
 *  locator<EffectEngine>.
 *
 *  Existing call sites (ChatsHomeScreen, AppRoot) pass only onDismiss and get
 *  exactly the old behavior. [engine] is exposed so camera-level callers can
 *  pass their own engine, and [onBrowseLenses] forwards the optional bridge
 *  into the lens explorer (see EffectPickerSheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectPickerHost(
    onDismiss: () -> Unit,
    engine: EffectEngine = ServiceLocator.effectEngine,
    onBrowseLenses: (() -> Unit)? = null,
) {
    ModalBottomSheet(tonalElevation = 0.dp, shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        EffectPickerSheet(engine = engine, onBrowseLenses = onBrowseLenses)
    }
}
