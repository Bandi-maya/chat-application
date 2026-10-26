package com.example.chat.ui.chats.detail

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TagFaces
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.EmojiSymbols
import androidx.compose.material.icons.rounded.Flag
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha

/** Dart ChatyEmojiComposerAction. */
enum class ChatyEmojiComposerAction { send, keyboard, close }

/** Dart ChatyEmojiComposerResult. */
data class ChatyEmojiComposerResult(
    val text: String,
    val action: ChatyEmojiComposerAction,
)

private val categoryIcons: List<ImageVector> = listOf(
    Icons.Rounded.TagFaces,
    Icons.Rounded.Pets,
    Icons.Rounded.Restaurant,
    Icons.Rounded.SportsSoccer,
    Icons.Rounded.Place,
    Icons.Rounded.Lightbulb,
    Icons.Rounded.EmojiSymbols,
    Icons.Rounded.Flag,
)

/** Port of lib/core/emoji/widgets/chaty_emoji_picker.dart's
 *  _ChatyEmojiPickerSheet: drag-resizable sheet, mode-dependent title,
 *  composer draft row with send morph, recents + category tab bar, 8-column
 *  grid, search, and long-press skin-tone variants. The Flutter grid came
 *  from the emoji_picker_flutter package; here it is rebuilt over
 *  [EmojiCatalog] (Unicode v18 fully-qualified data). */
