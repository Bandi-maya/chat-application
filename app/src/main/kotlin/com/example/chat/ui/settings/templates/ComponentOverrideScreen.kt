package com.example.chat.ui.settings.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CameraAlt as RoundedCameraAlt
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.templates.ChatyTemplateDefinition
import com.example.chat.core.templates.ChatyTemplateRegistry
import com.example.chat.core.templates.ComposerActionPlacement
import com.example.chat.core.templates.UpdatesLayoutMode
import com.example.chat.core.templates.TemplateComponentType
import com.example.chat.core.templates.TemplateController
import com.example.chat.core.templates.TemplateRuntime
import com.example.chat.ui.kit.ChatyAppBar
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline

/** Exact port of Dart TemplateComponentType.icon for THIS screen
 *  (component_override_screen.dart reads component.icon). Private and uniquely
 *  named: the templates-settings screen's shared componentGlyph maps a
 *  different Dart source (_glyphForComponent) and is not interchangeable. */
private fun componentOverrideIcon(iconName: String): ImageVector = when (iconName) {
    "dock_rounded" -> Icons.Rounded.Dock
    "home_outlined" -> Icons.Outlined.Home
    "format_list_bulleted_rounded" -> Icons.Rounded.FormatListBulleted
    "chat_bubble_outline_rounded" -> Icons.Rounded.ChatBubbleOutline
    "input_rounded" -> Icons.Rounded.Input
    "auto_stories_outlined" -> Icons.Outlined.AutoStories
    "person_outline_rounded" -> Icons.Rounded.PersonOutline
    "call_outlined" -> Icons.Outlined.Call
    else -> Icons.Rounded.Circle
}

/** Dart destinationIcon switch. */
private fun destinationIcon(id: String): ImageVector = when (id) {
    "chats" -> Icons.Rounded.ChatBubbleOutline
    "groups" -> Icons.Outlined.Groups
    "updates" -> Icons.Outlined.AutoStories
    "tasks" -> Icons.Rounded.Checklist
    "calls" -> Icons.Outlined.Call
    "settings" -> Icons.Outlined.Settings
    "desktop" -> Icons.Outlined.Devices
    else -> Icons.Rounded.Circle
}

/** Dart: tmpl.navigation.centerActionIcon ?? Icons.camera_alt_rounded. */
private fun centerActionIcon(iconName: String?): ImageVector = when (iconName) {
    null, "camera", "camera_alt_rounded" -> Icons.Rounded.CameraAlt
    else -> Icons.Rounded.CameraAlt
}

/** Observes a listener-style controller (AppearanceVariantController /
 *  TemplateController pattern) as a state counter. */
@Composable
private fun rememberControllerVersion(controller: TemplateController): Int {
    var version by remember(controller) { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val l: () -> Unit = { version += 1 }
        controller.addListener(l)
        onDispose { controller.removeListener(l) }
    }
    return version
}

/**
 * Port of lib/features/settings/templates/component_override_screen.dart.
 * Compares every registered template for one component and applies/removes a
 * per-component override. The Dart screen passed the three controllers so the
 * change synced live; the Kotlin TemplateController syncs through
 * [runtime] sinks instead (TemplateRuntimeAdapter wires the real ones).
 * Snackbars surface through [onMessage].
 */
