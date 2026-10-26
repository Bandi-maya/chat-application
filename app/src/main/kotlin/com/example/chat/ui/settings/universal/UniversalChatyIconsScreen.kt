package com.example.chat.ui.settings.universal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhotoSizeSelectSmall
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.icons.ChatyIconThemeId
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.settings.rememberPreferencesVersion
import com.example.chat.ui.theme.LocalThemeConfig

// Exact Flutter material color constants used by the Dart screen.
private val White10 = Color(0x1AFFFFFF)
private val White12 = Color(0x1FFFFFFF)
private val White24 = Color(0x3DFFFFFF)
private val White70 = Color(0xB3FFFFFF)
private val Black12 = Color(0x1F000000)
private val Black38 = Color(0x61000000)
private val Black87 = Color(0xDD000000)
private val SignalGreen = Color(0xFF10B981)

/** Dart grid delegates (shrinkWrap inside the page ListView) replayed as
 *  weighted rows so no lazy grid nests inside the scrolling page. */
@Composable
private fun <T> FixedGrid(
    items: List<T>,
    columns: Int,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    cellAspectRatio: Float,
    cell: @Composable (T) -> Unit,
) {
    items.chunked(columns).forEachIndexed { rowIndex, row ->
        if (rowIndex > 0) Spacer(Modifier.height(verticalSpacing))
        Row {
            row.forEachIndexed { index, item ->
                Box(Modifier.weight(1f).aspectRatio(cellAspectRatio)) { cell(item) }
                if (index < row.lastIndex) Spacer(Modifier.width(horizontalSpacing))
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Port of lib/features/settings/universal/universal_chaty_icons_screen.dart:
 *  active-theme live preview with dark/light + micro-size toggles, the ten
 *  theme selection cards, the 14-icon contact sheet, and the searchable
 *  individual catalogue with the multi-theme detail modal. Applying a theme
 *  writes universal.iconTheme = theme.title (Dart _applyTheme) and reports
 *  the Dart snackbar text via [onMessage] (2s snackbar in Dart).
 *
 *  UNCOMPILED (Compose) - CI is the compiler check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalChatyIconsScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    rememberPreferencesVersion(preferences)
    val config = LocalThemeConfig.current
    val view = LocalView.current
    val prefs = preferences.universal
    val activeTheme = ChatyIconThemeId.fromString(prefs.iconTheme)

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var previewIsDark by remember { mutableStateOf(true) }
    var microSizePreview by remember { mutableStateOf(false) }
    var detailIcon by remember { mutableStateOf<ChatyIconId?>(null) }

    val previewSize = UniversalIconsLogic.previewSize(microSizePreview).dp
    val filtered = UniversalIconsLogic.filteredIcons(selectedCategory, searchQuery)

    val accent = Color(config.accentColor)
    val surface = Color(config.surfaceColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)
    val previewIconColor = if (previewIsDark) Color.White else Black87

    fun applyTheme(theme: ChatyIconThemeId) {
        ChatyHapticsPort.selection(view)
        preferences.updateUniversal(prefs.copy(iconTheme = theme.title))
        // Dart snackbar: '<title> icon theme applied across Chaty', 2s.
        onMessage("${theme.title} icon theme applied across Chaty")
    }

    UniversalScaffold(
        title = "Universal Chaty Icons",
        onBack = onBack,
        actions = {
            TextButton(onClick = { applyTheme(ChatyIconThemeId.chatySigil) }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset", color = accent, fontSize = 12.5.sp)
            }
        },
    ) {
        // SECTION 1: active live preview with light/dark and size toggles.
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.18f), surface),
                    ),
                    RoundedCornerShape(18.dp),
                )
                .border(1.5.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "ACTIVE THEME",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W800,
                        letterSpacing = 1.1.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(activeTheme.title, color = primaryText, fontSize = 18.sp, fontWeight = FontWeight.W800)
                }
                Row(
                    Modifier.background(accent, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Applied", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.W800)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(activeTheme.description, color = secondaryText, fontSize = 12.5.sp)
            Spacer(Modifier.height(12.dp))
            Row {
                FilterChip(
                    selected = previewIsDark,
                    onClick = { previewIsDark = !previewIsDark },
                    label = { Text(if (previewIsDark) "Dark Preview" else "Light Preview", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            if (previewIsDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.2f),
                    ),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = microSizePreview,
                    onClick = { microSizePreview = !microSizePreview },
                    label = { Text(if (microSizePreview) "20px Micro" else "26px Standard", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.PhotoSizeSelectSmall, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.2f),
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth()
                    .background(
                        if (previewIsDark) Color.Black.copy(alpha = 0.8f) else Color.White,
                        RoundedCornerShape(14.dp),
                    )
                    .border(1.dp, if (previewIsDark) White12 else Black12, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatyIcon(ChatyIconId.chats, size = previewSize, color = accent, themeOverride = activeTheme)
                ChatyIcon(ChatyIconId.search, size = previewSize, color = previewIconColor, themeOverride = activeTheme)
                ChatyIcon(ChatyIconId.camera, size = previewSize, color = previewIconColor, themeOverride = activeTheme)
                ChatyIcon(ChatyIconId.calls, size = previewSize, color = previewIconColor, themeOverride = activeTheme)
                ChatyIcon(ChatyIconId.signal, size = previewSize * 0.9f, color = SignalGreen, themeOverride = activeTheme)
                ChatyIcon(ChatyIconId.more, size = previewSize, color = previewIconColor, themeOverride = activeTheme)
            }
        }
        Spacer(Modifier.height(20.dp))

        // SECTION 2: the ten theme selection cards.
        Text(
            "10 ICON THEMES (GENUINELY DISTINCT SHAPE GRAMMARS)",
            color = secondaryText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(10.dp))
        ChatyIconThemeId.entries.forEachIndexed { index, itemTheme ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            val isSelected = activeTheme == itemTheme
            val groupIconColor = if (isSelected) accent else if (previewIsDark) Color.White else Color.Black
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { applyTheme(itemTheme) }
                    .background(
                        if (isSelected) accent.copy(alpha = 0.12f) else surface,
                        RoundedCornerShape(16.dp),
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accent else White10,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.background(
                            if (previewIsDark) Color.Black.copy(alpha = 0.45f) else Color.White,
                            RoundedCornerShape(12.dp),
                        )
                            .border(1.dp, if (previewIsDark) White10 else Black12, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChatyIcon(ChatyIconId.chats, size = previewSize * 0.9f, color = groupIconColor, themeOverride = itemTheme)
                        Spacer(Modifier.width(8.dp))
                        ChatyIcon(ChatyIconId.more, size = previewSize * 0.9f, color = groupIconColor, themeOverride = itemTheme)
                        Spacer(Modifier.width(8.dp))
                        ChatyIcon(ChatyIconId.camera, size = previewSize * 0.9f, color = groupIconColor, themeOverride = itemTheme)
                        Spacer(Modifier.width(8.dp))
                        ChatyIcon(ChatyIconId.favourite, size = previewSize * 0.9f, color = groupIconColor, themeOverride = itemTheme)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                itemTheme.title,
                                color = primaryText,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.W800 else FontWeight.W600,
                            )
                            if (itemTheme == ChatyIconThemeId.chatySigil) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Default",
                                    Modifier.background(White12, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.5.sp,
                                    color = White70,
                                    fontWeight = FontWeight.W600,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            itemTheme.description,
                            color = secondaryText,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isSelected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = White24, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // SECTION 3: 14 core actions contact sheet.
        Text(
            "CORE ACTIONS CONTACT SHEET (DIRECT COMPARISON)",
            color = secondaryText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Direct side-by-side comparison of the 14 core actions in the active theme.",
            color = secondaryText,
            fontSize = 12.5.sp,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth()
                .background(surface, RoundedCornerShape(16.dp))
                .border(1.dp, White10, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            FixedGrid(
                items = UniversalIconsLogic.coreContactIcons,
                columns = 7,
                horizontalSpacing = 8.dp,
                verticalSpacing = 10.dp,
                cellAspectRatio = 0.8f,
            ) { iconId ->
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable {
                            ChatyHapticsPort.light(view)
                            detailIcon = iconId
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(38.dp)
                            .background(if (previewIsDark) Black38 else White70, RoundedCornerShape(10.dp))
                            .border(1.dp, White10, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatyIcon(iconId, size = previewSize * 0.85f, color = primaryText, themeOverride = activeTheme)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(iconId.name, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // SECTION 4: individual icon catalogue.
        Text(
            "INDIVIDUAL ICON CATALOGUE",
            color = secondaryText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Search and tap any icon to preview it across all 10 themes in the multi-theme modal.",
            color = secondaryText,
            fontSize = 12.5.sp,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = primaryText),
            placeholder = {
                Text("Search icons by name (e.g. camera, calls)…", color = secondaryText)
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = secondaryText) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = surface,
                unfocusedContainerColor = surface,
                focusedBorderColor = White10,
                unfocusedBorderColor = White10,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            UniversalIconsLogic.categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        ChatyHapticsPort.light(view)
                        selectedCategory = cat
                    },
                    label = {
                        Text(
                            cat,
                            color = if (isSelected) Color.Black else primaryText,
                            fontWeight = if (isSelected) FontWeight.W700 else FontWeight.W500,
                            fontSize = 12.5.sp,
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = surface,
                        selectedContainerColor = accent,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = White12,
                        selectedBorderColor = accent,
                    ),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        FixedGrid(
            items = filtered,
            columns = 4,
            horizontalSpacing = 12.dp,
            verticalSpacing = 12.dp,
            cellAspectRatio = 0.95f,
        ) { iconId ->
            Column(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        ChatyHapticsPort.light(view)
                        detailIcon = iconId
                    }
                    .background(surface, RoundedCornerShape(14.dp))
                    .border(1.dp, White10, RoundedCornerShape(14.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ChatyIcon(iconId, size = previewSize, color = primaryText, themeOverride = activeTheme)
                Spacer(Modifier.height(8.dp))
                Text(
                    UniversalIconsLogic.iconDisplayName(iconId),
                    Modifier.padding(horizontal = 4.dp),
                    color = secondaryText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.W500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    // Dart _showIconDetailModal: bottom sheet, top radius 24, one icon across
    // all ten themes.
    detailIcon?.let { iconId ->
        val scheme = MaterialTheme.colorScheme
        ModalBottomSheet(tonalElevation = 0.dp,
            onDismissRequest = { detailIcon = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = scheme.surface,
            shape = com.example.chat.ui.kit.ChatyModalShape,
            dragHandle = null,
        ) {
            Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(40.dp, 4.dp)
                        .background(White24, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.background(scheme.primary.copy(alpha = 0.15f), CircleShape).padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatyIcon(iconId, size = 28.dp, color = scheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            UniversalIconsLogic.iconDisplayName(iconId),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.W700,
                        )
                        Text(
                            "Unique ID: ChatyIconId.${iconId.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            color = scheme.primary,
                        )
                        Text(
                            "Side-by-side preview across all 10 distinct icon themes. Applying a theme updates this icon everywhere it is displayed.",
                            fontSize = 12.5.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                FixedGrid(
                    items = ChatyIconThemeId.entries,
                    columns = 5,
                    horizontalSpacing = 10.dp,
                    verticalSpacing = 12.dp,
                    cellAspectRatio = 0.85f,
                ) { theme ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(48.dp)
                                .background(scheme.surfaceContainerHighest.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .border(1.dp, White12, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatyIcon(iconId, size = 24.dp, color = scheme.primary, themeOverride = theme)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            theme.title,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.W500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
