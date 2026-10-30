package com.example.chat.ui.calls.bundles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.services.ChatyCallSession

/**
 * Faithfully implements the two presentations in Reference Image 3:
 * 1. Compact incoming call card: Minimal horizontal pill card with avatar, name/subtitle, Accept & Decline.
 * 2. Expanded floating call card: Expanded card with additional controls (Mute, Keypad, Message, Speaker).
 * Automatically styled and adapted to the active Call Experience Bundle.
 */
@Composable
fun BundleCompactCallCard(
    bundleId: CallBundleId,
    session: ChatyCallSession,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMuteToggle: () -> Unit = {},
    onSpeakerToggle: () -> Unit = {},
) {
    val tokens = remember(bundleId) { CallBundleRegistry.getTokens(bundleId) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(tokens.cardCornerRadiusDp.dp))
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(tokens.cardCornerRadiusDp.dp),
        color = tokens.cardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // Top Section (Always Visible Compact Card)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(tokens.primaryAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "C" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primaryAccent,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = session.remoteDisplayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primaryText,
                        )
                        Text(
                            text = if (session.isVideo) "Video call" else "Mobile",
                            fontSize = 12.sp,
                            color = tokens.secondaryText,
                        )
                    }
                }

                // Compact Quick Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(tokens.acceptColor)
                            .clickable { onAccept() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(tokens.declineColor)
                            .clickable { onDecline() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Expanded Card Actions (Reference Image 3 Expanded Card)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniCardAction(icon = Icons.Rounded.Mic, label = "Mute", onClick = onMuteToggle)
                        MiniCardAction(icon = Icons.Rounded.Dialpad, label = "Keypad", onClick = {})
                        MiniCardAction(icon = Icons.Rounded.ChatBubble, label = "Message", onClick = {})
                        MiniCardAction(icon = Icons.Rounded.VolumeUp, label = "Speaker", onClick = onSpeakerToggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

