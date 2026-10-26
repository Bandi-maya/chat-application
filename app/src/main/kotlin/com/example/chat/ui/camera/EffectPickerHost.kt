package com.example.chat.ui.camera

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.chat.ServiceLocator

/** Dart EffectPickerSheet.show: transparent modal bottom sheet over the shared locator<EffectEngine>. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectPickerHost(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        EffectPickerSheet(engine = ServiceLocator.effectEngine)
    }
}
