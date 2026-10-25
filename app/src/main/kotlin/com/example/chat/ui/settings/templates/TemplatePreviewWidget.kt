package com.example.chat.ui.settings.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.templates.ChatyTemplateDefinition
import com.example.chat.core.templates.ChatyTemplateId
import com.example.chat.core.templates.HomeHeaderStyle
import com.example.chat.core.templates.NavigationLayoutType
import com.example.chat.ui.theme.AppColors
import com.example.chat.ui.theme.LocalAppColors

/** Port of template_preview_widget.dart TemplateCompositePreview (api
 *  3bee780d). Lightweight deterministic previews; each template deliberately
 *  has its own composition so choosing a template communicates the actual
 *  layout identity. Never starts app data streams or touches persisted user
 *  data. UNCOMPILED (Compose; owner CI + pixels verify).
 *  The media tile ports Dart's FittedBox(fit: BoxFit.scaleDown,
 *  alignment: topLeft) exactly via [ScaleDownBox]: the icon+label column is
 *  measured at natural size and layer-scaled down (never up) to fit. */
@Composable
fun TemplateCompositePreview(
    template: ChatyTemplateDefinition,
    isSelected: Boolean = false,
    height: Int = 200,
) {
    val colors = LocalAppColors.current
    val accent = Color(templatePreviewAccent(template.id))
    Column(
        Modifier
            .height(height.dp)
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 15.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = accent.copy(alpha = 0.18f),
                        spotColor = accent.copy(alpha = 0.18f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceSecondary)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else colors.border,
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        PreviewHeader(template, colors, accent)
        Box(Modifier.weight(1f)) {
            when (template.id) {
                ChatyTemplateId.visualSocial -> VisualSocialLayout(template, colors, accent)
                ChatyTemplateId.stream -> StreamLayout(colors, accent)
                ChatyTemplateId.cameraFirst -> CameraFirstLayout(colors, accent)
                ChatyTemplateId.messageFirst -> MessageFirstLayout(template, colors, accent)
                ChatyTemplateId.powerChat -> PowerChatLayout(colors, accent)
                ChatyTemplateId.community -> CommunityLayout(colors, accent)
            }
        }
        PreviewNavigation(template, colors, accent)
    }
}

