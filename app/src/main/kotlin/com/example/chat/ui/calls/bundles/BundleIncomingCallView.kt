package com.example.chat.ui.calls.bundles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
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
 * Samsung = tap the action circle; Google Latest = horizontal puck;
 * red decline / green answer buttons; Azure Ring & Mono Chevron = vertical two-target drag;
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
    actionPending: Boolean = false,
    banner: Boolean = false,
    onExpand: () -> Unit = {},
    onMinimize: (() -> Unit)? = null,
) {
    val tokens = remember(bundleId) { CallBundleRegistry.getTokens(bundleId) }

    if (banner) {
        SharedIncomingCallBanner(session, actionPending, onAccept, onDecline, onExpand)
        return
    }
    Box(Modifier.fillMaxSize()) {
    when (bundleId) {
        CallBundleId.CHATY_DEFAULT -> ChatyDefaultIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.APPLE_IOS -> AppleIosIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.SAMSUNG_ONE_UI -> SamsungOneUiIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.GOOGLE_PHONE_LATEST -> GoogleLatestIncomingCall(tokens, session, onAccept, onDecline, actionPending)
        CallBundleId.GOOGLE_PHONE_CLASSIC -> GoogleClassicIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.VIVO_IQOO -> VivoIqooIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.AZURE_RING_VERTICAL -> AzureRingVerticalIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.MONO_CHEVRON_DARK -> MonoChevronDarkIncomingCall(tokens, session, onAccept, onDecline)
        CallBundleId.NEXTCLOUD_TALK -> NextcloudTalkIncomingCall(tokens, session, onAccept, onDecline)
    }
        if (onMinimize != null) {
            TextButton(onClick = onMinimize, modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Text("Show banner", color = tokens.primaryText)
            }
        }
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
                        CallTypeCenterIcon(session, tint = Color.White, modifier = Modifier.size(44.dp))
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
    val isLockedScreenSimulation = true // Approved slider interaction, no testing-mode switch in production.

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
                    text = if (session.isVideo) "Chaty video call" else "Chaty Audio…",
                    fontSize = 17.sp,
                    color = Color(0xFF8E8E93),
                )

                Spacer(Modifier.height(24.dp))
                CallTypeCenterIcon(session, tint = Color.White, modifier = Modifier.size(64.dp))


            }

            // Bottom Section: iOS Style Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
private fun IosSlideToAnswerPill(accentColor: Color, onConfirmed: () -> Unit) {
    ReferenceHorizontalPuck(false, Color.White.copy(alpha = 0.16f), accentColor, Color(0xFFFF3B30),
        onConfirmed, {}, Modifier.fillMaxWidth(), "Slide to answer")
}

/** Tracks the finger directly, clamps to measured width, and resets after cancellation/success. */
@Composable
private fun ReferenceHorizontalPuck(twoWay: Boolean, background: Color, accept: Color, decline: Color,
    onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier, description: String) {
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val scope = rememberCoroutineScope()
    val acceptNow by rememberUpdatedState(onAccept)
    val declineNow by rememberUpdatedState(onDecline)
    var offset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.heightIn(min = 80.dp).clip(RoundedCornerShape(40.dp)).background(background)) {
        val range = with(LocalDensity.current) { ((maxWidth - 76.dp).coerceAtLeast(24.dp) / (if (twoWay) 2 else 1)).toPx() }
        val home = if (twoWay) Alignment.Center else Alignment.CenterStart
        if (twoWay) {
            Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Decline", color = decline, fontSize = 14.sp)
                Text("Answer", color = accept, fontSize = 14.sp)
            }
        } else Text("slide to answer", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(start = 36.dp), fontSize = 16.sp)
        Box(Modifier.align(home).padding(horizontal = 6.dp).offset { IntOffset(offset.roundToInt(), 0) }
            .size(64.dp).clip(CircleShape).background(Color.White)
            .semantics {
                contentDescription = description
                customActions = buildList {
                    add(CustomAccessibilityAction("Answer call") { acceptNow(); true })
                    if (twoWay) add(CustomAccessibilityAction("Decline call") { declineNow(); true })
                }
            }.testTag(if (twoWay) "google_puck" else "ios_answer_puck")
            .pointerInput(range, reduced) {
                detectDragGestures(
                    onDragStart = { if (!settling) offset = 0f },
                    onDrag = { change, drag -> if (!settling) { change.consume(); offset = (offset + drag.x).coerceIn(if (twoWay) -range else 0f, range) } },
                    onDragCancel = { if (!settling) { settling = true; scope.launch {
                        val start = offset
                        androidx.compose.animation.core.animate(start, 0f, animationSpec = tween(if (reduced) 0 else 200)) { value, _ -> offset = value }
                        settling = false
                    } } },
                    onDragEnd = { if (!settling) {
                        settling = true
                        val direction = when { offset >= range * 0.7f -> 1; twoWay && offset <= -range * 0.7f -> -1; else -> 0 }
                        scope.launch {
                            val start = offset
                            androidx.compose.animation.core.animate(start, direction * range, animationSpec = tween(if (reduced) 0 else 180)) { value, _ -> offset = value }
                            offset = 0f; settling = false
                            if (direction == 1) acceptNow() else if (direction == -1) declineNow()
                        }
                    } },
                )
            }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = if (offset < 0f) decline else accept, modifier = Modifier.size(30.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 03 — SAMSUNG ONE UI INCOMING CALL (reference verified One UI by its "Bixby text call"
// pill; that feature is unsupported by Chaty and intentionally not rendered).
// Approved reference interaction: tap the action circle.
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
                CallTypeCenterIcon(session, tint = Color.White, modifier = Modifier.size(36.dp))
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
                text = "Tap Answer or Decline",
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
                TapHaloControl(
                    label = "Answer call",
                    icon = Icons.Rounded.Call,
                    iconTint = tokens.acceptColor,
                    haloColor = tokens.acceptColor.copy(alpha = 0.25f),
                    onTrigger = onAccept,
                    modifier = Modifier.testTag("samsung_answer_control"),
                )
                TapHaloControl(
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
// with name chip and large avatar. Chaty product choice: explicit red decline
// left / green answer right; not a claim of one universal Google gesture.)
// -------------------------------------------------------------------------------------------------
@Composable
private fun GoogleLatestIncomingCall(
    tokens: CallBundleThemeTokens,
    session: ChatyCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    actionPending: Boolean,
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
                    CallTypeCenterIcon(session, tint = tokens.primaryText, modifier = Modifier.size(64.dp))
                }
            }

            DeclineAnswerPuckBar(tokens,
                onAccept = { if (!actionPending) onAccept() },
                onDecline = { if (!actionPending) onDecline() },
                modifier = Modifier.padding(bottom = 36.dp).testTag("google_latest_puck_bar"))

        }
    }
}

