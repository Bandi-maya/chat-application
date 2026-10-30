package com.example.chat.ui.calls.bundles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.services.ChatyCallSession
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Universal Unified Bundle Incoming Call Screen.
 * Renders the incoming call screen specifically styled for each of the six bundles.
 */
@Composable
fun BundleIncomingCallView(
    bundleId: CallBundleId,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onTogglePreviewMode: (() -> Unit)? = null,
) {
    val tokens = remember(bundleId) { CallBundleRegistry.getTokens(bundleId) }

    when (bundleId) {
        CallBundleId.CHATY_DEFAULT -> ChatyDefaultIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.APPLE_IOS -> AppleIosIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.SAMSUNG_ONE_UI -> SamsungOneUiIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.GOOGLE_PHONE_LATEST -> GoogleLatestIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.GOOGLE_PHONE_CLASSIC -> GoogleClassicIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.VIVO_IQOO -> VivoIqooIncomingCall(tokens, session, onAccept, onDecline)
    }
}

// -------------------------------------------------------------------------------------------------
// 01 — CHATY DEFAULT INCOMING CALL (Reference Image 1 & 3 inspired)
// -------------------------------------------------------------------------------------------------
@Composable
private fun ChatyDefaultIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0F172A))
                )
            )
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: Status & Call Type
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(tokens.pillCornerRadiusDp.dp),
                    color = tokens.primaryAccent.copy(alpha = 0.16f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (session.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = null,
                            tint = tokens.primaryAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (session.isVideo) "Incoming Video Call" else "Incoming Voice Call",
                            color = tokens.primaryAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))

                // Avatar with glowing ring
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .background(tokens.primaryAccent.copy(alpha = 0.2f), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .background(tokens.primaryAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "C" },
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.primaryText,
                )
                Text(
                    text = "Chaty Secure Call",
                    fontSize = 15.sp,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Bottom Actions: Decline & Accept
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BundleActionButton(
                    icon = Icons.Rounded.CallEnd,
                    label = "Decline",
                    color = tokens.declineColor,
                    onClick = onDecline,
                )
                BundleActionButton(
                    icon = if (session.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    label = "Accept",
                    color = tokens.acceptColor,
                    onClick = onAccept,
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 02 — APPLE iOS INCOMING CALL (Reference Image 2: Unlocked + Slide-To-Answer)
// -------------------------------------------------------------------------------------------------
@Composable
private fun AppleIosIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    var isLockedScreenSimulation by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E), Color(0xFF000000))
                )
            )
            .padding(horizontal = 28.dp, vertical = 40.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Section: Caller Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 36.dp),
            ) {
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (session.isVideo) "Chaty FaceTime Video…" else "Chaty Audio…",
                    fontSize = 17.sp,
                    color = Color(0xFF8E8E93),
                )

                // Quick toggle pill to test both locked slider & unlocked 2-button layouts
                Spacer(Modifier.height(16.dp))
                FilterChip(
                    selected = isLockedScreenSimulation,
                    onClick = { isLockedScreenSimulation = !isLockedScreenSimulation },
                    label = { Text(if (isLockedScreenSimulation) "Mode: Slide to Answer" else "Mode: Two-Button Accept") },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.White.copy(alpha = 0.7f),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.08f),
                    ),
                )
            }

            // Bottom Section: iOS Style Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Secondary actions row: Remind Me & Message
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IosSecondaryAction(icon = Icons.Rounded.Alarm, label = "Remind Me")
                    IosSecondaryAction(icon = Icons.Rounded.ChatBubble, label = "Message")
                }

                Spacer(Modifier.height(12.dp))

                if (isLockedScreenSimulation) {
                    // Slide to Answer Horizontal Slider
                    IosSlideToAnswerPill(
                        accentColor = tokens.acceptColor,
                        onConfirmed = onAccept,
                    )
                } else {
                    // Standard Two Circular Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BundleActionButton(
                            icon = Icons.Rounded.CallEnd,
                            label = "Decline",
                            color = tokens.declineColor,
                            onClick = onDecline,
                        )
                        BundleActionButton(
                            icon = Icons.Rounded.Call,
                            label = "Accept",
                            color = tokens.acceptColor,
                            onClick = onAccept,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosSecondaryAction(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun IosSlideToAnswerPill(
    accentColor: Color,
    onConfirmed: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val maxDragPx = with(LocalDensity.current) { 220.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "slide to answer  ››",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.White)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val next = (dragOffset.value + delta).coerceIn(0f, maxDragPx)
                            dragOffset.snapTo(next)
                        }
                    },
                    onDragStopped = {
                        if (dragOffset.value > maxDragPx * 0.75f) {
                            coroutineScope.launch {
                                dragOffset.animateTo(maxDragPx)
                                onConfirmed()
                            }
                        } else {
                            coroutineScope.launch {
                                dragOffset.animateTo(0f, tween(200))
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = "Slide to answer",
                tint = accentColor,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 03 — SAMSUNG ONE UI INCOMING CALL
// -------------------------------------------------------------------------------------------------
@Composable
private fun SamsungOneUiIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
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
            // One UI Top Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(tokens.cardBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "S" },
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (session.isVideo) "One UI Video Call" else "One UI Voice Call",
                    fontSize = 15.sp,
                    color = tokens.secondaryText,
                )
            }

            // One UI Reachable Bottom Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(tokens.cardCornerRadiusDp.dp),
                color = tokens.cardBackground,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onDecline() },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(tokens.declineColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Decline", color = Color.White, fontSize = 13.sp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onAccept() },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(tokens.acceptColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Accept", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 04 — GOOGLE PHONE LATEST (Material You / Pixel)
// -------------------------------------------------------------------------------------------------
@Composable
private fun GoogleLatestIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
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
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(tokens.cardBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "G" },
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.primaryText,
                )
                Text(
                    text = if (session.isVideo) "Pixel Video Call" else "Incoming Call",
                    fontSize = 14.sp,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Material 3 Tonal Bottom Container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(28.dp),
                color = tokens.cardBackground,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onDecline,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.declineColor),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Rounded.CallEnd, "Decline", tint = Color.White)
                    }

                    Text("Swipe or Tap", color = tokens.secondaryText, fontSize = 13.sp)

                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.acceptColor),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Rounded.Call, "Accept", tint = Color.White)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 05 — GOOGLE PHONE CLASSIC (Google Phone 5.1 / Classic Android Dialer)
