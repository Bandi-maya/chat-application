package com.example.chat.ui.settings.appicon

import com.example.chat.ui.core.controllers.BrandIconSource
import com.example.chat.ui.core.controllers.LauncherIconVariant
import com.example.chat.ui.core.controllers.title

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chat.ui.settings.ChatyInfoTile
import com.example.chat.ui.settings.ChatyPreviewCard
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySettingsTile
import com.example.chat.ui.theme.LocalAppColors
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.selected

/** Exact port of lib/features/settings/appearance/app_icon_settings_screen.dart.
 *
 *  Seams:
 *  - [showToast]: Dart ScaffoldMessenger snackbars (hideCurrentSnackBar +
 *    showSnackBar) - host wires a SnackbarHostState.
 *  - [onRestartApp]: Dart SystemNavigator.pop() after the restart prompt -
 *    host wires activity finish/task removal.
 *  - [imageSizeDecoder]: Dart decodeImageFromList bounds read; default uses
 *    BitmapFactory inJustDecodeBounds.
 *  - Navigation to the editor is internal (state-driven), same as the Dart
 *    MaterialPageRoute push. */

/** Dart decodeImageFromList bounds check; returns (width, height) or null. */
fun defaultImageSizeDecoder(bytes: ByteArray): IntSize? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    return IntSize(opts.outWidth, opts.outHeight)
}