/** Material You bottom bar: Decline | draggable white puck | Answer. */
@Composable
private fun DeclineAnswerPuckBar(tokens: CallBundleThemeTokens, onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    ReferenceHorizontalPuck(true, tokens.cardBackground, tokens.acceptColor, tokens.declineColor,
        onAccept, onDecline, modifier.fillMaxWidth(), "Answer or decline puck")
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
                    CallTypeCenterIcon(session, tint = Color(0xFF00695C), modifier = Modifier.size(34.dp))
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
                VerticalDragPuck(
                    puckDescription = "Google classic swipe up to answer",
                    onDragUp = onAccept, onDragDown = {},
                    modifier = Modifier.testTag("google_classic_answer_puck"),
                ) { Box(Modifier.size(72.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Call, null, tint = tokens.acceptColor, modifier = Modifier.size(32.dp))
                } }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDecline) { Text("Decline", color = tokens.declineColor) }

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
                    CallTypeCenterIcon(session, tint = tokens.primaryAccent, modifier = Modifier.size(38.dp))
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
            val reducedRingMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
            val pulseAlpha = if (reducedRingMotion) 0.18f else {
                val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ringing-hint")
                val alpha by transition.animateFloat(initialValue = 0.10f, targetValue = 0.22f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        androidx.compose.animation.core.tween(1800), androidx.compose.animation.core.RepeatMode.Reverse), label = "ringing-opacity")
                alpha
            }
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
                            .background(tokens.primaryAccent.copy(alpha = pulseAlpha)),
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
                        contentAlignment = Alignment.Center,
                    ) {
                        CallTypeCenterIcon(session, tint = tokens.primaryAccent, modifier = Modifier.size(28.dp))
                    }
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
                    CallTypeCenterIcon(session, tint = Color(0xFF111114), modifier = Modifier.size(36.dp))
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
                CallTypeCenterIcon(session, tint = tokens.primaryAccent, modifier = Modifier.size(26.dp))
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
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var gestureAdmitted by remember { mutableStateOf(false) }
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
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White)
                .semantics {
                    contentDescription = label
                    // TalkBack/switch-access activation; touch input still requires the drag gesture.
                    onClick(label = label) { if (!settling) onTrigger(); true }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            gestureAdmitted = !settling
                            if (gestureAdmitted) { dx = 0f; dy = 0f }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (gestureAdmitted && !settling) {
                                dx += dragAmount.x
                                dy += dragAmount.y
                            }
                        },
                        onDragCancel = {
                            if (gestureAdmitted && !settling) { dx = 0f; dy = 0f }
                            gestureAdmitted = false
                        },
                        onDragEnd = {
                            if (gestureAdmitted && !settling) {
                                gestureAdmitted = false
                                settling = true
                                val startX = dx
                                val startY = dy
                                val distance = kotlin.math.hypot(startX, startY)
                                val accepted = distance >= thresholdPx
                                coroutineScope.launch {
                                    try {
                                        val scale = if (accepted) thresholdPx * 1.8f / distance else 0f
                                        androidx.compose.animation.core.animate(
                                            initialValue = 0f, targetValue = 1f,
                                            animationSpec = tween(if (accepted) 120 else 220),
                                        ) { fraction, _ ->
                                            dx = startX * (1f + (scale - 1f) * fraction)
                                            dy = startY * (1f + (scale - 1f) * fraction)
                                        }
                                        if (accepted) onTrigger()
                                    } finally {
                                        // A mounted control can be reused after either result.
                                        // Never let its previous exit offset count as a new drag.
                                        dx = 0f
                                        dy = 0f
                                        settling = false
                                    }
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
 *  true tap recognizer completes without crossing touch slop (Nextcloud accept button). */
@Composable
private fun VerticalDragPuck(
    puckDescription: String,
    onDragUp: () -> Unit,
    onDragDown: () -> Unit,
    modifier: Modifier = Modifier,
    thresholdDp: Float = 80f,
    onTap: (() -> Unit)? = null,
    upA11yLabel: String? = "Decline call",
    downA11yLabel: String? = "Answer call",
    puckContent: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var dy by remember { mutableStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var gestureAdmitted by remember { mutableStateOf(false) }
    val currentUp by androidx.compose.runtime.rememberUpdatedState(onDragUp)
    val currentDown by androidx.compose.runtime.rememberUpdatedState(onDragDown)
    val currentTap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val thresholdPx = with(LocalDensity.current) { thresholdDp.dp.toPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .offset { IntOffset(0, dy.roundToInt()) }
            .semantics {
                contentDescription = puckDescription
                customActions = listOfNotNull(
                    upA11yLabel?.let { CustomAccessibilityAction(it) { if (!settling) currentUp(); true } },
                    downA11yLabel?.let { CustomAccessibilityAction(it) { if (!settling) currentDown(); true } },
                )
            }
            .then(if (onTap != null) Modifier.pointerInput(Unit) {
                // A drag recognizer has already crossed touch slop. Its small residual
                // displacement must never be reclassified as a tap on release.
                detectTapGestures(onTap = { if (!settling) currentTap?.invoke() })
            } else Modifier)
            .pointerInput(thresholdPx, reduceMotion) {
                detectDragGestures(
                    onDragStart = {
                        gestureAdmitted = !settling
                        if (gestureAdmitted) dy = 0f
                    },
                    onDrag = { change, dragAmount ->
                        if (gestureAdmitted && !settling) {
                            change.consume()
                            dy = (dy + dragAmount.y).coerceIn(-thresholdPx * 1.4f, thresholdPx * 1.4f)
                        }
                    },
                    onDragCancel = {
                        if (gestureAdmitted && !settling) dy = 0f
                        gestureAdmitted = false
                    },
                    onDragEnd = {
                        if (gestureAdmitted && !settling) {
                            gestureAdmitted = false
                            settling = true
                            val start = dy
                            val direction = when {
                                start <= -thresholdPx -> -1
                                start >= thresholdPx -> 1
                                else -> 0
                            }
                            coroutineScope.launch {
                                try {
                                    val target = direction * thresholdPx * 1.4f
                                    if (!reduceMotion) {
                                        androidx.compose.animation.core.animate(
                                            initialValue = start, targetValue = target,
                                            animationSpec = tween(if (direction != 0) 120 else 220),
                                        ) { value, _ -> dy = value }
                                    } else dy = target
                                    when {
                                        direction < 0 -> currentUp()
                                        direction > 0 -> currentDown()
                                    }
                                } finally {
                                    // The parent may leave this control mounted after a callback.
                                    // An exit offset never belongs to the next gesture.
                                    dy = 0f
                                    settling = false
                                }
                            }
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


/** Centre call-type badge, distinct from Answer/Decline actions and caller name. */
@Composable
private fun CallTypeCenterIcon(session: ChatyCallSession, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (session.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
        contentDescription = if (session.isVideo) "Video call" else "Voice call",
        tint = tint,
        modifier = modifier.testTag("incoming_call_type_badge"),
    )
}

/** Reference-approved Samsung tap controls. Uses the same incoming action gate as every other bundle. */
@Composable
private fun TapHaloControl(label: String, icon: ImageVector, iconTint: Color, haloColor: Color,
    onTrigger: () -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier.size(100.dp).clip(CircleShape).background(haloColor)
            .semantics { contentDescription = label }
            .clickable(onClickLabel = label, onClick = onTrigger), contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(32.dp))
            }
        }
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

/** Shared in-app heads-up presentation. No fake screening/AI actions; OS notification layout stays system-owned. */
@Composable
fun SharedIncomingCallBanner(session: ChatyCallSession, pending: Boolean,
    onAccept: () -> Unit, onDecline: () -> Unit, onExpand: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF26272B), tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Open incoming call", onClick = onExpand)) {
                    Text(session.remoteDisplayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(if (session.isVideo) "Incoming video call · Chaty" else "Incoming call · Chaty", color = Color(0xFFD7D7DB), fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onDecline, enabled = !pending, modifier = Modifier.weight(1f).heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEB6B63), contentColor = Color.Black)) {
                        Icon(Icons.Rounded.CallEnd, null); Spacer(Modifier.width(6.dp)); Text("Decline")
                    }
                    Button(onAccept, enabled = !pending, modifier = Modifier.weight(1f).heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF70C99A), contentColor = Color.Black)) {
                        Icon(Icons.Rounded.Call, null); Spacer(Modifier.width(6.dp)); Text("Answer")
                    }
                }
            }
        }
    }
}
