package com.example.chat.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Port of lib/features/onboarding/permission_onboarding_screen.dart:
 *  contextual step-by-step permission onboarding after authentication.
 *  Same four steps, copy, icons and accent colors as the Dart source.
 *
 *  NOTE: the Dart screen has no call site in the api branch (nothing routes
 *  to it). It is ported 1:1 and left for the host to route; [onFinished]
 *  replaces the Dart pushAndRemoveUntil(MainNavigationShell). */
@Composable
fun PermissionOnboardingScreen(
    onFinished: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var currentStep by remember { mutableIntStateOf(0) }
    var isRequesting by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val steps = remember {
        listOf(
            PermissionStep(
                permission = ChatyPermissions.notification,
                title = "Stay in the loop",
                description = "Receive instant message notifications and live call alerts so you never miss an important conversation.",
                icon = Icons.Rounded.NotificationsActive,
                accentColor = Color(0xFF10B981),
            ),
            PermissionStep(
                permission = ChatyPermissions.camera,
                title = "Share your perspective",
                description = "Take photos, capture stories with AR filters, and start HD video calls with friends.",
                icon = Icons.Rounded.PhotoCamera,
                accentColor = Color(0xFF6366F1),
            ),
            PermissionStep(
                permission = ChatyPermissions.microphone,
                title = "Clear voice & audio",
                description = "Send voice notes, record audio updates, and enjoy crystal-clear voice and video calls.",
                icon = Icons.Rounded.Mic,
                accentColor = Color(0xFFF59E0B),
            ),
            PermissionStep(
                permission = ChatyPermissions.photos,
                title = "Media sharing",
                description = "Send photos, videos, and documents directly from your gallery with ease.",
                icon = Icons.Rounded.PhotoLibrary,
                accentColor = Color(0xFFEC4899),
            ),
        )
    }

    fun advance() {
        if (currentStep < steps.size - 1) {
            currentStep += 1
        } else {
            // Dart _finishOnboarding: success haptic, then route home.
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onFinished()
        }
    }

    val requestPermission = rememberPermissionRequester { _, permanentlyDenied ->
        if (permanentlyDenied) {
            showSettingsDialog = true
        } else {
            isRequesting = false
            advance()
        }
    }

    val step = steps[currentStep]

    if (showSettingsDialog) {
        // Dart ChatyConfirmDialog: 'Permission Required' / 'Open Settings'.
        AlertDialog(
            onDismissRequest = {
                showSettingsDialog = false
                isRequesting = false
                advance()
            },
            title = { Text("Permission Required") },
            text = {
                Text("This feature was disabled in settings. Would you like to open settings to enable it?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    isRequesting = false
                    ChatyPermissions.openAppSettings(context)
                    advance()
                }) { Text("Open Settings") }
            },
        )
    }

    Scaffold(containerColor = colors.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            // Progress indicator dots.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                steps.forEachIndexed { index, _ ->
                    val active = index == currentStep
                    val dotColor by animateColorAsState(
                        if (active) colors.primary else colors.primary.copy(alpha = 0.2f),
                        animationSpec = tween(240),
                        label = "dotColor",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .height(8.dp)
                            .background(dotColor, RoundedCornerShape(4.dp)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))

            // Step icon.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp)
                    .background(step.accentColor.copy(alpha = 0.12f), CircleShape)
                    .border(1.5.dp, step.accentColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(step.icon, contentDescription = null, tint = step.accentColor, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(32.dp))

            Text(
                step.title,
                color = colors.foreground,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                step.description,
                color = colors.foregroundSecondary,
                fontSize = 15.sp,
                lineHeight = (15 * 1.45).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    isRequesting = true
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    requestPermission(step.permission)
                },
                enabled = !isRequesting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRequesting) "Requesting…" else "Allow Permission",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { advance() }, enabled = !isRequesting) {
                    Text(
                        if (currentStep == steps.size - 1) "Finish" else "Not Now",
                        color = colors.foregroundTertiary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private class PermissionStep(
    val permission: String?,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
)
