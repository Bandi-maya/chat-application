package com.example.chat.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Port of features/calls/incoming_call_screen.dart: full-screen incoming call
 * UI with caller details, voice/video indication, and Accept / Decline
 * actions. The layout is selected through Settings > Calls > Call Screen
 * Style; templates are original Chaty designs.
 */
@Composable
fun IncomingCallScreen(
    session: ChatyCallSession,
    template: CallScreenTemplate,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val data = IncomingCallData(session, onAccept, onDecline)
    when (template) {
        CallScreenTemplate.chatyClassic -> ClassicIncomingCall(data)
        CallScreenTemplate.sheet -> SheetIncomingCall(data)
        CallScreenTemplate.card -> CardIncomingCall(data)
        CallScreenTemplate.edge -> EdgeIncomingCall(data)
    }
}

private class IncomingCallData(
    val session: ChatyCallSession,
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
) {
    val initials: String
        get() = session.remoteAvatarInitials
            ?: (if (session.remoteDisplayName.isNotEmpty()) {
                session.remoteDisplayName.substring(0, 1).uppercase()
            } else {
                "U"
            })
}

@Composable
private fun CallTypePill(isVideo: Boolean) {
    val theme = LocalThemeConfig.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(argbWithAlpha(theme.accentColor, 0.12)), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
            contentDescription = null,
            tint = Color(theme.accentColor),
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (isVideo) "Incoming Video Call" else "Incoming Voice Call",
            color = Color(theme.accentColor),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CallerName(session: ChatyCallSession, align: TextAlign = TextAlign.Center) {
    val theme = LocalThemeConfig.current
    Column(
        horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            session.remoteDisplayName,
            textAlign = align,
            color = Color(theme.primaryTextColor),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Chaty Secure Direct Call",
            textAlign = align,
            color = Color(theme.secondaryTextColor),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RoundAction(
    label: String,
    isVideoIcon: Boolean,
    isEnd: Boolean,
    color: Color,
    onColor: Color,
    onTap: () -> Unit,
) {
    val theme = LocalThemeConfig.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .background(color, CircleShape)
                .clickable { onTap() },
        ) {
            Icon(
                when {
                    isEnd -> Icons.Rounded.CallEnd
                    isVideoIcon -> Icons.Rounded.Videocam
                    else -> Icons.Rounded.Call
                },
                contentDescription = label,
                tint = onColor,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = Color(theme.secondaryTextColor),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActionPair(data: IncomingCallData) {
    val theme = LocalThemeConfig.current
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RoundAction(
            label = "Decline",
            isVideoIcon = false,
            isEnd = true,
            color = Color(theme.dangerColor),
            onColor = Color.White,
            onTap = data.onDecline,
        )
        RoundAction(
            label = "Answer",
            isVideoIcon = data.session.isVideo,
            isEnd = false,
            color = Color(theme.successColor),
            onColor = Color.White,
            onTap = data.onAccept,
        )
    }
}

/** Chaty Classic: centered identity with floating round actions. */
@Composable
private fun ClassicIncomingCall(data: IncomingCallData) {
    val theme = LocalThemeConfig.current
    val session = data.session
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            CallTypePill(isVideo = session.isVideo)
            Spacer(Modifier.height(48.dp))
            CallAvatar(initials = data.initials, colorHex = session.remoteAvatarColorHex, size = 110.dp)
            Spacer(Modifier.height(16.dp))
            CallerName(session)
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(bottom = 32.dp)) {
                ActionPair(data)
            }
        }
    }
}

/** Sheet: identity on the canvas, actions grouped in a bottom sheet. */
@Composable
private fun SheetIncomingCall(data: IncomingCallData) {
    val theme = LocalThemeConfig.current
    val session = data.session
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Spacer(Modifier.height(32.dp))
            CallTypePill(isVideo = session.isVideo)
            Spacer(Modifier.weight(1f))
            CallAvatar(initials = data.initials, colorHex = session.remoteAvatarColorHex, size = 96.dp)
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(theme.cardColor),
                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    )
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 32.dp),
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            Color(argbWithAlpha(theme.secondaryTextColor, 0.3)),
                            CircleShape,
                        ),
                )
                Spacer(Modifier.height(16.dp))
                CallerName(session)
                Spacer(Modifier.height(24.dp))
                ActionPair(data)
            }
        }
    }
}

/** Card: the whole incoming UI floats in a single centered card. */
@Composable
private fun CardIncomingCall(data: IncomingCallData) {
    val theme = LocalThemeConfig.current
    val session = data.session
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .background(
                        Color(theme.cardColor),
                        RoundedCornerShape(28.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            ) {
                CallTypePill(isVideo = session.isVideo)
                Spacer(Modifier.height(24.dp))
                CallAvatar(initials = data.initials, colorHex = session.remoteAvatarColorHex, size = 88.dp)
                Spacer(Modifier.height(12.dp))
                CallerName(session)
                Spacer(Modifier.height(24.dp))
                ActionPair(data)
            }
        }
    }
}

/** Edge: minimal chrome, identity pinned top-left, full-width edge actions. */
@Composable
private fun EdgeIncomingCall(data: IncomingCallData) {
    val theme = LocalThemeConfig.current
    val session = data.session
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallAvatar(initials = data.initials, colorHex = session.remoteAvatarColorHex, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    CallerName(session, align = TextAlign.Start)
                }
            }
            Spacer(Modifier.height(12.dp))
            CallTypePill(isVideo = session.isVideo)
            Spacer(Modifier.weight(1f))
            Row {
                Button(
                    onClick = data.onDecline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.dangerColor),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Rounded.CallEnd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Decline", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = data.onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.successColor),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(
                        if (session.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Answer", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
