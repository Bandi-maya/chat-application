package com.example.chat.ui.calls.bundles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallVideoSurfaceProvider
import com.example.chat.data.services.ChatyCallSession

/**
 * Universal Unified Ongoing Call Presentation for the Call Experience Bundles.
 */
@Composable
fun BundleOngoingCallView(
    bundleId: CallBundleId,
    session: ChatyCallSession,
    callDurationSeconds: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    val tokens = remember(bundleId) { CallBundleRegistry.getTokens(bundleId) }

    when (bundleId) {
        CallBundleId.APPLE_IOS -> AppleIosOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
        CallBundleId.SAMSUNG_ONE_UI -> SamsungOneUiOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
        CallBundleId.GOOGLE_PHONE_LATEST -> GoogleLatestOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
        CallBundleId.GOOGLE_PHONE_CLASSIC -> GoogleClassicOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
        CallBundleId.VIVO_IQOO -> VivoIqooOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
        CallBundleId.CHATY_DEFAULT -> ChatyDefaultOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
        CallBundleId.AZURE_RING_VERTICAL -> AzureRingVerticalOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
        CallBundleId.MONO_CHEVRON_DARK -> MonoChevronDarkOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
        CallBundleId.NEXTCLOUD_TALK -> NextcloudTalkOngoingCall(tokens, session, callDurationSeconds, isMuted, isSpeaker, isCameraOn, onToggleMute, onToggleSpeaker, onToggleCamera, onSwitchCamera, onEndCall, onInviteParticipant, onMinimize)
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

// -------------------------------------------------------------------------------------------------
// 01 — CHATY DEFAULT ONGOING CALL
// -------------------------------------------------------------------------------------------------
@Composable
private fun ChatyDefaultOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.surfaceBackground)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: Top action chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", tint = Color.White)
                }
                Surface(
                    shape = RoundedCornerShape(tokens.pillCornerRadiusDp.dp),
                    color = tokens.primaryAccent.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = formatDuration(duration),
                        color = tokens.primaryAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                IconButton(onClick = onInviteParticipant) {
                    Icon(Icons.Rounded.PersonAdd, "Invite", tint = Color.White)
                }
            }

            // Body: Caller Identity & Audio Indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(tokens.cardBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "C" },
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.primaryText,
                )
                Text(
                    text = if (isMuted) "Microphone Muted" else "Connected",
                    fontSize = 14.sp,
                    color = if (isMuted) tokens.declineColor else tokens.secondaryText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Bottom Floating Call Control Bar
            Surface(
                shape = RoundedCornerShape(tokens.pillCornerRadiusDp.dp),
                color = tokens.controlBarBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIconButton(
                        icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        isActive = isMuted,
                        activeColor = tokens.declineColor,
                        onClick = onToggleMute,
                    )
                    ControlIconButton(
                        icon = if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing,
                        isActive = isSpeaker,
                        activeColor = tokens.primaryAccent,
                        onClick = onToggleSpeaker,
                    )
                    if (session.isVideo) {
                        ControlIconButton(
                            icon = if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            isActive = !isCameraOn,
                            activeColor = tokens.declineColor,
                            onClick = onToggleCamera,
                        )
                        ControlIconButton(
                            icon = Icons.Rounded.Cameraswitch,
                            isActive = false,
                            activeColor = tokens.primaryAccent,
                            onClick = onSwitchCamera,
                        )
                    }
                    ControlIconButton(
                        icon = Icons.Rounded.CallEnd,
                        isActive = true,
                        activeColor = tokens.declineColor,
                        onClick = onEndCall,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 02 — APPLE iOS ONGOING CALL (Classic 3x2 Grid + Bottom End Call)
// -------------------------------------------------------------------------------------------------
@Composable
private fun AppleIosOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // iOS Top Title & Duration
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp),
            ) {
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatDuration(duration),
                    fontSize = 16.sp,
                    color = Color(0xFF8E8E93),
                )
            }

            // iOS 3x2 Call Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IosGridCallButton(icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, label = "mute", isSelected = isMuted, onClick = onToggleMute)
                    IosGridCallButton(icon = Icons.Rounded.Dialpad, label = "keypad", isSelected = false, onClick = {})
                    IosGridCallButton(icon = Icons.Rounded.VolumeUp, label = "speaker", isSelected = isSpeaker, onClick = onToggleSpeaker)
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IosGridCallButton(icon = Icons.Rounded.Add, label = "add call", isSelected = false, onClick = onInviteParticipant)
                    IosGridCallButton(icon = Icons.Rounded.Videocam, label = "FaceTime", isSelected = isCameraOn, onClick = onToggleCamera)
                    IosGridCallButton(icon = Icons.Rounded.Contacts, label = "contacts", isSelected = false, onClick = onMinimize)
                }
            }

            // iOS Bottom Centered Red End Call Button
            Box(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .size(68.dp)
                    .background(tokens.declineColor, CircleShape)
                    .clickable { onEndCall() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, "End Call", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun IosGridCallButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .background(if (isSelected) Color.White else Color(0xFF2C2C2E), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.Black else Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White,
        )
    }
}

