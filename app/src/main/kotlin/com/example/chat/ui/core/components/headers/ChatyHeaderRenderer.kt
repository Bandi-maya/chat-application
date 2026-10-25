package com.example.chat.ui.core.components.headers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.theme.LocalAppColors

/** Port of ChatyHeaderData (lib/ui/core/components/headers/chaty_header_renderer.dart, api 3bee780d). */
class ChatyHeaderData(
    val title: String,
    val subtitle: String? = null,
    val avatar: (@Composable () -> Unit)? = null,
    val actions: (@Composable RowScope.() -> Unit)? = null,
    val leading: (@Composable () -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
    val onTitleTap: (() -> Unit)? = null,
    val selectionCount: Int = 0,
    val isSelectionMode: Boolean = false,
    val selectionActions: (@Composable RowScope.() -> Unit)? = null,
    val onClearSelection: (() -> Unit)? = null,
    val callDurationLabel: String? = null,
    val isCallActive: Boolean = false,
)

/** Preferred height per variant (Dart preferredSize: large_context 74, compact 48, else kToolbarHeight 56). */
fun chatyHeaderPreferredHeight(variantId: String): Dp = when (variantId) {
    "large_context" -> 74.dp
    "compact" -> 48.dp
    else -> 56.dp
}

/**
 * Variant renderer. [canNavigateBack] replaces Dart's `Navigator.of(context).canPop()` (auto back button only
 * when the stack can pop and no explicit [ChatyHeaderData.leading]); the owner passes the NavController state.
 * profile_led delegates to large_context exactly as in Dart.
 */
@Composable
fun ChatyHeaderRenderer(
    variantId: String,
    data: ChatyHeaderData,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    foregroundColor: Color? = null,
    elevation: Dp = 0.dp,
    canNavigateBack: Boolean = true,
) {
    val colors = LocalAppColors.current
    if (data.isSelectionMode) {
        val bg = backgroundColor ?: colors.surface
        val fg = foregroundColor ?: colors.foreground
        HeaderBar(modifier, bg, fg, 1.dp, 56.dp,
            leading = {
                IconButton(onClick = { data.onClearSelection?.invoke() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close selection", tint = fg)
                }
            },
            title = { Text("${data.selectionCount} selected", color = fg, fontSize = 17.sp, fontWeight = FontWeight.W700) },
            actions = data.selectionActions)
        return
    }
    if (data.isCallActive) {
        HeaderBar(modifier, colors.success, Color.White, 2.dp, 56.dp,
            leading = leadingFor(data, Color.White, canNavigateBack),
            title = {
                Column(verticalArrangement = Arrangement.Center) {
                    Text(data.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W700)
                    Text(data.callDurationLabel ?: "Call in progress", color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp, fontWeight = FontWeight.W500)
                }
            },
            actions = data.actions)
        return
    }
    val bg = backgroundColor ?: colors.surface
    val fg = foregroundColor ?: colors.foreground
    when (variantId) {
        "large_context", "profile_led" -> HeaderBar(modifier, bg, fg, elevation, 74.dp,
            leading = leadingFor(data, fg, canNavigateBack),
            title = { IdentityTitle(data, fg, avatarSize = 48.dp, gap = 12.dp, titleSp = 18f, titleWeight = FontWeight.W800,
                subtitleSp = 12.5f, letterSpacing = 0f) },
            actions = data.actions)
        "compact" -> HeaderBar(modifier, bg, fg, elevation, 48.dp,
            leading = leadingFor(data, fg, canNavigateBack),
            title = { Text(data.title, color = fg, fontSize = 14.5.sp, fontWeight = FontWeight.W700) },
            actions = data.actions)
        "search_first" -> HeaderBar(modifier, bg, fg, elevation, 56.dp,
            leading = leadingFor(data, fg, canNavigateBack),
            title = { Text(data.title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.W700) },
            actions = data.actions)
        "glass_strip" -> Box(modifier.clipToBounds()) {
            // Dart: BackdropFilter blur(16,16) over the content behind + surface @ 0.75 alpha. Compose has no
            // portable backdrop blur; the 0.75-alpha translucent surface is kept, blur is a documented limitation.
            HeaderBar(Modifier, (backgroundColor ?: colors.surface).copy(alpha = 0.75f), fg, 0.dp, 56.dp,
                leading = leadingFor(data, fg, canNavigateBack),
                title = { Text(data.title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.W700) },
                actions = data.actions)
        }
        else -> HeaderBar(modifier, bg, fg, elevation, 56.dp,
            leading = leadingFor(data, fg, canNavigateBack),
            leadingWidth = 54.dp,
            title = { IdentityTitle(data, fg, avatarSize = null, gap = 10.dp, titleSp = 16f, titleWeight = FontWeight.W700,
                subtitleSp = 12f, letterSpacing = -0.2f) },
            actions = data.actions)
    }
}

private fun leadingFor(data: ChatyHeaderData, color: Color, canNavigateBack: Boolean): (@Composable () -> Unit)? {
    data.leading?.let { return it }
    if (!canNavigateBack) return null
    // ChatyBackButton (Kotlin kit) has no tint parameter; Dart passed `color`. Behaviorally identical action.
    return { ChatyBackButton(onPressed = { data.onBack?.invoke() }) }
}

@Composable
private fun IdentityTitle(
    data: ChatyHeaderData, fg: Color, avatarSize: Dp?, gap: Dp, titleSp: Float,
    titleWeight: FontWeight, subtitleSp: Float, letterSpacing: Float,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { data.onTitleTap?.invoke() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        data.avatar?.let { av ->
            if (avatarSize != null) Box(Modifier.size(avatarSize)) { av() } else av()
            Spacer(Modifier.width(gap))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(data.title, color = fg, fontSize = titleSp.sp, fontWeight = titleWeight, maxLines = 1,
                overflow = TextOverflow.Ellipsis, letterSpacing = letterSpacing.sp)
            data.subtitle?.let {
                Text(it, color = colors.foregroundSecondary, fontSize = subtitleSp.sp, fontWeight = FontWeight.W500,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Material AppBar equivalent: leading slot (default 56dp wide), title slot, trailing actions, optional shadow. */
@Composable
private fun HeaderBar(
    modifier: Modifier,
    bg: Color,
    fg: Color,
    elevation: Dp,
    height: Dp,
    leading: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
    leadingWidth: Dp = 56.dp,
) {
    Row(
        modifier.fillMaxWidth().shadow(elevation).background(bg).height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Box(Modifier.width(leadingWidth), contentAlignment = Alignment.Center) { leading() }
        else Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { title() }
        if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
