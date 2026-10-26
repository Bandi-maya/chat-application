package com.example.chat.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.core.theme.ChatyColors

/**
 * Instance-scoped hand-off for Dart `homeHeaderBottomWidget` (WhatsApp OLD UI). MainNavigationShell owns one
 * instance, provides it through [LocalHomeHeaderTabs], and TopWhatsAppShell publishes plain DATA (ids, selected
 * index, height), never a composable lambda. ChatsHomeHost draws [HomeHeaderTabsBar] from it right after its header.
 * [publish] writes a snapshot field only when the value changed, so republishing every composition does not
 * trigger recomposition. [onSelect] is a plain (non-snapshot) field read at click time, so it is always the latest
 * shell callback without being state.
 */
@Stable
class HomeHeaderTabsState {
    var ids: List<String> by mutableStateOf(emptyList())
        private set
    var selectedIndex: Int by mutableStateOf(0)
        private set
    var heightDp: Float by mutableStateOf(48f)
        private set
    var active: Boolean by mutableStateOf(false)
        private set

    /** Latest shell selection callback; read only inside click handlers. */
    @Volatile var onSelect: (Int) -> Unit = {}

    /** Number of snapshot writes performed (lifecycle regression tests). */
    var writeCount: Int = 0
        private set

    fun publish(ids: List<String>, selectedIndex: Int, heightDp: Float, onSelect: (Int) -> Unit) {
        this.onSelect = onSelect
        if (this.ids != ids) { this.ids = ids; writeCount++ }
        if (this.selectedIndex != selectedIndex) { this.selectedIndex = selectedIndex; writeCount++ }
        if (this.heightDp != heightDp) { this.heightDp = heightDp; writeCount++ }
        if (!active) { active = true; writeCount++ }
    }

    fun clear() {
        onSelect = {}
        if (active) { active = false; writeCount++ }
    }
}

val LocalHomeHeaderTabs = compositionLocalOf<HomeHeaderTabsState?> { null }

/** The WhatsApp OLD UI tab row. Styled like the Header settings live preview
 *  (GbLiveHeaderPreview OldUiHeader): surface background, selected = bold accent label + 2.5dp accent underline,
 *  others secondary 13sp. All tabs always visible, equal weight (matches the current published shell). */
@Composable
fun HomeHeaderTabsBar(colors: ChatyColors, ids: List<String>, selectedIndex: Int, heightDp: Float, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(colors.surface),
    ) {
        ids.forEachIndexed { index, id ->
            val selected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        NavigationCatalog.labelFor(id).uppercase(),
                        color = if (selected) colors.primary else colors.foregroundSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(if (selected) colors.primary else Color.Transparent),
                )
            }
        }
    }
}