// -------------------------------------------------------------------------------------------------
// 03 — SAMSUNG ONE UI ONGOING CALL
// -------------------------------------------------------------------------------------------------
@Composable
private fun SamsungOneUiOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111418))
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 36.dp),
            ) {
                Text(session.remoteDisplayName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(formatDuration(duration), fontSize = 15.sp, color = tokens.secondaryText)
            }

            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadiusDp.dp),
                color = tokens.cardBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        ControlIconButton(if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, isMuted, tokens.declineColor, onToggleMute)
                        ControlIconButton(if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing, isSpeaker, tokens.primaryAccent, onToggleSpeaker)
                        ControlIconButton(Icons.Rounded.PersonAdd, false, tokens.primaryAccent, onInviteParticipant)
                        ControlIconButton(Icons.Rounded.PictureInPicture, false, tokens.primaryAccent, onMinimize)
                    }
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(tokens.declineColor, RoundedCornerShape(28.dp))
                            .clickable { onEndCall() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CallEnd, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("End Call", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 04 — GOOGLE LATEST ONGOING CALL
// -------------------------------------------------------------------------------------------------
@Composable
private fun GoogleLatestOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141218))
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp),
            ) {
                Text(session.remoteDisplayName, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                Text(formatDuration(duration), fontSize = 15.sp, color = tokens.secondaryText, modifier = Modifier.padding(top = 4.dp))
            }

            Surface(
                shape = RoundedCornerShape(tokens.cardCornerRadiusDp.dp),
                color = tokens.cardBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ControlIconButton(if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, isMuted, tokens.declineColor, onToggleMute)
                        ControlIconButton(Icons.Rounded.Dialpad, false, tokens.primaryAccent, {})
                        ControlIconButton(if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing, isSpeaker, tokens.primaryAccent, onToggleSpeaker)
                        ControlIconButton(Icons.Rounded.PersonAdd, false, tokens.primaryAccent, onInviteParticipant)
                    }
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(tokens.declineColor, CircleShape)
                            .clickable { onEndCall() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.CallEnd, "End Call", tint = Color.White)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 05 — GOOGLE CLASSIC ONGOING CALL
// -------------------------------------------------------------------------------------------------
@Composable
private fun GoogleClassicOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF004D40)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF00695C))
                    .padding(vertical = 36.dp),
            ) {
                Text(session.remoteDisplayName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(formatDuration(duration), fontSize = 14.sp, color = Color(0xFFB2DFDB))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ControlIconButton(if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, isMuted, tokens.declineColor, onToggleMute)
                    ControlIconButton(if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing, isSpeaker, tokens.primaryAccent, onToggleSpeaker)
                    ControlIconButton(Icons.Rounded.PersonAdd, false, tokens.primaryAccent, onInviteParticipant)
                }
                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(tokens.declineColor, CircleShape)
                        .clickable { onEndCall() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CallEnd, null, tint = Color.White)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 06 — VIVO / iQOO ONGOING CALL
// -------------------------------------------------------------------------------------------------
@Composable
private fun VivoIqooOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1B2A))
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 36.dp),
            ) {
                Text(session.remoteDisplayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(formatDuration(duration), fontSize = 14.sp, color = tokens.primaryAccent)
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = tokens.cardBackground.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        ControlIconButton(if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, isMuted, tokens.declineColor, onToggleMute)
                        ControlIconButton(if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing, isSpeaker, tokens.primaryAccent, onToggleSpeaker)
                        ControlIconButton(Icons.Rounded.PersonAdd, false, tokens.primaryAccent, onInviteParticipant)
                        ControlIconButton(Icons.Rounded.PictureInPicture, false, tokens.primaryAccent, onMinimize)
                    }
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(tokens.declineColor, RoundedCornerShape(27.dp))
                            .clickable { onEndCall() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.CallEnd, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
// -------------------------------------------------------------------------------------------------
// 07 — AZURE RING VERTICAL ONGOING CALL (reference active-call grid, tentative same
// family): teal gradient, big caller id + status chip + timer, 3-column control grid,
// big red end button. Only real Chaty call actions are rendered (no Notes/Record/Hold).
// -------------------------------------------------------------------------------------------------
@Composable
private fun AzureRingVerticalOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1E6E8C), Color(0xFF155E63), tokens.surfaceBackground),
                ),
            )
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 44.dp),
            ) {
                Text(
                    session.remoteDisplayName,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = (if (session.isVideo) "HD Video  " else "HD Voice  ") + formatDuration(duration),
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    AzureGridButton(if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, "Mute", isMuted, onToggleMute)
                    AzureGridButton(Icons.Rounded.PersonAdd, "Add call", false, onInviteParticipant)
                    AzureGridButton(if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Video", isCameraOn, onToggleCamera)
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AzureGridButton(if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing, "Speaker", isSpeaker, onToggleSpeaker)
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(tokens.declineColor, CircleShape)
                            .clickable { onEndCall() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    AzureGridButton(Icons.Rounded.PictureInPicture, "Minimize", false, onMinimize)
                }
            }
        }
    }
}

