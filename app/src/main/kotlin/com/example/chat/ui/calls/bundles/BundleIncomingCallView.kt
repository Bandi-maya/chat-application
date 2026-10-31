package com.example.chat.ui.calls.bundles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
 * Renders the incoming call screen specifically styled for each bundle.
 * Gesture-faithful answer mechanics per user-supplied references (2026-10-31):
 * Samsung = drag the action circle out of its halo; Google Latest = drag the
 * center puck left/right; Azure Ring & Mono Chevron = vertical two-target drag;
 * Nextcloud Talk = tap or swipe up to accept. Unsupported reference functions
 * (Bixby text call, AI Call Assistant, Screen call, Live Translation, Remind,
 * SMS, Voicemail) are intentionally NOT rendered - no fake buttons.
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
        CallBundleId.AZURE_RING_VERTICAL -> AzureRingVerticalIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.MONO_CHEVRON_DARK -> MonoChevronDarkIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.NEXTCLOUD_TALK -> NextcloudTalkIncomingCall(tokens, session, onAccept, onDecline)
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
// 03 — SAMSUNG ONE UI INCOMING CALL (reference verified One UI by its "Bixby text call"
// pill; that feature is unsupported by Chaty and intentionally not rendered).
// One UI default answer mechanic: drag the action circle OUT of its halo.
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
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B2A4A), tokens.surfaceBackground, Color(0xFF241B3A)),
                ),
            )
            .testTag("incoming_screen_samsung_one_ui"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Incoming call",
                color = tokens.secondaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(32.dp))
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
                text = if (session.isVideo) "Video call" else "Voice call",
                fontSize = 15.sp,
                color = tokens.secondaryText,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Drag a button out of its circle",
                color = tokens.secondaryText,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 18.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DragOutOfCircleControl(
                    label = "Answer call",
                    icon = Icons.Rounded.Call,
                    iconTint = tokens.acceptColor,
                    haloColor = tokens.acceptColor.copy(alpha = 0.25f),
                    onTrigger = onAccept,
                    modifier = Modifier.testTag("samsung_answer_control"),
                )
                DragOutOfCircleControl(
                    label = "Decline call",
                    icon = Icons.Rounded.CallEnd,
                    iconTint = tokens.declineColor,
                    haloColor = tokens.declineColor.copy(alpha = 0.25f),
                    onTrigger = onDecline,
                    modifier = Modifier.testTag("samsung_decline_control"),
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 04 — GOOGLE PHONE LATEST (Material You / Pixel; reference: M3 fullscreen incoming
// with name chip, large avatar and the Decline | puck | Answer bottom bar).
// Mechanic: drag the center puck right to answer, left to decline.
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
            .background(tokens.surfaceBackground)
            .padding(24.dp)
            .testTag("incoming_screen_google_phone_latest"),
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
                Text(
                    text = session.remoteDisplayName,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.primaryText,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = CircleShape,
                    color = tokens.cardBackground,
                ) {
                    Text(
                        text = if (session.isVideo) "Video call" else "Incoming call",
                        color = tokens.secondaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(36.dp))
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(tokens.cardBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "G" },
                        fontSize = 64.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText,
                    )
                }
            }

            DeclineAnswerPuckBar(
                tokens = tokens,
                onAccept = onAccept,
                onDecline = onDecline,
                modifier = Modifier.padding(bottom = 36.dp),
            )
        }
    }
}

