package com.example.chat.ui.calls.bundles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.settings.CallContactOption

/**
 * Universal Group Meeting Calling Screen & Participant Invitation Flow
 * Faithfully ports Reference Image 1 screens:
 * A. Speaker-focused video meeting
 * B. Participant invitation screen with "Ring" pill buttons
 * C. Notification & active ongoing meeting presentation
 * D. Group meeting tile grid with active speakers
 */

data class GroupMeetingParticipant(
    val id: String,
    val displayName: String,
    val username: String,
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoOn: Boolean = true,
)

@Composable
fun BundleGroupMeetingView(
    bundleId: CallBundleId,
    meetingTitle: String = "General Meeting",
    durationSeconds: Int = 305,
    participants: List<GroupMeetingParticipant>,
    isSpeakerFocused: Boolean,
    onToggleSpeakerFocus: () -> Unit,
    onInviteClicked: () -> Unit,
    onLeaveMeeting: () -> Unit,
) {
    val tokens = remember(bundleId) { CallBundleRegistry.getTokens(bundleId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.surfaceBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleSpeakerFocus) {
                    Icon(
                        imageVector = if (isSpeakerFocused) Icons.Rounded.GridView else Icons.Rounded.Person,
                        contentDescription = "Toggle Grid/Speaker",
                        tint = Color.White,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = meetingTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "${participants.size} Participants • ${durationSeconds / 60}:${String.format("%02d", durationSeconds % 60)}",
                        fontSize = 12.sp,
                        color = tokens.secondaryText,
                    )
                }
                IconButton(onClick = onInviteClicked) {
                    Icon(Icons.Rounded.PersonAdd, "Invite People", tint = tokens.primaryAccent)
                }
            }

            // Central Area: Grid vs Speaker Focus
            if (isSpeakerFocused && participants.isNotEmpty()) {
                // Large Speaker View
                val activeSpeaker = participants.firstOrNull { it.isSpeaking } ?: participants.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(tokens.cardCornerRadiusDp.dp))
                        .background(tokens.cardBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(tokens.primaryAccent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = activeSpeaker.displayName.take(1).uppercase(),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = activeSpeaker.displayName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (activeSpeaker.isSpeaking) {
                            Text(
                                text = "Speaking…",
                                color = tokens.acceptColor,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            } else {
                // 2x2 or Adaptive Responsive Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val rows = participants.chunked(2)
                    for (row in rows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (p in row) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(tokens.cardBackground),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(tokens.primaryAccent.copy(alpha = 0.3f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(p.displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(p.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Call Action Strip
            Surface(
                shape = RoundedCornerShape(tokens.pillCornerRadiusDp.dp),
                color = tokens.controlBarBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIconButton(Icons.Rounded.Mic, false, tokens.primaryAccent, {})
                    ControlIconButton(Icons.Rounded.Videocam, false, tokens.primaryAccent, {})
                    ControlIconButton(Icons.Rounded.VolumeUp, true, tokens.primaryAccent, {})
                    ControlIconButton(Icons.Rounded.CallEnd, true, tokens.declineColor, onLeaveMeeting)
                }
            }
        }
    }
}

/**
 * Participant Invitation Screen directly from Reference Image 1 (B: "Invite People")
 */
@Composable
fun BundleParticipantInviteSheet(
    bundleId: CallBundleId,
    contacts: List<CallContactOption>,
    invitedContactIds: Set<String>,
    onToggleInvite: (String) -> Unit,
    onClose: () -> Unit,
) {
    val tokens = remember(bundleId) { CallBundleRegistry.getTokens(bundleId) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.surfaceBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    text = "Invite People",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search", color = tokens.secondaryText) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = tokens.secondaryText) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = tokens.cardBackground,
                    unfocusedContainerColor = tokens.cardBackground,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = tokens.primaryAccent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )

            // Contact List with "Ring" Action Buttons
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredContacts, key = { it.id }) { contact ->
                    val isRinging = invitedContactIds.contains(contact.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.cardBackground.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(tokens.primaryAccent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(contact.displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.displayName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("@${contact.id.take(8)}", color = tokens.secondaryText, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onToggleInvite(contact.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRinging) tokens.declineColor else tokens.primaryAccent
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(if (isRinging) "Cancel" else "Ring", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