// -------------------------------------------------------------------------------------------------
@Composable
private fun GoogleClassicIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF004D40)), // Classic Teal
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Teal Top Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF00695C))
                    .padding(vertical = 48.dp, horizontal = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "C" },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00695C),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "Classic Dialer Incoming Call",
                    fontSize = 14.sp,
                    color = Color(0xFFB2DFDB),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Classic Central Swipe Puck
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "▲ Swipe up to answer",
                    color = Color(0xFFB2DFDB),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BundleActionButton(
                        icon = Icons.Rounded.CallEnd,
                        label = "Reject",
                        color = tokens.declineColor,
                        onClick = onDecline,
                    )
                    BundleActionButton(
                        icon = Icons.Rounded.Call,
                        label = "Answer",
                        color = tokens.acceptColor,
                        onClick = onAccept,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 06 — VIVO / iQOO NATIVE CALL (OriginOS / FuntouchOS)
// -------------------------------------------------------------------------------------------------
@Composable
private fun VivoIqooIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF0B1320))
                )
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
                modifier = Modifier.padding(top = 40.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.primaryAccent.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "ORIGIN OS CALL",
                        color = tokens.primaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(tokens.primaryAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "V" },
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.primaryAccent,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "HD Voice Call",
                    fontSize = 14.sp,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Vivo Origin Pill Action Bar
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = tokens.cardBackground.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BundleActionButton(
                        icon = Icons.Rounded.CallEnd,
                        label = "Decline",
                        color = tokens.declineColor,
                        onClick = onDecline,
                    )
                    BundleActionButton(
                        icon = Icons.Rounded.Call,
                        label = "Answer",
                        color = tokens.acceptColor,
                        onClick = onAccept,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Common Reusable Action Button
// -------------------------------------------------------------------------------------------------
@Composable
fun BundleActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