@Composable
private fun AzureGridButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF9FD8FF) else Color.White,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) Color(0xFF9FD8FF) else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
        )
    }
}

// -------------------------------------------------------------------------------------------------
// 08 — MONO CHEVRON DARK ONGOING CALL: minimal dark active screen.
// -------------------------------------------------------------------------------------------------
@Composable
private fun MonoChevronDarkOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.surfaceBackground)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 52.dp),
            ) {
                Text(session.remoteDisplayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                Spacer(Modifier.height(6.dp))
                Text(formatDuration(duration), fontSize = 15.sp, color = tokens.secondaryText)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ControlIconButton(if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, isMuted, tokens.primaryAccent, onToggleMute)
                    ControlIconButton(if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing, isSpeaker, tokens.primaryAccent, onToggleSpeaker)
                    ControlIconButton(if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, isCameraOn, tokens.primaryAccent, onToggleCamera)
                }
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(tokens.declineColor, CircleShape)
                        .clickable { onEndCall() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 09 — NEXTCLOUD TALK ONGOING CALL (reference: teal header, dark slate body, bottom
// control bar). Only real Chaty call actions are rendered.
// -------------------------------------------------------------------------------------------------
@Composable
private fun NextcloudTalkOngoingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    duration: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isCameraOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onInviteParticipant: () -> Unit,
    onMinimize: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.surfaceBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.primaryAccent)
                .padding(top = 44.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(session.remoteDisplayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(formatDuration(duration), fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C3E4A)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "N" },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.secondaryText,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(tokens.declineColor, CircleShape)
                    .clickable { onEndCall() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.primaryAccent)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing,
                "Speaker",
                tint = if (isSpeaker) Color.White else Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onToggleSpeaker() },
            )
            Icon(
                if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                "Video",
                tint = if (isCameraOn) Color.White else Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onToggleCamera() },
            )
            Icon(
                if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                "Mute",
                tint = if (isMuted) Color.White else Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onToggleMute() },
            )
        }
    }
}

fun ControlIconButton(
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(
                if (isActive) activeColor else Color.White.copy(alpha = 0.12f),
                CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color.White else Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(24.dp),
        )
    }
}