/** Dart _launcherStatus. */
internal fun launcherStatusText(controller: AppIconController): String =
    if (controller.brandIconSource == BrandIconSource.bundled) {
        "Launcher: ${controller.launcherIcon.title}"
    } else {
        "In-app brand: Custom brand icon active"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconSettingsScreen(
    controller: AppIconController,
    onBack: () -> Unit,
    showToast: (String) -> Unit = {},
    /** Dart SystemNavigator.pop() after the restart prompt; null uses the
     *  host activity's finish(). */
    onRestartApp: (() -> Unit)? = null,
    imageSizeDecoder: (ByteArray) -> IntSize? = ::defaultImageSizeDecoder,
) {
    val context = LocalContext.current
    val restartApp: () -> Unit = onRestartApp ?: { (context as? Activity)?.finish() }
    // ListenableBuilder: recompose on every controller notification.
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val listener: () -> Unit = { revision++ }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") revision // read marker

    // WidgetsBindingObserver.didChangeAppLifecycleState(resumed).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Dart: unawaited refreshNativeLauncherState()
            }
            Unit
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()

    // ---- Dart flow helpers, ported as state-machine lambdas ----

    var confirmRequest by remember { mutableStateOf<ConfirmRequest?>(null) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var editorBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun showError(message: String) = showToast(message)

    fun selectLauncherIcon(variant: LauncherIconVariant) {
        if (controller.isBusy) return
        if (controller.brandIconSource == BrandIconSource.bundled && controller.launcherIcon == variant) return
        confirmRequest = ConfirmRequest(
            title = "Use ${variant.title} icon?",
            message = "Chaty will switch to this launcher icon. Your saved custom icons stay available.",
            confirmLabel = "Apply",
        ) {
            scope.launch {
                val success = controller.applyLauncherIcon(variant)
                if (!success) {
                    showError(controller.lastError ?: "Launcher icon change failed.")
                    return@launch
                }
                confirmRequest = ConfirmRequest(
                    title = "Launcher icon applied",
                    message = "Some launchers cache app icons briefly. Restart Chaty if the old icon is still visible.",
                    confirmLabel = "Restart now",
                    cancelLabel = "Continue",
                    barrierDismissible = false,
                ) {
                    restartApp()
                }
            }
        }
    }

    fun selectCustomPreset(preset: CustomIconPreset) {
        if (controller.isBusy) return
        if (controller.brandIconSource == BrandIconSource.custom && controller.activeCustomPresetId == preset.id) return
        scope.launch {
            val success = controller.activateCustomPreset(preset.id)
            if (!success) {
                showError(controller.lastError ?: "Could not apply the custom brand icon.")
                return@launch
            }
            showToast("Custom Chaty brand icon applied.")
        }
    }

    fun deleteCustomPreset(preset: CustomIconPreset) {
        confirmRequest = ConfirmRequest(
            title = "Delete custom brand icon?",
            message = "This removes this custom icon preset. Built-in icons are not affected.",
            confirmLabel = "Delete",
            destructive = true,
        ) {
            scope.launch { controller.removeCustomPreset(preset.id) }
        }
    }

    fun loadCustomImage(source: CustomIconInputSource) {
        scope.launch {
            val preparedPath = controller.pickCustomIconImage(source)
            if (preparedPath.isNullOrEmpty()) {
                controller.lastError?.let { showError(it) }
                return@launch
            }
            val preparedFile = File(preparedPath)
            try {
                if (!preparedFile.exists()) {
                    showError("The selected image is no longer available.")
                    return@launch
                }
                val bytes = preparedFile.readBytes()
                if (bytes.isEmpty()) {
                    showError("The selected image is empty or corrupted.")
                    return@launch
                }
                val dims = imageSizeDecoder(bytes)
                if (dims == null) {
                    showError("The selected image could not be opened.")
                    return@launch
                }
                if (dims.width < 128 || dims.height < 128) {
                    showError("Use an image that is at least 128 × 128 pixels.")
                    return@launch
                }
                editorBytes = bytes
            } catch (_: Exception) {
                showError("The selected image could not be opened.")
            } finally {
                try {
                    if (preparedFile.exists()) preparedFile.delete()
                } catch (_: Exception) {
                    // Temporary picker input is best-effort cleanup only.
                }
            }
        }
    }

    // ---- Editor (Dart _CustomBrandIconEditor, pushed route) ----
    val editing = editorBytes
    if (editing != null) {
        CustomBrandIconEditor(
            imageBytes = editing,
            controller = controller,
            onClose = { editorBytes = null },
            showToast = showToast,
        )
        return
    }

    val customPresets = controller.customIconPresets.filter { it.exists }
    val totalTiles = LauncherIconVariant.entries.size + customPresets.size + 1

    ChatySettingsPage(
        title = "App Icon",
        subtitle = "Launcher icon and Chaty branding",
        onBack = onBack,
    ) {
        ChatyPreviewCard(title = "Current icon") {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ChatyBrandIcon(controller = controller, size = 72.dp, borderRadius = 19.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (controller.brandIconSource == BrandIconSource.custom) {
                            "Custom Chaty brand icon"
                        } else {
                            controller.launcherIcon.title
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W800,
                        color = LocalAppColors.current.foreground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        launcherStatusText(controller),
                        fontSize = 12.5.sp,
                        color = LocalAppColors.current.foregroundSecondary,
                    )
                    if (controller.isBusy) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                    }
                }
            }
        }

        ChatySettingsSection(
            title = "Launcher Icons",
            description = "Choose from six distinctive Chaty brand identities. Tap any icon to apply it as your device launcher icon.",
        ) {
            // GridView maxCrossAxisExtent 150 / mainAxisExtent 132 / spacing 10,
            // shrinkWrap inside the page scroll -> explicit chunked grid.
            BoxWithConstraints(Modifier.padding(12.dp)) {
                // SliverGridDelegateWithMaxCrossAxisExtent(150): ceil(w/150) columns.
                val columns = maxOf(1, kotlin.math.ceil(maxWidth.value / 150f).toInt())
                val rows = (0 until totalTiles).chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowIndexes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowIndexes.forEach { index ->
                                Box(Modifier.weight(1f).height(132.dp)) {
                                    when {
                                        index < LauncherIconVariant.entries.size -> {
                                            val variant = LauncherIconVariant.entries[index]
                                            val selected = controller.brandIconSource == BrandIconSource.bundled &&
                                                controller.launcherIcon == variant
                                            LauncherIconOption(
                                                variant = variant,
                                                selected = selected,
                                                enabled = !controller.isBusy,
                                                onTap = { selectLauncherIcon(variant) },
                                            )
                                        }
                                        index - LauncherIconVariant.entries.size < customPresets.size -> {
                                            val preset = customPresets[index - LauncherIconVariant.entries.size]
                                            val selected = controller.brandIconSource == BrandIconSource.custom &&
                                                controller.activeCustomPresetId == preset.id
                                            SavedCustomIconOption(
                                                preset = preset,
                                                selected = selected,
                                                enabled = !controller.isBusy,
                                                onTap = { selectCustomPreset(preset) },
                                                onDelete = { deleteCustomPreset(preset) },
                                            )
                                        }
                                        else -> {
                                            AddCustomIconOption(
                                                enabled = !controller.isBusy,
                                                onTap = {
                                                    if (!controller.isBusy) showSourceSheet = true
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            // Fill trailing cells so rows align like the grid.
                            repeat(columns - rowIndexes.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (controller.launcherIcon != LauncherIconVariant.warm ||
                controller.brandIconSource == BrandIconSource.custom
            ) {
                ChatySettingsTile(
                    icon = Icons.Rounded.Restore,
                    title = "Restore Warm Signature icon",
                    subtitle = "Use Chaty’s default Warm Neutral launcher mark",
                    onTap = if (controller.isBusy) null else ({ selectLauncherIcon(LauncherIconVariant.warm) }),
                )
            }
        }

        if (customPresets.isNotEmpty()) {
            ChatySettingsSection(
                title = "In-app brand icon library",
                description = "${customPresets.size} saved custom brand icon${if (customPresets.size == 1) "" else "s"}. Long-press any custom tile above to delete.",
            ) {
                ChatySettingsTile(
                    icon = Icons.Outlined.AddPhotoAlternate,
                    title = "Add custom brand icon",
                    subtitle = "Photo Picker or Camera • crop, zoom and rotate",
                    onTap = if (controller.isBusy) null else ({ showSourceSheet = true }),
                )
            }
        }

        controller.lastError?.let { error ->
            ChatyInfoTile(
                message = error,
                icon = Icons.Outlined.ErrorOutline,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    // ---- ChatyConfirmDialog ----
    confirmRequest?.let { request ->
        AlertDialog(
            onDismissRequest = {
                if (request.barrierDismissible) confirmRequest = null
            },
            title = { Text(request.title) },
            text = { Text(request.message) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRequest = null
                        request.onConfirm()
                    },
                    colors = if (request.destructive) {
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                        )
                    } else {
                        androidx.compose.material3.ButtonDefaults.buttonColors()
                    },
                ) { Text(request.confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRequest = null }) { Text(request.cancelLabel) }
            },
        )
    }

    // ---- Dart showModalBottomSheet source picker ----
    if (showSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showSourceSheet = false }) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(
                    "Add custom app icon",
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W800,
                )
                SourceSheetTile(
                    icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                    title = "Photos",
                    subtitle = "Choose an image with Android Photo Picker",
                    onClick = {
                        showSourceSheet = false
                        loadCustomImage(CustomIconInputSource.PHOTOS)
                    },
                )
                SourceSheetTile(
                    icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                    title = "Camera",
                    subtitle = "Capture a new image",
                    onClick = {
                        showSourceSheet = false
                        loadCustomImage(CustomIconInputSource.CAMERA)
                    },
                )
            }
        }
    }
}

private class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val cancelLabel: String = "Cancel",
    val destructive: Boolean = false,
    val barrierDismissible: Boolean = true,
    val onConfirm: () -> Unit,
)

@Composable
private fun SourceSheetTile(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.W600, fontSize = 14.5.sp)
            Text(subtitle, fontSize = 12.5.sp, color = LocalAppColors.current.foregroundSecondary)
        }
    }
}