@Composable
private fun PreviewHeader(template: ChatyTemplateDefinition, colors: AppColors, accent: Color) {
    val headerStyle = template.home.headerStyle
    val prominent = headerStyle == HomeHeaderStyle.prominentIdentity
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (prominent) 38.dp else 32.dp)
            .background(colors.surface)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (prominent) 25.dp else 21.dp)
                .then(
                    if (template.chatList.avatarShape == "circle") {
                        Modifier.background(accent.copy(alpha = 0.18f), CircleShape)
                    } else {
                        Modifier.background(accent.copy(alpha = 0.18f), RoundedCornerShape(7.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (template.id) {
                    ChatyTemplateId.community -> Icons.Rounded.Diversity3
                    ChatyTemplateId.cameraFirst -> Icons.Rounded.CameraAlt
                    else -> Icons.Rounded.ChatBubble
                },
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = accent,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            when (template.id) {
                ChatyTemplateId.community -> "Spaces"
                ChatyTemplateId.visualSocial -> "Your day"
                ChatyTemplateId.cameraFirst -> "Create"
                ChatyTemplateId.powerChat -> "Chat tools"
                else -> template.name
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = if (prominent) 12.sp else 10.5.sp,
            fontWeight = FontWeight.W800,
            color = colors.foreground,
        )
        if (headerStyle == HomeHeaderStyle.searchForward) {
            Box(
                Modifier
                    .width(74.dp)
                    .height(20.dp)
                    .background(colors.surfaceSecondary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.foregroundSecondary)
            }
        } else {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.foregroundSecondary)
            Spacer(Modifier.width(9.dp))
            Icon(Icons.Rounded.MoreHoriz, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.foregroundSecondary)
        }
    }
}

@Composable
private fun VisualSocialLayout(template: ChatyTemplateDefinition, colors: AppColors, accent: Color) {
    Column(Modifier.padding(start = 9.dp, top = 7.dp, end = 9.dp, bottom = 6.dp)) {
        Row {
            for (i in 0 until 5) {
                val size = if (i == 0) 31.dp else 27.dp
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(size)
                            .border(
                                width = 2.dp,
                                color = if (i == 0) accent else accent.copy(alpha = 0.55f),
                                shape = CircleShape,
                            )
                            .background(if (i == 0) accent.copy(alpha = 0.14f) else colors.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (i == 0) Icons.Rounded.Add else Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = accent,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(6f)) {
                MediaTile(colors, accent, Icons.Rounded.Landscape, "Highlights", tall = true)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(5f)) {
                Box(Modifier.weight(1f)) {
                    MediaTile(colors, accent, Icons.Rounded.MusicNote, "Audio")
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.weight(1f)) {
                    MediaTile(colors, accent, Icons.Rounded.Favorite, "Friends")
                }
            }
        }
    }
}

@Composable
private fun StreamLayout(colors: AppColors, accent: Color) {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PreviewChip("ALL", accent, selected = true)
            Spacer(Modifier.width(5.dp))
            PreviewChip("UNREAD", accent)
            Spacer(Modifier.width(5.dp))
            PreviewChip("GROUPS", accent)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.foregroundSecondary)
        }
        Spacer(Modifier.height(3.dp))
        Column(Modifier.weight(1f)) {
            for (i in 0 until 3) {
                Row(
                    Modifier
                        .weight(1f)
                        .then(
                            if (i == 2) {
                                Modifier
                            } else {
                                Modifier.bottomBorder(colors.border, 0.6.dp)
                            },
                        )
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (i == 1) Icons.Rounded.Groups else Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = accent,
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .width(if (i == 0) 75.dp else 60.dp)
                                .height(5.dp)
                                .background(colors.foreground.copy(alpha = 0.75f), RoundedCornerShape(3.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(if (i == 2) 82.dp else 104.dp)
                                .height(3.dp)
                                .background(colors.foregroundSecondary.copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
                        )
                    }
                    Text(
                        if (i == 0) "now" else if (i == 1) "2m" else "8m",
                        fontSize = 7.sp,
                        color = colors.foregroundSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraFirstLayout(colors: AppColors, accent: Color) {
    Row(Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 6.dp)) {
        Box(
            Modifier
                .weight(5f)
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(13.dp),
                )
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.35f), colors.surface, accent.copy(alpha = 0.12f)),
                    ),
                    RoundedCornerShape(13.dp),
                ),
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp)
                    .size(13.dp),
                tint = accent,
            )
            Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(38.dp)
                        .border(3.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(27.dp)
                            .background(accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    }
                }
            }
            Text(
                "QUICK CAPTURE",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 7.dp),
                fontSize = 7.sp,
                fontWeight = FontWeight.W900,
                letterSpacing = 0.7.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(4f), verticalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(colors, accent, Icons.Rounded.PhotoLibrary, "Gallery")
            QuickAction(colors, accent, Icons.Rounded.Bolt, "Effects")
            QuickAction(colors, accent, Icons.Rounded.Send, "Send")
        }
    }
}