@Composable
fun ComponentOverrideScreen(
    component: TemplateComponentType,
    templateController: TemplateController,
    onBack: () -> Unit,
    runtime: TemplateRuntime = TemplateRuntime(),
    onMessage: (String) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    rememberControllerVersion(templateController)

    val currentEffective = templateController.resolveTemplateFor(component)
    val isOverridden = templateController.config.isOverridden(component)
    val baseTemplate = templateController.baseTemplate

    Column(Modifier.background(colors.background)) {
        ChatyAppBar(
            title = "${component.title} Template",
            onBack = onBack,
            backgroundColor = colors.surface,
            actions = {
                if (isOverridden) {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                templateController.removeComponentOverride(component, runtime)
                                onMessage(overrideResetSuccess(component.title))
                            } catch (t: Throwable) {
                                onMessage(overrideErrorText(t))
                            }
                        }
                    }) { Text("Reset") }
                }
            },
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1040.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp,
                ),
            ) {
                item {
                    // Component header card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceSecondary, RoundedCornerShape(16.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                componentOverrideIcon(component.iconName),
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                component.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.W700,
                                color = colors.foreground,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                component.description,
                                fontSize = 12.sp,
                                color = colors.foregroundSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    // Dart source literal keeps the trailing space and never
                    // appends the component name; parity kept.
                    Text(
                        "Select Variant for ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W800,
                        color = colors.foreground,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                items(ChatyTemplateRegistry.list) { tmpl ->
                    val isCurrentlyUsed = currentEffective == tmpl.id
                    val isFromBase = !isOverridden && baseTemplate == tmpl.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .background(colors.surfaceSecondary, RoundedCornerShape(18.dp))
                            .border(
                                width = if (isCurrentlyUsed) 2.dp else 1.dp,
                                color = if (isCurrentlyUsed) colors.primary else colors.border,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tmpl.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W800,
                                color = colors.foreground,
                            )
                            overrideBadge(isCurrentlyUsed, isFromBase)?.let { badge ->
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.W700,
                                    color = colors.primary,
                                    modifier = Modifier
                                        .background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(tmpl.subtitle, fontSize = 12.sp, color = colors.foregroundSecondary)
                        Spacer(Modifier.height(12.dp))
                        ComponentSnippet(component, tmpl)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        templateController.applyComponent(component, tmpl.id, runtime)
                                        onMessage(overrideApplySuccess(tmpl.name, component.title))
                                    } catch (t: Throwable) {
                                        onMessage(overrideErrorText(t))
                                    }
                                }
                            },
                            enabled = !isCurrentlyUsed,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = Color.White,
                                disabledContainerColor = colors.surfaceElevated,
                                disabledContentColor = colors.foregroundSecondary,
                            ),
                        ) {
                            Text(if (isCurrentlyUsed) "Currently Active" else "Use This Template")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TinyText(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val colors = LocalAppColors.current
    Text(
        text,
        modifier = modifier,
        fontSize = 10.5.sp,
        color = colors.foregroundSecondary,
        fontWeight = FontWeight.W600,
        textAlign = textAlign,
    )
}

@Composable
private fun PreviewSurface(radius: Int = 14, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(radius.dp))
            .border(1.dp, colors.border, RoundedCornerShape(radius.dp))
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun CircleAction(icon: ImageVector, color: Color? = null, size: Int = 17) {
    val colors = LocalAppColors.current
    val tint = color ?: colors.primary
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(tint.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun Bar(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, alpha: Float = 1f) {
    val colors = LocalAppColors.current
    Box(Modifier.size(width, height).background(colors.border.copy(alpha = alpha)))
}

@Composable
private fun ComponentSnippet(component: TemplateComponentType, tmpl: ChatyTemplateDefinition) {
    when (component) {
        TemplateComponentType.navigation -> NavigationSnippet(tmpl)
        TemplateComponentType.composer -> ComposerSnippet(tmpl)
        TemplateComponentType.conversation -> ConversationSnippet(tmpl)
        TemplateComponentType.home -> HomeSnippet(tmpl)
        TemplateComponentType.chatList -> ChatListSnippet(tmpl)
        TemplateComponentType.updates -> UpdatesSnippet(tmpl)
        TemplateComponentType.profile -> ProfileSnippet(tmpl)
        TemplateComponentType.calls -> CallsSnippet(tmpl)
    }
}

@Composable
private fun NavigationSnippet(tmpl: ChatyTemplateDefinition) {
    val colors = LocalAppColors.current
    val destinations = tmpl.navigation.primaryDestinationIds
    PreviewSurface {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                destinations.take(4).forEach { id ->
                    val active = id == destinations.firstOrNull()
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            destinationIcon(id),
                            contentDescription = null,
                            tint = if (active) colors.primary else colors.foregroundSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            navigationDestinationLabel(id),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.W600,
                            color = if (active) colors.primary else colors.foregroundSecondary,
                        )
                    }
                }
            }
            if (tmpl.navigation.hasCenterAction) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        centerActionIcon(tmpl.navigation.centerActionIcon),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    TinyText(centerActionLabel(tmpl.navigation.centerActionId))
                }
            }
            Spacer(Modifier.height(8.dp))
            TinyText(
                navigationSummary(tmpl.navigation.layout.name, tmpl.navigation.height),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ComposerSnippet(tmpl: ChatyTemplateDefinition) {
    val colors = LocalAppColors.current
    val composer = tmpl.composer
    val input: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .heightIn(min = 38.dp)
                .background(colors.background, RoundedCornerShape(composer.cornerRadius.dp))
                .border(1.dp, colors.border, RoundedCornerShape(composer.cornerRadius.dp))
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.EmojiEmotions,
                contentDescription = null,
                tint = colors.foregroundSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            TinyText("Message…")
        }
    }
    val attachment = @Composable { CircleAction(Icons.Rounded.Add, size = 18) }
    val camera = @Composable { CircleAction(Icons.Outlined.CameraAlt, size = 16) }
    val voice = @Composable {
        CircleAction(if (composer.showVoiceLock) Icons.Rounded.MicNone else Icons.Rounded.GraphicEq, size = 16)
    }
    val send = @Composable { CircleAction(Icons.Rounded.ArrowUpward, color = colors.primary) }

    PreviewSurface {
        if (composer.actionPlacement == ComposerActionPlacement.powerRow) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { input() }
                    Spacer(Modifier.width(8.dp))
                    send()
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    attachment()
                    if (composer.showCameraShortcut) camera()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (composer.showVoiceLock) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = colors.foregroundSecondary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        TinyText("Power row")
                    }
                    voice()
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (composer.actionPlacement == ComposerActionPlacement.split && composer.showCameraShortcut) {
                    camera()
                    Spacer(Modifier.width(6.dp))
                    attachment()
                    Spacer(Modifier.width(6.dp))
                } else {
                    attachment()
                    if (composer.showCameraShortcut) {
                        Spacer(Modifier.width(6.dp))
                        camera()
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Box(Modifier.weight(1f)) { input() }
                Spacer(Modifier.width(7.dp))
                send()
            }
        }
    }
}