@Composable
private fun LauncherIconOption(
    variant: LauncherIconVariant,
    selected: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    IconTileShell(
        selected = selected,
        enabled = enabled,
        label = variant.title,
        onTap = onTap,
    ) {
        LauncherIconPreview(variant = variant, size = 64.dp, borderRadius = 15.dp)
    }
}

@Composable
private fun SavedCustomIconOption(
    preset: CustomIconPreset,
    selected: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    IconTileShell(
        selected = selected,
        enabled = enabled,
        label = "Custom",
        onTap = onTap,
        onLongPress = if (enabled) onDelete else null,
    ) {
        val file = File(preset.path)
        if (file.exists()) {
            coil.compose.AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(15.dp)),
            )
        } else {
            // Dart errorBuilder fallback.
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BrokenImage, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AddCustomIconOption(
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    IconTileShell(
        selected = false,
        enabled = enabled,
        label = "Add",
        onTap = onTap,
    ) {
        Box(
            Modifier.size(64.dp).background(scheme.surfaceVariant, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(32.dp))
        }
    }
}

/** _IconTileShell: Material container, InkWell tap/long-press, 180ms border
 *  animation (reduced to zero when animations disabled - host concern),
 *  selected check badge. */
@Composable
private fun IconTileShell(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    child: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .semantics { this.selected = selected }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) scheme.primaryContainer.copy(alpha = 0.45f)
                else scheme.surfaceContainerLow,
            )
            .combinedClickableCompat(
                enabled = enabled,
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) scheme.primary
                else scheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                child()
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer { translationX = 5.dp.toPx(); translationY = -5.dp.toPx() }
                            .size(22.dp)
                            .background(scheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.W800 else FontWeight.W600,
            )
        }
    }
}

/** Modifier.combinedClickable indirection so the long-press stays optional
 *  exactly like Dart InkWell(onLongPress: enabled ? onDelete : null). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier = combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
