package com.example.chat.ui.core.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.geometry.Rect
import com.example.chat.ui.kit.AppContextMenu
import com.example.chat.ui.kit.ContextAnchorType
import com.example.chat.ui.kit.ContextMenuItem
import com.example.chat.ui.kit.ContextMenuSection
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * Port of lib/ui/core/components/overlays/chaty_overlay_renderer.dart plus the design-system pieces it fronts
 * (chaty_kit.dart ChatyConfirmDialog / ChatyToast / ChatyMenuItem / ChatyMenuSheet, chaty_modal.dart ChatyModal;
 * api 3bee780d). Flutter shows these imperatively with a BuildContext; Compose has no equivalent, so a single
 * [ChatyOverlayController] is remembered at the app/screen root, rendered once by [ChatyOverlayHost], and the
 * screens call the same-named entry points on it. showConfirmDialog is `suspend` and returns true only on confirm.
 *
 * Fidelity notes (uncompiled against Compose here):
 *  - ChatyToast = SnackBar: replaces the current one, 2s, optional background; floats at bottom.
 *  - ChatyMenuSheet delegates to the ported ui/kit/AppContextMenu (Settings drop 34): anchored popover when an
 *    anchorRect (window px) is passed, bottom-sheet fallback otherwise. Dart's anchorPosition (Offset) has no
 *    equivalent param in AppContextMenu; pass a zero-size Rect at that point.
 *  - showAdaptiveSheet: width < 600dp -> bottom sheet (surface, top radius 24, grabber 36x4 radius 2, border color);
 *    otherwise centered modal (as Dart).
 */
class ChatyMenuItem(
    val icon: ImageVector,
    val label: String,
    val onTap: () -> Unit,
    val destructive: Boolean = false,
)

private sealed class Overlay {
    class Confirm(
        val title: String, val message: String, val confirmLabel: String, val cancelLabel: String,
        val destructive: Boolean, val barrierDismissible: Boolean, val result: CompletableDeferred<Boolean>,
    ) : Overlay()
    class Modal(
        val header: (@Composable () -> Unit)?, val content: @Composable () -> Unit, val footer: (@Composable () -> Unit)?,
        val maxWidthDp: Float, val barrierDismissible: Boolean, val done: CompletableDeferred<Unit>,
    ) : Overlay()
    class Sheet(
        val content: @Composable () -> Unit, val dismissible: Boolean, val showGrabber: Boolean, val done: CompletableDeferred<Unit>,
    ) : Overlay()
    class Actions(
        val title: String, val items: List<ChatyMenuItem>, val anchorRect: Rect?, val anchorType: ContextAnchorType,
        val surfaceColor: Color?, val textColor: Color?, val dangerColor: Color?, val done: CompletableDeferred<Unit>,
    ) : Overlay()
}

class ChatyOverlayController {
    internal var current by mutableStateOf<Any?>(null)
    internal var toastMessage by mutableStateOf<String?>(null)
    internal var toastBackground by mutableStateOf<Color?>(null)
    internal var toastToken by mutableStateOf(0)

    /** ChatyToast.show / ChatyOverlayRenderer.showToast: replaces any visible toast; hides after 2s. */
    fun showToast(message: String, background: Color? = null) {
        toastMessage = message; toastBackground = background; toastToken += 1
    }

    /** ChatyConfirmDialog.show: true only when the user confirms. */
    suspend fun showConfirmDialog(
        title: String, message: String, confirmLabel: String = "Confirm", cancelLabel: String = "Cancel",
        destructive: Boolean = false, barrierDismissible: Boolean = true,
    ): Boolean {
        val d = CompletableDeferred<Boolean>()
        current = Overlay.Confirm(title, message, confirmLabel, cancelLabel, destructive, barrierDismissible, d)
        return try { d.await() } finally { current = null }
    }

    /** ChatyOverlayRenderer.showCenterModal (10dp radius ChatyModal, maxWidth 420 default). */
    suspend fun showCenterModal(
        header: (@Composable () -> Unit)? = null, footer: (@Composable () -> Unit)? = null,
        maxWidthDp: Float = 420f, barrierDismissible: Boolean = true, content: @Composable () -> Unit,
    ) {
        val d = CompletableDeferred<Unit>()
        current = Overlay.Modal(header, content, footer, maxWidthDp, barrierDismissible, d)
        try { d.await() } finally { current = null }
    }

    /** ChatyOverlayRenderer.showAdaptiveSheet. [isCompact] = width < 600dp (host decides when rendering). */
    suspend fun showAdaptiveSheet(
        isDismissible: Boolean = true, showGrabber: Boolean = true, content: @Composable () -> Unit,
    ) {
        val d = CompletableDeferred<Unit>()
        current = Overlay.Sheet(content, isDismissible, showGrabber, d)
        try { d.await() } finally { current = null }
    }