@Composable
private fun ConversationSnippet(tmpl: ChatyTemplateDefinition) {
    val colors = LocalAppColors.current
    PreviewSurface {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleAction(Icons.Rounded.PersonOutline, size = 15)
                Spacer(Modifier.width(7.dp))
                TinyText("Conversation preview")
                Spacer(Modifier.weight(1f))
                TinyText("12:04")
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .background(colors.primary, RoundedCornerShape(tmpl.conversation.bubbleCornerRadius.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Message sent",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W600,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.DoneAll,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TinyText(conversationSummary(tmpl.conversation.bubbleStyle, tmpl.conversation.tickStyle))
            if (tmpl.conversation.showAvatarInGroup) {
                Spacer(Modifier.height(4.dp))
                TinyText("Group sender avatars shown")
            }
        }
    }
}

@Composable
private fun HomeSnippet(tmpl: ChatyTemplateDefinition) {
    val colors = LocalAppColors.current
    val circular = tmpl.home.storiesStyle.lowercase() == "circular"
    PreviewSurface {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ChatBubbleOutline,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    tmpl.home.headerStyle.name,
                    color = colors.foreground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W700,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.foregroundSecondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = colors.foregroundSecondary, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(10.dp))
            if (tmpl.home.showStoriesStrip) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tints = listOf(colors.primary, colors.info, colors.success, colors.accent)
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .padding(end = 7.dp)
                                .size(23.dp)
                                .background(
                                    tints[i].copy(alpha = 0.25f),
                                    if (circular) CircleShape else RoundedCornerShape(7.dp),
                                ),
                        )
                    }
                    TinyText(tmpl.home.storiesStyle)
                }
            } else {
                TinyText("Stories strip hidden")
            }
            Spacer(Modifier.height(8.dp))
            repeat(2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp)
                        .height(homeRowHeightDp(tmpl.home.homeStylePreset).dp)
                        .background(colors.background, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleAction(Icons.Rounded.PersonOutline, size = 13)
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f).height(4.dp).background(colors.border))
                }
            }
        }
    }
}

