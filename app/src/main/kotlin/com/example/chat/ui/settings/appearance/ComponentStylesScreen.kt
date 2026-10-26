package com.example.chat.ui.settings.appearance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Update
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.customization.ChatyComponentRegistry
import com.example.chat.core.customization.ComponentId
import com.example.chat.core.customization.CustomizationController
import com.example.chat.core.customization.CustomizationSnapshot
import com.example.chat.ui.core.components.composer.ChatyComposerData
import com.example.chat.ui.core.components.composer.ChatyComposerRenderer
import com.example.chat.ui.core.components.headers.ChatyHeaderData
import com.example.chat.ui.core.components.headers.ChatyHeaderRenderer
import com.example.chat.ui.core.components.navigation.ChatyNavItemData
import com.example.chat.ui.core.components.navigation.ChatyNavigationRenderer
import com.example.chat.ui.core.components.overlays.ChatyOverlayController
import com.example.chat.ui.core.components.overlays.ChatyOverlayHost
import com.example.chat.ui.kit.ChatyAppBar
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Settings

private val categories = listOf(
    "Navigation", "Header", "Bubbles", "Ticks", "Composer", "Overlays", "Motion & Density",
)

/** Dart _getActiveId. */
private fun activeIdFor(id: ComponentId, snapshot: CustomizationSnapshot): String = when (id) {
    ComponentId.navigation -> snapshot.navigationVariant
    ComponentId.header -> snapshot.headerVariant
    ComponentId.composer -> snapshot.composerVariant
    ComponentId.bubble -> snapshot.bubbleVariant
    ComponentId.tick -> snapshot.tickVariant
    ComponentId.modal -> snapshot.popupVariant
    else -> ""
}

/** Dart _handleMenuChoice tab-index mapping (index 6 has no component). */
private fun componentForTab(index: Int): ComponentId? = when (index) {
    0 -> ComponentId.navigation
    1 -> ComponentId.header
    2 -> ComponentId.bubble
    3 -> ComponentId.tick
    4 -> ComponentId.composer
    5 -> ComponentId.modal
    else -> null
}

@Composable
private fun rememberCustomizationVersion(controller: CustomizationController): Int {
    var version by remember(controller) { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val l: () -> Unit = { version += 1 }
        controller.addListener(l)
        onDispose { controller.removeListener(l) }
    }
    return version
}

/**
 * Port of lib/features/settings/appearance/component_styles_screen.dart
 * (api 3bee780d): live preview canvas + per-component variant grids +
 * motion/density staging with Apply/Discard.
 *
 * Consumes the customization engine contracts only (core.customization
 * controller/registry/snapshot, header/navigation/overlay renderers).
 * Toasts and the reset confirm dialog go through [overlayController];
 * ChatyOverlayHost is hosted at this screen's root.
 *
 * The preview canvas renders the real ChatyHeaderRenderer /
 * ChatyComposerRenderer / ChatyNavigationRenderer with the staged variants,
 * exactly like the Dart screen.
 *
 * [reducedMotion] = Dart MediaQuery.disableAnimationsOf (owner wires it;
 * durations become 0). ChatyAppBar has no bottom slot, so the Dart
 * TabBar-in-AppBar becomes a ScrollableTabRow directly under it.
 */
