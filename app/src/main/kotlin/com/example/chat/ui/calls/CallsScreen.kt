package com.example.chat.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallRecord
import com.example.chat.domain.models.CallType
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalThemeConfig
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Port of features/calls/calls_screen.dart: the protected recent-calls log
 *  with pull-to-refresh, empty/error states, direction icon, duration and a
 *  redial action per row, plus the Add call FAB opening the contact picker. */
@Composable
fun CallsScreen(
    records: List<CallRecord>,
    historyUnavailable: Boolean,
    currentUserId: String,
    contactFor: (String) -> UserProfile?,
    showCallActions: Boolean = true,
    onRefresh: () -> Unit,
    onOpenAddCall: () -> Unit,
    onRedial: (remoteUserId: String, displayName: String, initials: String?, colorHex: String?, isVideo: Boolean) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Scaffold(
        containerColor = Color(theme.backgroundColor),
        floatingActionButton = {
            if (showCallActions) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.semantics { contentDescription = "Add call" },
                    onClick = onOpenAddCall,
                    containerColor = Color(theme.accentColor),
                    contentColor = Color(theme.onAccentColor),
                    icon = { Icon(Icons.Rounded.Dialpad, contentDescription = null) },
                    text = { Text("Add call") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item {
                Row(
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    Text(
                        "Calls",
                        color = Color(theme.primaryTextColor),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when {
                historyUnavailable && records.isEmpty() -> item {
                    CallEmptyState(
                        icon = {
                            Icon(
                                Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = Color(theme.dangerColor),
                                modifier = Modifier.size(42.dp),
                            )
                        },
                        title = "Call history unavailable",
                        titleColor = Color(theme.primaryTextColor),
                        message = "Chaty could not load your protected call history. Pull down or retry.",
                        messageColor = Color(theme.secondaryTextColor),
                        actionLabel = "Retry",
                        onAction = onRefresh,
                    )
                }
                records.isEmpty() -> item {
                    CallEmptyState(
                        icon = {
                            Icon(
                                Icons.Rounded.PhoneCallback,
                                contentDescription = null,
                                tint = Color(theme.accentColor),
                                modifier = Modifier.size(42.dp),
                            )
                        },
                        title = "No recent calls",
                        titleColor = Color(theme.primaryTextColor),
                        message = "Completed, missed, and declined server call sessions will appear here.",
                        messageColor = Color(theme.secondaryTextColor),
                    )
                }
                else -> items(records, key = { it.id }) { call ->
                    val remoteUserId = call.participantIds.firstOrNull { it.isNotEmpty() && it != currentUserId }
                        ?: call.callerId
                    val contact = contactFor(remoteUserId)
                    val isMissed = call.direction == CallDirection.missed
                    val isVideo = call.type == CallType.video
                    val canCall = remoteUserId.isNotEmpty() && remoteUserId != currentUserId
                    val name = contact?.displayName?.ifEmpty { null } ?: "Chaty contact"
                    val initials = contact?.avatarInitials ?: "U"
                    val colorHex = contact?.avatarColorHex
                    val subtitle = buildString {
                        append(call.timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter))
                        append(" • ")
                        append(if (call.durationSeconds > 0) "${call.durationSeconds}s" else "No connection")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable(enabled = canCall) {
                                onRedial(remoteUserId, name, initials, colorHex, isVideo)
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        CallAvatar(initials = initials, colorHex = colorHex, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                name,
                                color = if (isMissed) Color(theme.dangerColor) else Color(theme.primaryTextColor),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                letterSpacing = (-0.2).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (call.direction) {
                                        CallDirection.incoming -> Icons.Rounded.CallReceived
                                        CallDirection.outgoing -> Icons.Rounded.CallMade
                                        CallDirection.missed -> Icons.Rounded.CallMissed
                                    },
                                    contentDescription = null,
                                    tint = if (isMissed) Color(theme.dangerColor) else Color(theme.secondaryTextColor),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    subtitle,
                                    color = Color(theme.secondaryTextColor),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = { onRedial(remoteUserId, name, initials, colorHex, isVideo) },
                            enabled = canCall,
                        ) {
                            Icon(
                                if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                contentDescription = if (isVideo) "Video call" else "Voice call",
                                tint = Color(theme.accentColor),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    titleColor: Color,
    message: String,
    messageColor: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(28.dp),
    ) {
        Spacer(Modifier.height(120.dp))
        icon()
        Spacer(Modifier.height(12.dp))
        Text(title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(message, color = messageColor, fontSize = 13.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