@Composable
private fun ChatListSnippet(tmpl: ChatyTemplateDefinition) {
    val colors = LocalAppColors.current
    val avatarRadius = chatListAvatarRadiusDp(tmpl.chatList.avatarShape)
    val rowPadding = chatListRowPaddingDp(tmpl.chatList.density.name)
    PreviewSurface {
        Column(Modifier.fillMaxWidth()) {
            repeat(2) { i ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = rowPadding.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                (if (i == 0) colors.primary else colors.info).copy(alpha = 0.22f),
                                RoundedCornerShape(avatarRadius.dp),
                            ),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Bar(105.dp, 5.dp)
                        Spacer(Modifier.height(5.dp))
                        Bar(150.dp, 3.dp, alpha = 0.7f)
                    }
                    if (tmpl.chatList.showPresenceBadge) {
                        Box(Modifier.size(7.dp).background(colors.success, CircleShape))
                    }
                }
            }
            TinyText(chatListSummary(tmpl.chatList.density.name, tmpl.chatList.itemHeight))
        }
    }
}

@Composable
private fun UpdatesSnippet(tmpl: ChatyTemplateDefinition) {
    val colors = LocalAppColors.current
    PreviewSurface {
        Column(Modifier.fillMaxWidth()) {
            when (tmpl.updates.layoutMode) {
                UpdatesLayoutMode.circularRail -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        repeat(4) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .border(2.dp, colors.primary, CircleShape)
                                        .background(colors.primary.copy(alpha = 0.12f), CircleShape),
                                )
                                Spacer(Modifier.height(5.dp))
                                Bar(26.dp, 3.dp)
                            }
                        }
                    }
                }
                UpdatesLayoutMode.minimalList -> {
                    Column {
                        repeat(2) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircleAction(Icons.Outlined.AutoStories, size = 15)
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f).height(4.dp).background(colors.border))
                            }
                        }
                    }
                }
                else -> {
                    Row {
                        val count = if (tmpl.updates.layoutMode == UpdatesLayoutMode.gridTiles) 3 else 2
                        repeat(count) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                                    .height(48.dp)
                                    .background(colors.primary.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TinyText(updatesSummary(tmpl.updates.layoutMode.name, tmpl.updates.enableStatusAudio))
        }
    }
}

@Composable
private fun ProfileSnippet(tmpl: ChatyTemplateDefinition) {
    val colors = LocalAppColors.current
    val circle = tmpl.profile.avatarShape == "circle"
    PreviewSurface {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(profileBannerHeightDp(tmpl.profile.headerStyle.name).dp)
                    .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            )
            Box(
                modifier = Modifier
                    .offset(y = (-14).dp)
                    .size(30.dp)
                    .background(
                        colors.primary.copy(alpha = 0.2f),
                        if (circle) CircleShape else RoundedCornerShape(8.dp),
                    )
                    .border(2.dp, colors.surface, if (circle) CircleShape else RoundedCornerShape(8.dp)),
            )
            TinyText(
                tmpl.profile.headerStyle.name,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (tmpl.profile.showStatsGrid) {
                Spacer(Modifier.height(8.dp))
                Row {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .height(18.dp)
                                .background(colors.background, RoundedCornerShape(6.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsSnippet(tmpl: ChatyTemplateDefinition) {
    val colors = LocalAppColors.current
    PreviewSurface {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleAction(Icons.Outlined.Call, size = 15)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Bar(85.dp, 5.dp)
                    Spacer(Modifier.height(5.dp))
                    Bar(57.dp, 3.dp, alpha = 0.7f)
                }
                TinyText(callsIslandLabel(tmpl.calls.enableFloatingIsland))
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(tmpl.calls.controlBarCornerRadius.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                listOf(Icons.Rounded.MicNone, Icons.Rounded.VolumeUp, Icons.Rounded.CallEnd).forEach { icon ->
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