@Composable
fun ComponentStylesScreen(
    controller: CustomizationController,
    overlayController: ChatyOverlayController,
    onBack: () -> Unit,
    conversationId: String? = null,
    conversationTitle: String? = null,
    reducedMotion: Boolean = false,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    rememberCustomizationVersion(controller)
    var selectedTab by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    // Dart _dummyComposerText / _dummyFocus for the preview composer.
    var dummyComposerText by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("Hey! Check out this new skin ✨"))
    }
    val dummyFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    val snapshot = controller.effectiveSnapshot
    val isDirty = controller.isDirty
    val motionMs = if (reducedMotion) 0 else 160

    fun resetSection() {
        val compId = componentForTab(selectedTab) ?: return
        val def = ChatyComponentRegistry.instance.getDefaultVariantId(compId)
        scope.launch {
            controller.setVariant(compId, def, immediate = true)
            overlayController.showToast("Reset section to default")
        }
    }

    fun resetAll() {
        scope.launch {
            val confirmed = overlayController.showConfirmDialog(
                title = "Reset Customizations",
                message = "Are you sure you want to revert all custom visual variants to default?",
                confirmLabel = "Reset All",
                destructive = true,
            )
            if (confirmed) {
                controller.resetAll()
                overlayController.showToast("All customizations reset to defaults")
            }
        }
    }

    Box {
        Column(Modifier.background(colors.background)) {
            ChatyAppBar(
                title = if (conversationId != null) "Chat appearance (${conversationTitle ?: "Chat"})" else "Component styles",
                subtitle = if (conversationId != null) "Per-conversation visual overrides" else "Navigation, header, bubbles, ticks, composer and more",
                onBack = onBack,
                actions = {
                    if (controller.canUndo) {
                        IconButton(onClick = {
                            ChatyHapticsPort.light(view)
                            scope.launch {
                                controller.undo()
                                overlayController.showToast("Reverted previous customization")
                            }
                        }) {
                            Icon(Icons.Rounded.Undo, contentDescription = "Undo last change", tint = colors.foreground)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = colors.foreground)
                        }
                        DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Reset current section") },
                                onClick = { menuOpen = false; resetSection() },
                            )
                            DropdownMenuItem(
                                text = { Text("Reset all customizations") },
                                onClick = { menuOpen = false; resetAll() },
                            )
                        }
                    }
                },
            )
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = colors.primary,
                edgePadding = 0.dp,
                divider = {},
                indicator = {
                    // Dart indicatorWeight 3.0; PrimaryIndicator usage mirrors
                    // the owner-compiled LinkedDevicesQrScreen (cfe6363), with
                    // height added for the source weight - the one unverified
                    // API arg here; drop `height` if CI rejects it.
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier,
                        color = colors.primary,
                        height = 3.dp,
                    )
                },
            ) {
                categories.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                        selectedContentColor = colors.primary,
                        unselectedContentColor = colors.foregroundSecondary,
                    )
                }
            }

            LivePreviewCard(
                snapshot = snapshot,
                conversationTitle = conversationTitle,
                reducedMotion = reducedMotion,
                dummyText = dummyComposerText,
                onDummyTextChange = { dummyComposerText = it },
                dummyFocus = dummyFocus,
            )

            Box(Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> VariantGrid(ComponentId.navigation, snapshot, controller, motionMs)
                    1 -> VariantGrid(ComponentId.header, snapshot, controller, motionMs)
                    2 -> VariantGrid(ComponentId.bubble, snapshot, controller, motionMs)
                    3 -> VariantGrid(ComponentId.tick, snapshot, controller, motionMs)
                    4 -> VariantGrid(ComponentId.composer, snapshot, controller, motionMs)
                    5 -> VariantGrid(ComponentId.modal, snapshot, controller, motionMs)
                    else -> MotionDensitySettings(snapshot, controller)
                }
            }

            if (isDirty) {
                ActionBar(
                    conversationId = conversationId,
                    controller = controller,
                    overlayController = overlayController,
                )
            }
        }
        ChatyOverlayHost(overlayController)
    }
}