@Composable
private fun MessageFirstLayout(template: ChatyTemplateDefinition, colors: AppColors, accent: Color) {
    Column(
        Modifier.padding(start = 11.dp, top = 8.dp, end = 11.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        MessageLine(template, colors, accent, "Alex Rivera", "Are we still on for 6?", "12:45")
        MessageLine(template, colors, accent, "Design Team", "The new build is ready", "12:30", group = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(
                Modifier
                    .background(accent, RoundedCornerShape(template.conversation.bubbleCornerRadius.dp))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sounds good!", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.W700)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun PowerChatLayout(colors: AppColors, accent: Color) {
    Column(Modifier.padding(start = 9.dp, top = 6.dp, end = 9.dp, bottom = 6.dp)) {
        Row {
            PreviewChip("All", accent, selected = true)
            Spacer(Modifier.width(4.dp))
            PreviewChip("Direct", accent)
            Spacer(Modifier.width(4.dp))
            PreviewChip("Groups", accent)
            Spacer(Modifier.width(4.dp))
            PreviewChip("Pinned", accent)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.weight(1f)) {
            Column(
                Modifier
                    .width(25.dp)
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.ChatBubble, contentDescription = null, modifier = Modifier.size(12.dp), tint = accent)
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.foregroundSecondary)
                Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.foregroundSecondary)
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                MiniDenseRow(colors, accent, "Operations")
                MiniDenseRow(colors, accent, "Maya")
                MiniDenseRow(colors, accent, "Project group")
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(17.dp)
                .background(colors.surface, RoundedCornerShape(7.dp))
                .border(1.dp, colors.border, RoundedCornerShape(7.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(10.dp), tint = accent)
            Spacer(Modifier.width(5.dp))
            Text(
                "Message or command…",
                modifier = Modifier.weight(1f),
                fontSize = 7.sp,
                color = colors.foregroundSecondary,
            )
            Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(10.dp), tint = accent)
        }
    }
}

@Composable
private fun CommunityLayout(colors: AppColors, accent: Color) {
    Column(Modifier.padding(start = 10.dp, top = 7.dp, end = 10.dp, bottom = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(31.dp)
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.08f))),
                    RoundedCornerShape(9.dp),
                )
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(9.dp))
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Hub, contentDescription = null, modifier = Modifier.size(17.dp), tint = accent)
            Spacer(Modifier.width(7.dp))
            Text("Design Community", modifier = Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.W800)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = accent)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) { SpaceCard(colors, accent, Icons.Rounded.Forum, "General", "8 new") }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) { SpaceCard(colors, accent, Icons.Rounded.Campaign, "Updates", "3 new") }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) { SpaceCard(colors, accent, Icons.Rounded.Groups, "Teams", "5 new") }
        }
    }
}

@Composable
private fun PreviewNavigation(template: ChatyTemplateDefinition, colors: AppColors, accent: Color) {
    val layout = template.navigation.layout
    val isFloating = layout == NavigationLayoutType.floatingPill
    val isIdentity = layout == NavigationLayoutType.identityBar
    val isUtility = layout == NavigationLayoutType.utilityBar
    val hasCenter = template.navigation.hasCenterAction || layout == NavigationLayoutType.centerActionDock
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (layout == NavigationLayoutType.centerActionDock) 36.dp else 31.dp)
            .background(colors.surface)
            .topBorder(colors.border, 0.7.dp)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewNavItem(template, colors, accent, Icons.Rounded.ChatBubble, "Chats", active = true, identity = isIdentity)
        PreviewNavItem(template, colors, accent, Icons.Rounded.AutoStories, "Updates", active = false, identity = isIdentity)
        if (hasCenter) {
            Box(
                Modifier
                    .size(28.dp)
                    .shadow(5.dp, CircleShape, ambientColor = accent.copy(alpha = 0.25f), spotColor = accent.copy(alpha = 0.25f))
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Dart: template.navigation.centerActionIcon ??
                // Icons.camera_alt_rounded; the canonical String semantic is
                // 'camera_alt_rounded' whenever set.
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
            }
        }
        if (isUtility) {
            PreviewNavItem(template, colors, accent, Icons.Rounded.GridView, "Tools", active = false, identity = false)
        }
        PreviewNavItem(template, colors, accent, Icons.Rounded.Call, "Calls", active = false, identity = isIdentity)
        if (!isFloating && !isIdentity) {
            PreviewNavItem(template, colors, accent, Icons.Rounded.Tune, "More", active = false, identity = false)
        }
        if (isIdentity) {
            PreviewNavItem(template, colors, accent, Icons.Rounded.Groups, "Spaces", active = false, identity = true)
        }
    }
}

@Composable
private fun PreviewNavItem(
    template: ChatyTemplateDefinition,
    colors: AppColors,
    accent: Color,
    icon: ImageVector,
    label: String,
    active: Boolean,
    identity: Boolean,
) {
    val foreground = if (active) accent else colors.foregroundSecondary
    Row(
        Modifier
            .then(
                if (active && template.navigation.layout == NavigationLayoutType.floatingPill) {
                    Modifier.background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = if (identity) 8.dp else 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = foreground)
        if (identity) {
            Spacer(Modifier.width(3.dp))
            Text(label, fontSize = 7.sp, color = foreground, fontWeight = FontWeight.W700)
        }
    }
}

@Composable
private fun MediaTile(
    colors: AppColors,
    accent: Color,
    icon: ImageVector,
    label: String,
    tall: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.25f), colors.surface)),
                RoundedCornerShape(if (tall) 12.dp else 9.dp),
            )
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(if (tall) 12.dp else 9.dp))
            .padding(6.dp),
    ) {
        // Dart: FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.topLeft)
        // around the icon+label column - measured scale-down, never up.
        ScaleDownBox(Modifier.fillMaxSize()) {
            Column {
                Icon(icon, contentDescription = null, modifier = Modifier.size(if (tall) 17.dp else 13.dp), tint = accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    fontSize = if (tall) 9.sp else 7.sp,
                    color = colors.foreground,
                    fontWeight = FontWeight.W800,
                )
            }
        }
    }
}