    /** ChatyOverlayRenderer.showActionSheet / ChatyMenuSheet.show: rows auto-dismiss BEFORE running onTap. */
    suspend fun showActionSheet(
        title: String, items: List<ChatyMenuItem>, anchorRect: Rect? = null,
        anchorType: ContextAnchorType = ContextAnchorType.generic,
        surfaceColor: Color? = null, textColor: Color? = null, dangerColor: Color? = null,
    ) {
        val d = CompletableDeferred<Unit>()
        current = Overlay.Actions(title, items, anchorRect, anchorType, surfaceColor, textColor, dangerColor, d)
        try { d.await() } finally { current = null }
    }
}

@Composable
fun rememberChatyOverlayController(): ChatyOverlayController = remember { ChatyOverlayController() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatyOverlayHost(controller: ChatyOverlayController, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current

    // Toast: 2s, newest replaces previous (hideCurrentSnackBar then show).
    val msg = controller.toastMessage
    if (msg != null) {
        LaunchedEffect(controller.toastToken) { delay(2000); controller.toastMessage = null }
        Box(modifier.fillMaxSize().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.widthIn(max = 560.dp).shadow(6.dp, RoundedCornerShape(4.dp))
                    .background(controller.toastBackground ?: colors.foreground.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) { Text(msg, color = if (controller.toastBackground != null) Color.White else colors.background, fontSize = 14.sp) }
        }
    }

    when (val o = controller.current) {
        is Overlay.Confirm -> AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { if (o.barrierDismissible) o.result.complete(false) },
            containerColor = colors.surface,
            title = { Text(o.title, color = colors.foreground) },
            text = { Text(o.message, color = colors.foregroundSecondary) },
            dismissButton = { TextButton(onClick = { o.result.complete(false) }) { Text(o.cancelLabel) } },
            confirmButton = {
                Button(
                    onClick = { o.result.complete(true) },
                    colors = if (o.destructive) ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = Color.White)
                    else ButtonDefaults.buttonColors(),
                ) { Text(o.confirmLabel) }
            },
        )
        is Overlay.Modal -> ChatyModalDialog(o.maxWidthDp, o.barrierDismissible, { o.done.complete(Unit) }, o.header, o.content, o.footer)
        is Overlay.Sheet -> {
            val compact = LocalConfiguration.current.screenWidthDp < 600
            if (!compact) {
                ChatyModalDialog(420f, o.dismissible, { o.done.complete(Unit) }, null, o.content, null)
            } else {
                ModalBottomSheet(tonalElevation = 0.dp,
                    onDismissRequest = { o.done.complete(Unit) },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = colors.surface,
                    shape = com.example.chat.ui.kit.ChatyModalShape,
                    dragHandle = if (o.showGrabber) {
                        { Box(Modifier.padding(vertical = 10.dp).size(width = 36.dp, height = 4.dp).background(colors.border, RoundedCornerShape(2.dp))) }
                    } else null,
                ) { o.content() }
            }
        }
        is Overlay.Actions -> {
            // Dart ChatyMenuSheet.show -> AppContextMenu.show: anchored popover when anchorRect is given, else bottom sheet.
            val ink = o.textColor ?: colors.onSurface
            AppContextMenu(
                onDismiss = { o.done.complete(Unit) },
                title = o.title,
                backgroundColor = o.surfaceColor ?: colors.surface,
                primaryTextColor = ink,
                secondaryTextColor = ink.copy(alpha = 0.65f),
                destructiveColor = o.dangerColor ?: colors.error,
                anchorRect = o.anchorRect,
                anchorType = o.anchorType,
                sections = listOf(ContextMenuSection(o.items.map {
                    ContextMenuItem(label = it.label, onTap = it.onTap, icon = it.icon, isDestructive = it.destructive)
                })),
            )
        }
        else -> Unit
    }
}

/** ChatyModal: 10dp radius, surface fill, 1dp border (white .12 dark / black .08 light), max height 82% of screen,
 *  margin 20/24, fixed header + scrollable body + fixed footer. */
@Composable
private fun ChatyModalDialog(
    maxWidthDp: Float, dismissible: Boolean, onDismiss: () -> Unit,
    header: (@Composable () -> Unit)?, content: @Composable () -> Unit, footer: (@Composable () -> Unit)?,
) {
    val colors = LocalAppColors.current
    val screenH = LocalConfiguration.current.screenHeightDp
    val border = if (colors.surface.luminanceApprox() < 0.5f) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    Dialog(onDismissRequest = { if (dismissible) onDismiss() }, properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible, usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = maxWidthDp.dp).heightIn(max = (screenH * 0.82f).dp)
                .shadow(28.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp)).background(colors.surface)
                .border(1.dp, border, RoundedCornerShape(10.dp)),
        ) {
            header?.invoke()
            Box(Modifier.weight(1f, fill = false)) { content() }
            footer?.invoke()
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