@Composable
private fun LivePreviewCard(
    snapshot: CustomizationSnapshot,
    conversationTitle: String?,
    reducedMotion: Boolean,
    dummyText: androidx.compose.ui.text.input.TextFieldValue,
    onDummyTextChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    dummyFocus: androidx.compose.ui.focus.FocusRequester,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 8.dp)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = colors.shadow.copy(alpha = 0.06f),
                spotColor = colors.shadow.copy(alpha = 0.06f),
            )
            .background(colors.surface, RoundedCornerShape(18.dp))
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)),
    ) {
        ChatyHeaderRenderer(
            variantId = snapshot.headerVariant,
            data = ChatyHeaderData(
                title = conversationTitle ?: "Alex Morgan",
                subtitle = "online • end-to-end encrypted",
                avatar = { StylesAvatarCore(initials = "AM", color = Color(0xFF3B82F6), size = 36) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Call, contentDescription = null, tint = colors.foreground, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Videocam, contentDescription = null, tint = colors.foreground, modifier = Modifier.size(22.dp))
                    }
                },
            ),
            canNavigateBack = false,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceSecondary.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Incoming bubble
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    "Previewing: ${snapshot.bubbleVariant} + ${snapshot.tickVariant}",
                    fontSize = 13.sp,
                    color = colors.foreground,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .background(colors.surface, RoundedCornerShape(14.dp))
                        .border(0.6.dp, colors.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            // Outgoing bubble
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .background(colors.primary.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                        .border(0.6.dp, colors.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Styling changes update here in real time!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                        color = colors.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.DoneAll,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        ChatyComposerRenderer(
            variantId = snapshot.composerVariant,
            data = ChatyComposerData(
                text = dummyText,
                onTextChange = onDummyTextChange,
                focusRequester = dummyFocus,
                hasText = true,
                onSend = {},
                onMicPress = {},
                onAttach = {},
                onCamera = {},
                onEmojiToggle = {},
            ),
            reducedMotion = reducedMotion,
        )
        ChatyNavigationRenderer(
            variantId = snapshot.navigationVariant,
            items = listOf(
                ChatyNavItemData(
                    id = "chats", label = "Chats",
                    icon = Icons.Rounded.ChatBubbleOutline, activeIcon = Icons.Rounded.ChatBubble,
                    badgeCount = 2, isSelected = true, onTap = {},
                ),
                ChatyNavItemData(
                    id = "updates", label = "Updates",
                    icon = Icons.Outlined.Update, activeIcon = Icons.Rounded.Update,
                    isSelected = false, onTap = {},
                ),
                ChatyNavItemData(
                    id = "calls", label = "Calls",
                    icon = Icons.Outlined.Call, activeIcon = Icons.Rounded.Call,
                    isSelected = false, onTap = {},
                ),
                ChatyNavItemData(
                    id = "settings", label = "Settings",
                    icon = Icons.Outlined.Settings, activeIcon = Icons.Rounded.Settings,
                    isSelected = false, onTap = {},
                ),
            ),
            reducedMotion = reducedMotion,
        )
    }
}

@Composable
private fun VariantGrid(
    componentId: ComponentId,
    snapshot: CustomizationSnapshot,
    controller: CustomizationController,
    motionMs: Int,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val variants = remember(componentId) { ChatyComponentRegistry.instance.getVariantsFor(componentId) }
    val activeId = activeIdFor(componentId, snapshot)

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(variants.size) { index ->
            val spec = variants[index]
            val isSelected = spec.variantId == activeId
            val bg by animateColorAsState(
                if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.surface,
                tween(motionMs), label = "variantBg",
            )
            val borderColor by animateColorAsState(
                if (isSelected) colors.primary else colors.border,
                tween(motionMs), label = "variantBorder",
            )
            val borderWidth by animateDpAsState(
                if (isSelected) 1.8.dp else 1.dp,
                tween(motionMs), label = "variantBorderWidth",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
                    .clickable {
                        ChatyHapticsPort.selection(view)
                        scope.launch { controller.setVariant(componentId, spec.variantId) }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        spec.displayName,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.W800 else FontWeight.W600,
                        color = if (isSelected) colors.primary else colors.foreground,
                    )
                    if (spec.description.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(spec.description, fontSize = 12.sp, color = colors.foregroundSecondary)
                    }
                }
                Icon(
                    if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) colors.primary else colors.border,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun MotionDensitySettings(
    snapshot: CustomizationSnapshot,
    controller: CustomizationController,
) {
    val view = LocalView.current
    val motionProfiles = listOf("Subtle", "Standard", "Expressive", "Reduced")
    val densityProfiles = listOf("Compact", "Comfortable", "Spacious")

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp,
        ),
    ) {
        item {
            StylesGroupedSection(
                title = "Motion Profile",
                description = "Physics and duration profiles for screen transitions and micro-interactions.",
            ) {
                motionProfiles.forEach { mp ->
                    val isSelected = snapshot.motionProfile.lowercase() == mp.lowercase()
                    ProfileRow(
                        label = mp,
                        selected = isSelected,
                        onTap = {
                            ChatyHapticsPort.selection(view)
                            controller.stage(snapshot.copyWith(motionProfile = mp.lowercase()))
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            StylesGroupedSection(
                title = "Density Profile",
                description = "Information density across conversation lists and item rows.",
            ) {
                densityProfiles.forEach { dp ->
                    val isSelected = snapshot.densityProfile.lowercase() == dp.lowercase()
                    ProfileRow(
                        label = dp,
                        selected = isSelected,
                        onTap = {
                            ChatyHapticsPort.selection(view)
                            controller.stage(snapshot.copyWith(densityProfile = dp.lowercase()))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, selected: Boolean, onTap: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            color = colors.foreground,
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary)
        }
    }
}

@Composable
private fun ActionBar(
    conversationId: String?,
    controller: CustomizationController,
    overlayController: ChatyOverlayController,
) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(1.dp, colors.border)
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Box(Modifier.weight(1f)) {
            StylesSecondaryButton(text = "Discard", onPressed = {
                ChatyHapticsPort.light(view)
                controller.cancelPreview()
            })
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            StylesPrimaryButton(text = "Apply Changes", onPressed = {
                ChatyHapticsPort.success(view)
                scope.launch {
                    if (conversationId != null) {
                        controller.setConversationOverride(
                            conversationId = conversationId,
                            componentId = ComponentId.bubble,
                            variantId = controller.effectiveSnapshot.bubbleVariant,
                        )
                        controller.commit(description = "Applied conversation overrides")
                    } else {
                        controller.commit(description = "Applied global visual customizations")
                    }
                    overlayController.showToast("Customization applied successfully!")
                }
            })
        }
    }
}

/* ---- Private ports of the Dart design-system primitives this screen uses
 * (app_components.dart / chaty_kit.dart). Private to this file: when a
 * canonical shared version lands, swap the call sites. ---- */

/** ChatyPrimaryButton (height 48, radius lg 16, press scale 0.97, selection
 *  haptic on tap, disabled bg 0.45/fg 0.6). */
@Composable
private fun StylesPrimaryButton(text: String, onPressed: (() -> Unit)?) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val enabled = onPressed != null
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && enabled) 0.97f else 1f, tween(100), label = "primaryScale",
    )
    androidx.compose.material3.Button(
        onClick = {
            ChatyHapticsPort.selection(view)
            onPressed?.invoke()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = Color.White,
            disabledContainerColor = colors.primary.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.6f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        Text(
            text,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontSize = 15.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = (-0.2).sp,
        )
    }
}

/** ChatySecondaryButton (outlined, border 1.2, same sizing). */
@Composable
private fun StylesSecondaryButton(text: String, onPressed: (() -> Unit)?) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val enabled = onPressed != null
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && enabled) 0.97f else 1f, tween(100), label = "secondaryScale",
    )
    androidx.compose.material3.OutlinedButton(
        onClick = {
            ChatyHapticsPort.selection(view)
            onPressed?.invoke()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, colors.border),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = colors.foreground,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        Text(
            text,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontSize = 15.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = (-0.2).sp,
        )
    }
}

/** ChatyGroupedSection (uppercase primary title, surface card radius 18,
 *  1dp border, 16dp-indented dividers, optional caption description). */
@Composable
private fun StylesGroupedSection(
    title: String?,
    description: String?,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.padding(bottom = 16.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 1.0.sp,
                color = colors.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(18.dp))
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)),
        ) {
            content()
        }
        if (description != null) {
            Text(
                description,
                fontSize = 12.sp,
                color = colors.foregroundSecondary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
            )
        }
    }
}

/** ChatyAvatarCore (initials tile; only the shape='circle' path this screen
 *  uses, initials uppercased to 2 chars). */
@Composable
private fun StylesAvatarCore(initials: String, color: Color, size: Int) {
    val text = initials.trim().ifEmpty { "?" }.take(2).uppercase()
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color, RoundedCornerShape((size / 2).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = (size * 0.40).sp,
            fontWeight = FontWeight.W700,
        )
    }
}
