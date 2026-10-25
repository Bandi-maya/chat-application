package com.example.chat.ui.chats.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.UserProfile

/** Port of chaty_story_tile.dart (looks, strip heights, tile widths) and the
 *  stories strip section of chats_home_screen.dart's build(). */

enum class ChatyStoryLook { circular, instagram, stock, facebook, squircle, card, wideCard }

fun chatyStoryLookFor(style: String): ChatyStoryLook = when (style) {
    "Instagram" -> ChatyStoryLook.instagram
    "Stock" -> ChatyStoryLook.stock
    "Facebook" -> ChatyStoryLook.facebook
    "Squircle" -> ChatyStoryLook.squircle
    "Card", "Card View" -> ChatyStoryLook.card
    "Wide Card" -> ChatyStoryLook.wideCard
    else -> ChatyStoryLook.circular
}

/** Strip height needed by a look, never smaller than [base]. */
fun chatyStoryStripHeight(look: ChatyStoryLook, base: Double): Double = when (look) {
    ChatyStoryLook.facebook -> if (base < 118) 118.0 else base
    ChatyStoryLook.wideCard -> if (base < 76) 76.0 else base
    ChatyStoryLook.card -> if (base < 88) 88.0 else base
    else -> base
}

fun chatyStoryTileWidth(look: ChatyStoryLook, base: Double): Double = when (look) {
    ChatyStoryLook.facebook -> 80.0
    ChatyStoryLook.wideCard -> 156.0
    else -> base
}

@Composable
fun ChatyStoryTile(
    look: ChatyStoryLook,
    initials: String,
    avatarColor: Color,
    name: String,
    online: Boolean,
    avatarSize: Double,
    baseWidth: Double,
    nameFontSize: Double,
    wrapInCard: Boolean,
    accent: Color,
    cardColor: Color,
    borderColor: Color,
    nameColor: Color,
    onlineColor: Color,
    backgroundColor: Color,
) {
    val width = chatyStoryTileWidth(look, baseWidth)

    @Composable
    fun avatar(size: Double, shape: String) {
        Box {
            HomeAvatar(initials = initials, color = avatarColor, size = size.toFloat(), shape = shape)
            if (online) {
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = 1.dp)) {
                    HomeOnlineDot(true, size.toFloat(), onlineColor, backgroundColor)
                }
            }
        }
    }

    @Composable
    fun label(color: Color? = null) {
        Text(
            name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = color ?: nameColor,
            fontSize = nameFontSize.sp,
        )
    }

    @Composable
    fun ring(outer: Double, ringWidth: Double, gap: Double, color: Color, content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .size(outer.dp)
                .border(ringWidth.dp, color, CircleShape)
                .padding(gap.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    @Composable
    fun column(width: Double, avatarContent: @Composable () -> Unit, card: Boolean = false) {
        val inner: @Composable () -> Unit = {
            Column(
                modifier = Modifier.width((if (card || wrapInCard) width - 8 else width).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                avatarContent()
                Spacer(Modifier.height(4.dp))
                label()
            }
        }
        if (!card && !wrapInCard) {
            inner()
        } else {
            Box(
                modifier = Modifier
                    .width(width.dp)
                    .padding(vertical = 2.dp)
                    .background(cardColor, RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
            ) { inner() }
        }
    }

    when (look) {
        ChatyStoryLook.facebook -> {
            // Tall portrait card: tinted body, ringed avatar top-left, name on
            // a dark scrim at the bottom.
            Box(
                modifier = Modifier
                    .width(width.dp)
                    .padding(vertical = 2.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                avatarColor.copy(alpha = 0.95f),
                                avatarColor.copy(alpha = 0.55f),
                            ),
                        ),
                        RoundedCornerShape(14.dp),
                    )
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
            ) {
                Text(
                    initials,
                    color = Color(0x8CFFFFFF),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.W800,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 6.dp)) {
                    ring(36.0, 2.0, 1.5, accent) { avatar(29.0, "circle") }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .background(Color(0x61000000))
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                ) { label(Color.White) }
            }
        }
        ChatyStoryLook.wideCard -> {
            // Wide card: avatar on the left, name on the right.
            Row(
                modifier = Modifier
                    .width(width.dp)
                    .padding(vertical = 4.dp)
                    .background(cardColor, RoundedCornerShape(16.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                avatar(40.0, "circle")
                Spacer(Modifier.width(8.dp))
                label()
            }
        }
        ChatyStoryLook.instagram -> column(width, {
            ring(avatarSize, 2.5, 2.5, accent) { avatar(avatarSize - 10, "circle") }
        })
        ChatyStoryLook.stock -> column(width, {
            ring(avatarSize, 1.5, 1.5, accent.copy(alpha = 0.75f)) {
                avatar(avatarSize - 6, "circle")
            }
        })
        ChatyStoryLook.squircle -> column(width, { avatar(avatarSize, "squircle") })
        ChatyStoryLook.card -> column(width, { avatar(avatarSize, "roundedSquare") }, card = true)
        ChatyStoryLook.circular -> column(width, { avatar(avatarSize, "circle") })
    }
}

/** The strip itself (build(): horizontal ListView.separated over contacts,
 *  14dp horizontal padding, 10dp gaps, first-name labels, online ring unless
 *  the 'Minimal' stories style). */
@Composable
fun StoryStrip(
    contacts: List<UserProfile>,
    theme: ThemeConfig,
    look: ChatyStoryLook,
    stripHeight: Double,
    storiesAvatar: Double,
    storiesStyle: String,
    storyCardWrap: Boolean,
    storyShowOnlineRing: Boolean,
    storyCompact: Boolean,
    storyTileWidth: Double,
    storyNameFontSize: Double,
    isOnline: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.height(stripHeight.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(contacts, key = { _, c -> c.id }) { _, contact ->
            val online = isOnline(contact.id) && storyShowOnlineRing
            ChatyStoryTile(
                look = look,
                initials = contact.avatarInitials,
                avatarColor = parseAvatarColorHex(contact.avatarColorHex, Color(theme.accentColor)),
                name = contact.displayName.split(' ').first(),
                online = online,
                avatarSize = if (storyCompact) storiesAvatar * 0.82 else storiesAvatar,
                baseWidth = storyTileWidth,
                nameFontSize = storyNameFontSize,
                wrapInCard = storyCardWrap,
                accent = Color(theme.accentColor),
                cardColor = Color(theme.cardColor),
                borderColor = Color(theme.surfaceColor),
                nameColor = if (storiesStyle == "Minimal") {
                    Color(argbWithAlpha(theme.secondaryTextColor, 0.55))
                } else {
                    Color(theme.secondaryTextColor)
                },
                onlineColor = Color(theme.successColor),
                backgroundColor = Color(theme.backgroundColor),
            )
        }
    }
}