@Composable
fun ChatyEmojiPickerSheet(
    theme: ThemeConfig,
    reactionMode: Boolean,
    composerMode: Boolean,
    initialText: String = "",
    recentCache: EmojiRecentCache = remember { EmojiRecentCache().apply { initialize() } },
    onEmojiPicked: (String) -> Unit = {},
    onComposerDone: (ChatyEmojiComposerResult) -> Unit = {},
    headerColor: Color? = null,
    headerIconsColor: Color? = null,
    backgroundColor: Color? = null,
) {
    // Dart ChatyEmojiPicker: backgroundColor ?? surface, headerColor as the header
    // container color (null = none), headerIconsColor ?? onSurface for the header
    // title and icons - fed by conversation.emojiHeaderColor /
    // emojiHeaderIconsColor / emojiPickerBgColor at the call sites.
    val surface = backgroundColor ?: Color(theme.surfaceColor)
    val foreground = Color(theme.primaryTextColor)
    val foregroundSecondary = Color(theme.secondaryTextColor)
    val accent = Color(theme.accentColor)
    val headerForeground = headerIconsColor ?: foreground

    var heightFactor by remember {
        mutableStateOf(
            when {
                reactionMode -> 0.32f
                composerMode -> 0.72f
                else -> 0.42f
            },
        )
    }
    var draftText by remember { mutableStateOf(initialText) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(0) } // 0 = recents
    var tonePickerFor by remember { mutableStateOf<String?>(null) }
    var recentsVersion by remember { mutableStateOf(0) }

    val screenHeight = LocalConfiguration.current.screenHeightDp.toFloat()
    val sheetHeight = (screenHeight * heightFactor).coerceIn(250f, screenHeight * 0.9f)

    fun recordAndPick(entry: EmojiEntry, emoji: String) {
        recentCache.recordUsage(emoji, entry.name)
        recentsVersion++
        if (composerMode) {
            draftText += emoji
        } else {
            onEmojiPicked(emoji)
        }
    }

    val recentItems = remember(recentsVersion) { recentCache.items() }
    val gridEntries: List<EmojiEntry> = remember(searchQuery, selectedCategory, recentsVersion) {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            EmojiCatalog.categories.flatMap { it.second }
                .filter { q in it.name }
        } else if (selectedCategory == 0) {
            recentItems.map { EmojiEntry(it.unicode, it.label) }
        } else {
            EmojiCatalog.categories[selectedCategory - 1].second
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .height(sheetHeight.dp)
            .clip(com.example.chat.ui.kit.ChatyModalShape)
            .background(surface),
    ) {
        // Drag handle (Dart: 42x4, vertical drag resizes 0.32..0.90).
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput("emojiDrag") {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (screenHeight > 0) {
                            heightFactor = (heightFactor - dragAmount / screenHeight)
                                .coerceIn(0.32f, 0.90f)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .background(foreground.copy(alpha = 0.32f)),
            )
        }

        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (headerColor != null) it.background(headerColor) else it }
                .padding(start = 18.dp, top = 2.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    reactionMode -> "Choose reaction"
                    composerMode -> "Message with emoji"
                    else -> "Choose emoji"
                },
                color = headerForeground,
                fontWeight = FontWeight.W800,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
            )
            if (composerMode) {
                IconButton(onClick = {
                    onComposerDone(ChatyEmojiComposerResult(draftText, ChatyEmojiComposerAction.keyboard))
                }) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = "Return to keyboard",
                        tint = headerForeground)
                }
            }
            IconButton(onClick = {
                if (composerMode) {
                    onComposerDone(ChatyEmojiComposerResult(draftText, ChatyEmojiComposerAction.close))
                } else {
                    onEmojiPicked("")
                }
            }) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = headerForeground)
            }
        }

        // Composer draft row
        if (composerMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .background(
                            Color(argbWithAlpha(theme.secondaryTextColor, 0.1)),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (draftText.isEmpty()) {
                        Text("Type or select emojis…", color = foregroundSecondary, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        textStyle = TextStyle(color = foreground, fontSize = 14.sp),
                        cursorBrush = SolidColor(accent),
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(Modifier.width(8.dp))
                val canSend = draftText.trim().isNotEmpty()
                Box(
                    Modifier
                        .size(46.dp)
                        .background(
                            if (canSend) accent else accent.copy(alpha = 0.35f),
                            CircleShape,
                        )
                        .pointerInput(canSend, draftText) {
                            detectTapGestures(
                                onTap = {
                                    if (canSend) {
                                        onComposerDone(
                                            ChatyEmojiComposerResult(
                                                draftText,
                                                ChatyEmojiComposerAction.send,
                                            ),
                                        )
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = Color(theme.onAccentColor),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Search field (Dart SearchViewConfig, hint 'Search emoji…')
        if (searchOpen) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(
                        Color(argbWithAlpha(theme.secondaryTextColor, 0.1)),
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (searchQuery.isEmpty()) {
                    Text("Search emoji…", color = foregroundSecondary, fontSize = 14.sp)
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = foreground, fontSize = 14.sp),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Category tab bar (Dart tabBarHeight 44; recents first when non-empty)
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryTab(
                icon = Icons.Rounded.AccessTime,
                selected = !searchOpen && selectedCategory == 0,
                accent = accent,
                tint = foregroundSecondary,
                contentDescription = "Recent",
                modifier = Modifier.weight(1f),
                onTap = { searchOpen = false; searchQuery = ""; selectedCategory = 0 },
            )
            EmojiCatalog.categories.forEachIndexed { index, (label, _) ->
                CategoryTab(
                    icon = categoryIcons[index],
                    selected = !searchOpen && selectedCategory == index + 1,
                    accent = accent,
                    tint = foregroundSecondary,
                    contentDescription = label,
                    modifier = Modifier.weight(1f),
                    onTap = {
                        searchOpen = false
                        searchQuery = ""
                        selectedCategory = index + 1
                    },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    foreground.copy(alpha = if (theme.isDark) 0.08f else 0.06f),
                ),
        )

        // Grid: 8 columns, emojiSizeMax 30.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (gridEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No emoji found" else "No recent emoji",
                        color = foregroundSecondary,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(gridEntries, key = { it.emoji }) { entry ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .pointerInput(entry.emoji) {
                                    detectTapGestures(
                                        onTap = { recordAndPick(entry, entry.emoji) },
                                        onLongPress = {
                                            if (EmojiCatalog.toneVariants.containsKey(entry.emoji)) {
                                                tonePickerFor = entry.emoji
                                            }
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Visible-only (LazyVerticalGrid composes just on-screen tiles): production
                            // AnimatedEmojiView loops the Noto animation, static glyph on registry
                            // miss / load failure / animator scale 0. Taps stay on the parent Box.
                            AnimatedEmojiView(
                                unicode = entry.emoji,
                                size = 24f,
                                mode = EmojiDisplayMode.picker,
                            )
                        }
                    }
                }
            }

            // Skin-tone variant popup (Dart skin tone support).
            tonePickerFor?.let { base ->
                Popup(onDismissRequest = { tonePickerFor = null }) {
                    Row(
                        Modifier
                            .background(Color(theme.cardColor), com.example.chat.ui.kit.ChatyModalShape)
                            .border(
                                1.dp,
                                Color(argbWithAlpha(theme.secondaryTextColor, 0.2)),
                                com.example.chat.ui.kit.ChatyModalShape,
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val baseEntry = EmojiCatalog.categories
                            .flatMap { it.second }
                            .firstOrNull { it.emoji == base }
                        (listOf(base) + (EmojiCatalog.toneVariants[base] ?: emptyList())).forEach { variant ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .pointerInput(variant) {
                                        detectTapGestures(
                                            onTap = {
                                                tonePickerFor = null
                                                baseEntry?.let { recordAndPick(it, variant) }
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(variant, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }

        // Bottom action bar (Dart BottomActionBarConfig: search enabled,
        // backspace hidden).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }) {
                Icon(Icons.Rounded.Search, contentDescription = "Search emoji", tint = accent)
            }
        }
    }
}

@Composable
private fun CategoryTab(
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    Column(
        modifier
            .pointerInput(contentDescription) { detectTapGestures(onTap = { onTap() }) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) accent else tint,
            modifier = Modifier.size(22.dp),
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(24.dp)
                .height(2.dp)
                .background(if (selected) accent else Color.Transparent),
        )
    }
}
