package com.example.chat.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.home.HomeAvatar
import com.example.chat.ui.chats.home.parseAvatarColorHex
import com.example.chat.ui.core.theme.ChatyColors

/** Account/global-search actions shared with the existing Home header.
 * Screen-specific menus remain with their existing screens; a blank slot is not a fake menu.
 */
@Composable
fun ReferenceRootActions(
    colors: ChatyColors,
    user: UserProfile?,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Row {
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search chats and messages", tint = colors.foreground) }
        IconButton(onClick = onSettings) {
            HomeAvatar(initials = user?.avatarInitials.orEmpty(),
                color = parseAvatarColorHex(user?.avatarColorHex, colors.primary),
                size = 28f, shape = "circle", url = user?.avatarUrl)
        }
    }
}
