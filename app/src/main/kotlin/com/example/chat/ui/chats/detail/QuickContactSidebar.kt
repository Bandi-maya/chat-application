@file:OptIn(ExperimentalFoundationApi::class)

package com.example.chat.ui.chats.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.chats.home.HomeAvatar
import com.example.chat.ui.chats.home.parseAvatarColorHex

/** One contact row in the quick-contact rail (Dart dataStore.contacts entry:
 *  displayName, avatarInitials, avatarColorHex; the id routes the tap). */
data class QuickContactEntry(
    val id: String,
    val displayName: String,
    val avatarInitials: String?,
    val avatarColorHex: String?,
)

/** Exact port of the quick-contact sidebar in chat_detail_screen.dart:2222-2286:
 *  62dp rail, contacts top- or bottom-anchored (ListView reversed when Bottom),
 *  38dp avatars in the home avatar shape, tap opens (or creates) the direct
 *  conversation. Shown only when conversation.enableQuickContactSidebar is on and
 *  the screen is at least 720dp wide; the caller owns that gate. The Dart
 *  long-press Tooltip (name, 12sp, quickContactTextColor on Flutter's default
 *  grey700/90% bubble, radius 4) is ported as a small custom Popup because this
 *  project's m3 version ships no PlainTooltip on this platform
 *  (ThemePreviewCard.kt:109). */
@Composable
fun QuickContactSidebar(
    theme: ThemeConfig,
    contacts: List<QuickContactEntry>,
    backgroundColor: Color,
    textColor: Color,
    bottomAnchored: Boolean,
    avatarShape: String,
    onOpen: (QuickContactEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(62.dp)
            .fillMaxHeight()
            .background(backgroundColor),
        verticalArrangement = if (bottomAnchored) Arrangement.Bottom else Arrangement.Top,
    ) {
        // Dart: Expanded(ListView(reverse: bottomAnchored)).
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = bottomAnchored,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(contacts, key = { it.id }) { contact ->
                var tooltipVisible by remember(contact.id) {
                    mutableStateOf(false)
                }
                Box {
                    Column(
                        Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = 5.dp)
                            .combinedClickable(
                                onClick = { onOpen(contact) },
                                onLongClick = { tooltipVisible = true },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HomeAvatar(
                            initials = contact.avatarInitials
                                ?: contact.displayName.take(2).uppercase(),
                            color = parseAvatarColorHex(
                                contact.avatarColorHex,
                                Color(theme.accentColor),
                            ),
                            size = 38f,
                            shape = avatarShape,
                        )
                    }
                    if (tooltipVisible) {
                        val tooltipOffset = with(LocalDensity.current) {
                            24.dp.roundToPx()
                        }
                        // Flutter Tooltip defaults: grey700 at 90% opacity,
                        // radius 4, horizontal 8 / vertical 4 padding, below the
                        // child; Dart overrides only the 12sp text color.
                        Popup(
                            onDismissRequest = { tooltipVisible = false },
                            alignment = Alignment.BottomCenter,
                            offset = IntOffset(0, tooltipOffset),
                        ) {
                            Surface(
                                color = Color(0xE6616161),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    contact.displayName,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 4.dp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