/** Material You bottom bar: Decline | draggable white puck | Answer. */
@Composable
private fun DeclineAnswerPuckBar(
    tokens: CallBundleThemeTokens,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val maxDragPx = with(LocalDensity.current) { 120.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(tokens.cardBackground),
    ) {
        Text(
            text = "Decline",
            color = tokens.declineColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp),
        )
        Text(
            text = "Answer",
            color = tokens.acceptColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(drag.value.roundToInt(), 0) }
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White)
                .semantics {
                    contentDescription = "Answer or decline puck"
                    customActions = listOf(
                        CustomAccessibilityAction("Answer call") { onAccept(); true },
                        CustomAccessibilityAction("Decline call") { onDecline(); true },
                    )
                }
                .testTag("google_puck")
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            drag.snapTo((drag.value + delta).coerceIn(-maxDragPx, maxDragPx))
                        }
                    },
                    onDragStopped = {
                        when {
                            drag.value <= -maxDragPx * 0.7f -> {
                                coroutineScope.launch {
                                    drag.animateTo(-maxDragPx)
                                    onDecline()
                                }
                            }
                            drag.value >= maxDragPx * 0.7f -> {
                                coroutineScope.launch {
                                    drag.animateTo(maxDragPx)
                                    onAccept()
                                }
                            }
                            else -> coroutineScope.launch { drag.animateTo(0f, tween(200)) }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = null,
                tint = tokens.acceptColor,
                modifier = Modifier.size(30.dp),
            )
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
// 07 — AZURE RING VERTICAL (reference-clustered, brand unverified): deep-blue screen,
// central pulse ring on a dotted vertical track; drag the ring UP to the red arc to
// decline or DOWN to the green phone to answer. Remind/SMS quick actions from the
// reference are system-dialer functions and intentionally not rendered.
// -------------------------------------------------------------------------------------------------
@Composable
private fun AzureRingVerticalIncomingCall(
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
                    listOf(Color(0xFF123058), tokens.surfaceBackground, Color(0xFF081428)),
                ),
            )
            .testTag("incoming_screen_azure_ring_vertical"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = session.remoteDisplayName,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.primaryText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (session.isVideo) "Incoming video call" else "Incoming voice call",
                fontSize = 15.sp,
                color = tokens.secondaryText,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.CallEnd,
                contentDescription = null,
                tint = tokens.declineColor,
                modifier = Modifier.size(30.dp),
            )
            VerticalDottedTrack(tokens.secondaryText)
            VerticalDragPuck(
                puckDescription = "Azure ring answer or decline puck",
                onDragUp = onDecline,
                onDragDown = onAccept,
                modifier = Modifier.testTag("azure_puck"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(tokens.primaryAccent.copy(alpha = 0.16f)),
                    )
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(tokens.primaryAccent.copy(alpha = 0.32f)),
                    )
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
            VerticalDottedTrack(tokens.secondaryText)
            Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = null,
                tint = tokens.acceptColor,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun VerticalDottedTrack(color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 10.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.6f)),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 08 — MONO CHEVRON DARK (reference-clustered, brand unverified): minimal dark screen,
// central white puck; red chevrons up = decline, green chevrons down = answer.
// The reference's "SMS reply" and "AI Call Assistant" pills are unsupported by Chaty
// and intentionally not rendered.
// -------------------------------------------------------------------------------------------------
@Composable
private fun MonoChevronDarkIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.surfaceBackground)
            .testTag("incoming_screen_mono_chevron_dark"),
    ) {
        Text(
            text = "Incoming call",
            color = tokens.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 20.dp, start = 20.dp),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Text(
                text = session.remoteDisplayName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.primaryText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (session.isVideo) "Video call" else "Voice call",
                fontSize = 14.sp,
                color = tokens.secondaryText,
            )
            Spacer(Modifier.weight(1f))
            ChevronPair(up = true, color = tokens.declineColor)
            VerticalDragPuck(
                puckDescription = "Mono chevron answer or decline puck",
                onDragUp = onDecline,
                onDragDown = onAccept,
                modifier = Modifier.testTag("mono_puck"),
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        tint = Color(0xFF111114),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            ChevronPair(up = false, color = tokens.acceptColor)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChevronPair(up: Boolean, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        val icon = if (up) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Icon(
            icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.45f),
            modifier = Modifier
                .size(26.dp)
                .offset(y = (-8).dp),
        )
    }
}

