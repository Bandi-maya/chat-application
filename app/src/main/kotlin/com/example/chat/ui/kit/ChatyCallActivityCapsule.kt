package com.example.chat.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.calls.CallPresentationController
import com.example.chat.ui.calls.CallPresentationMode

/**
 * Port of ChatyCallActivityCapsule (lib/ui/core/design_system/components/call_activity_capsule.dart).
 * Intentionally NOT built on tooltips (Dart comment: RawTooltip crashed outside an Overlay); accessibility via semantics
 * and 48dp targets. Hidden while a specialised surface owns the call (fullScreen / inAppIsland / inAppVideoPip /
 * systemPip). [presentationMode] defaults to the process-wide signal but is not observable; the caller must recompose
 * when it changes (it does whenever the call session changes).
 * Backdrop blur(18) and the BoxShadow(black 14%, blur 24, dy 8) are real, via ChatyBackdrop.kt (API 31+; below that the
 * blur is absent and so is the shadow). In the Dart tree the ClipRRect wraps the DecoratedBox, so the shadow is clipped
 * INSIDE the capsule and shows through only the 4% transparency of the fill: there is deliberately NO outer drop shadow.
 * Needs ChatyGlobalActivityHost to provide LocalChatyBackdrop. Pixels unverified.
 */
@Composable
fun ChatyCallActivityCapsule(
    contactName: String,
    status: String,
    isVideo: Boolean,
    isSpeaker: Boolean,
    onOpen: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier,
    presentationMode: CallPresentationMode = CallPresentationController.presentationModeSignal,
) {
    if (specializedSurfaceOwnsPresentation(presentationMode)) return
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val fill = scheme.primary.copy(alpha = .10f).compositeOver(scheme.surface.copy(alpha = .96f))
    Box(
        modifier
            // SafeArea(bottom: false, minimum: fromLTRB(14, 8, 14, 0))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .union(WindowInsets(left = 14.dp, top = 8.dp, right = 14.dp)),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape) // Dart ClipRRect(24)
                .chatyBackdropBlur(18.dp) // Dart BackdropFilter(blur 18)
                .chatyBoxShadow(Color.Black.copy(alpha = .14f), 24.dp, 0.dp, 8.dp, 24.dp) // BoxShadow, inside the clip as in Dart
                .background(fill)
                .border(BorderStroke(1.dp, scheme.primary.copy(alpha = .20f)), shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .semantics { contentDescription = "Return to call with $contactName. $status" }
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(start = 13.dp, top = 9.dp, end = 8.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call, null, Modifier.size(20.dp), tint = scheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(contactName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface, fontWeight = FontWeight.W800, fontSize = 13.5.sp)
                    Spacer(Modifier.height(1.dp))
                    Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurfaceVariant, fontSize = 10.5.sp, fontWeight = FontWeight.W600)
                }
            }
            OverlaySafeCallAction(
                semanticsLabel = "Speaker",
                hint = if (isSpeaker) "Double tap to use the earpiece" else "Double tap to use the speaker",
                toggled = isSpeaker,
                icon = if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.Hearing,
                foreground = scheme.onSurface,
                onPressed = onToggleSpeaker,
            )
            Box(Modifier.padding(end = 8.dp)) {
                OverlaySafeCallAction(
                    semanticsLabel = "Hang up", hint = null, toggled = null,
                    icon = Icons.Rounded.CallEnd, foreground = scheme.onError, background = scheme.error, onPressed = onHangUp,
                )
            }
        }
    }
}

private fun specializedSurfaceOwnsPresentation(mode: CallPresentationMode): Boolean =
    mode == CallPresentationMode.fullScreen || mode == CallPresentationMode.inAppIsland ||
        mode == CallPresentationMode.inAppVideoPip || mode == CallPresentationMode.systemPip

/** Dart _OverlaySafeCallAction: 48x48 circle, 22dp icon. */
@Composable
private fun OverlaySafeCallAction(
    semanticsLabel: String,
    hint: String?,
    toggled: Boolean?,
    icon: ImageVector,
    foreground: Color,
    onPressed: () -> Unit,
    background: Color? = null,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .semantics {
                contentDescription = semanticsLabel
                if (toggled != null) stateDescription = if (toggled) "On" else "Off"
            }
            .clickable(role = Role.Button, onClickLabel = hint, onClick = onPressed),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = foreground)
    }
}