/** Port of FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.topLeft):
 *  the child is measured at its natural (unbounded) size, then scaled down -
 *  never up - so it fits the incoming constraints, anchored top-start. The
 *  layout itself takes the constrained child size like RenderFittedBox. */
@Composable
private fun ScaleDownBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val width = if (constraints.hasBoundedWidth) minOf(constraints.maxWidth, placeable.width) else placeable.width
        val height = if (constraints.hasBoundedHeight) minOf(constraints.maxHeight, placeable.height) else placeable.height
        val scale = minOf(
            1f,
            if (placeable.width > 0) width.toFloat() / placeable.width else 1f,
            if (placeable.height > 0) height.toFloat() / placeable.height else 1f,
        )
        layout(width, height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun PreviewChip(label: String, accent: Color, selected: Boolean = false) {
    Text(
        label,
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        fontSize = 6.5.sp,
        fontWeight = FontWeight.W800,
        color = if (selected) accent else Color(0xFF89929E),
    )
}

@Composable
private fun QuickAction(colors: AppColors, accent: Color, icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(25.dp)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = accent)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 8.sp, color = colors.foreground, fontWeight = FontWeight.W700)
    }
}

@Composable
private fun MessageLine(
    template: ChatyTemplateDefinition,
    colors: AppColors,
    accent: Color,
    name: String,
    snippet: String,
    time: String,
    group: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(24.dp)
                .then(
                    if (template.chatList.avatarShape == "circle") {
                        Modifier.background(accent.copy(alpha = 0.13f), CircleShape)
                    } else {
                        Modifier.background(accent.copy(alpha = 0.13f), RoundedCornerShape(7.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (group) Icons.Rounded.Groups else Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = accent,
            )
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 8.5.sp,
                    color = colors.foreground,
                    fontWeight = FontWeight.W800,
                )
                Text(time, fontSize = 7.sp, color = colors.foregroundSecondary)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                snippet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 7.5.sp,
                color = colors.foregroundSecondary,
            )
        }
    }
}

@Composable
private fun MiniDenseRow(colors: AppColors, accent: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(18.dp)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(10.dp), tint = accent)
        }
        Spacer(Modifier.width(5.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 7.5.sp,
                color = colors.foreground,
                fontWeight = FontWeight.W700,
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(58.dp)
                    .height(3.dp)
                    .background(colors.foregroundSecondary.copy(alpha = 0.38f), RoundedCornerShape(2.dp)),
            )
        }
        Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(9.dp), tint = colors.foregroundSecondary)
    }
}

@Composable
private fun SpaceCard(colors: AppColors, accent: Color, icon: ImageVector, title: String, badge: String) {
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface, RoundedCornerShape(9.dp))
            .border(1.dp, colors.border, RoundedCornerShape(9.dp))
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = accent)
        }
        Spacer(Modifier.weight(1f))
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 8.sp,
            fontWeight = FontWeight.W800,
            color = colors.foreground,
        )
        Spacer(Modifier.height(2.dp))
        Text(badge, fontSize = 6.5.sp, color = accent, fontWeight = FontWeight.W700)
    }
}

/** One-sided borders used by the Dart BoxDecoration(Border(bottom/top:)). */
private fun Modifier.bottomBorder(color: Color, width: androidx.compose.ui.unit.Dp): Modifier =
    this.then(
        Modifier.drawBehind {
            val stroke = width.toPx()
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(0f, size.height - stroke / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height - stroke / 2f),
                strokeWidth = stroke,
            )
        },
    )

private fun Modifier.topBorder(color: Color, width: androidx.compose.ui.unit.Dp): Modifier =
    this.then(
        Modifier.drawBehind {
            val stroke = width.toPx()
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(0f, stroke / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, stroke / 2f),
                strokeWidth = stroke,
            )
        },
    )