// -------------------------------------------------------------------------------------------------
// 09 — NEXTCLOUD TALK STYLE (reference verified by its own "Nextcloud Talk voice call"
// header): teal header with avatar + name + INCOMING, dark slate body, bottom action
// row; accept = tap or swipe up per the reference caption.
// -------------------------------------------------------------------------------------------------
@Composable
private fun NextcloudTalkIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.surfaceBackground)
            .testTag("incoming_screen_nextcloud_talk"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.primaryAccent)
                .padding(top = 44.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD8DEE3)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = session.remoteDisplayName.take(1).uppercase().ifEmpty { "N" },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.primaryAccent,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = session.remoteDisplayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (session.isVideo) "INCOMING VIDEO" else "INCOMING",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Swipe up to accept",
            color = tokens.secondaryText,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(tokens.declineColor)
                    .clickable { onDecline() }
                    .semantics { contentDescription = "Decline call" }
                    .testTag("nextcloud_decline"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CallEnd,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            VerticalDragPuck(
                puckDescription = "Accept call",
                onDragUp = onAccept,
                onDragDown = {},
                tapBelowPx = 14f,
                onTap = onAccept,
                upA11yLabel = "Accept call",
                downA11yLabel = null,
                modifier = Modifier.testTag("nextcloud_accept"),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(tokens.acceptColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Shared gesture controls
// -------------------------------------------------------------------------------------------------

/** Samsung-style control: drag the white circle OUT of its halo (past the
 *  threshold) to trigger; it springs back otherwise. Tap is intentionally not a
 *  trigger - gesture-to-answer is the reference default and prevents pocket taps. */
@Composable
private fun DragOutOfCircleControl(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    haloColor: Color,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { 52.dp.toPx() }
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(108.dp)) {
        Box(
            Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(haloColor),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(dx.value.roundToInt(), dy.value.roundToInt()) }
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White)
                .semantics {
                    contentDescription = label
                    // TalkBack/switch-access activation; touch input still requires the drag gesture.
                    onClick(label = label) { onTrigger(); true }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                dx.snapTo(dx.value + dragAmount.x)
                                dy.snapTo(dy.value + dragAmount.y)
                            }
                        },
                        onDragEnd = {
                            val dist = kotlin.math.hypot(dx.value, dy.value)
                            if (dist >= thresholdPx) {
                                coroutineScope.launch {
                                    val scale = (thresholdPx * 1.8f) / dist
                                    dx.animateTo(dx.value * scale, tween(120))
                                    dy.animateTo(dy.value * scale, tween(120))
                                    onTrigger()
                                }
                            } else {
                                coroutineScope.launch {
                                    dx.animateTo(0f, tween(220))
                                    dy.animateTo(0f, tween(220))
                                }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

/** Vertical two-target puck: drag up past the threshold = [onDragUp] (decline),
 *  down = [onDragDown] (answer); springs back otherwise. [onTap] fires when the
 *  gesture ends almost stationary (Nextcloud-style accept button). */
@Composable
private fun VerticalDragPuck(
    puckDescription: String,
    onDragUp: () -> Unit,
    onDragDown: () -> Unit,
    modifier: Modifier = Modifier,
    thresholdDp: Float = 80f,
    tapBelowPx: Float = 0f,
    onTap: (() -> Unit)? = null,
    upA11yLabel: String? = "Decline call",
    downA11yLabel: String? = "Answer call",
    puckContent: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val dy = remember { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { thresholdDp.dp.toPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .offset { IntOffset(0, dy.value.roundToInt()) }
            .semantics {
                contentDescription = puckDescription
                customActions = listOfNotNull(
                    upA11yLabel?.let { CustomAccessibilityAction(it) { onDragUp(); true } },
                    downA11yLabel?.let { CustomAccessibilityAction(it) { onDragDown(); true } },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            dy.snapTo(
                                (dy.value + dragAmount.y)
                                    .coerceIn(-thresholdPx * 1.4f, thresholdPx * 1.4f),
                            )
                        }
                    },
                    onDragEnd = {
                        when {
                            dy.value <= -thresholdPx -> coroutineScope.launch {
                                dy.animateTo(-thresholdPx * 1.4f, tween(120))
                                onDragUp()
                            }
                            dy.value >= thresholdPx -> coroutineScope.launch {
                                dy.animateTo(thresholdPx * 1.4f, tween(120))
                                onDragDown()
                            }
                            onTap != null && kotlin.math.abs(dy.value) < tapBelowPx -> {
                                onTap()
                            }
                            else -> coroutineScope.launch { dy.animateTo(0f, tween(220)) }
                        }
                    },
                )
            },
    ) {
        puckContent()
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


