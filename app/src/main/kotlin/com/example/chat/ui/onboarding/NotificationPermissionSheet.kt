package com.example.chat.ui.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Persisted "notification education shown" flag.
 *  Port of NotificationChannelManager.educationShown /
 *  markEducationShown (lib/data/services/notification_channel_manager.dart),
 *  which stores 'chaty_notif_education_shown' via shared_preferences. The
 *  same key and store file are used so the value lines up if a full
 *  NotificationChannelManager port lands later. */
object NotificationEducationStore {
    private const val PREFS_FILE = "FlutterSharedPreferences"
    private const val KEY = "chaty_notif_education_shown"

    fun isShown(context: Context): Boolean = try {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    } catch (_: Exception) {
        false
    }

    fun markShown(context: Context) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, true).apply()
        } catch (_: Exception) {
        }
    }
}

/** Port of lib/features/notifications/notification_permission_sheet.dart,
 *  including NotificationPermissionSheet.showIfNeeded: the Dart chats-home
 *  screen calls it once from initState's post-frame callback.
 *
 *  Host wiring (integration owner / chats-home slice): place this composable
 *  once inside ChatsHomeScreen's composition, e.g. as the last child of a
 *  top-level Box so the SnackbarHost can overlay the list:
 *
 *      NotificationPermissionSheetHost(Modifier.align(Alignment.BottomCenter))
 *
 *  It renders nothing until the sheet or the "disabled in settings" snackbar
 *  is needed. [onCompleted] fires after the enable flow, mirroring the Dart
 *  sheet's optional callback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPermissionSheetHost(
    modifier: Modifier = Modifier,
    onCompleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSheet by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }

    // showIfNeeded: skip when education was already shown; when the runtime
    // permission is already granted, mark shown silently and never display.
    LaunchedEffect(Unit) {
        if (NotificationEducationStore.isShown(context)) return@LaunchedEffect
        if (ChatyPermissions.isGranted(context, ChatyPermissions.notification)) {
            NotificationEducationStore.markShown(context)
            return@LaunchedEffect
        }
        showSheet = true
    }

    val requestPermission = rememberPermissionRequester { _, permanentlyDenied ->
        NotificationEducationStore.markShown(context)
        requesting = false
        showSheet = false
        onCompleted()
        if (permanentlyDenied) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Notifications are disabled in system settings.",
                    actionLabel = "Settings",
                )
                if (result == SnackbarResult.ActionPerformed) {
                    ChatyPermissions.openAppSettings(context)
                }
            }
        }
    }

    Box(modifier) {
        if (showSheet) {
            NotificationPermissionSheet(
                requesting = requesting,
                onNotNow = {
                    // Dart 'Not Now': mark shown, dismiss, no OS prompt.
                    NotificationEducationStore.markShown(context)
                    showSheet = false
                },
                onEnable = {
                    requesting = true
                    requestPermission(ChatyPermissions.notification)
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The sheet body itself — 1:1 with the Dart widget tree (ChatySpacing /
 *  ChatyRadius tokens inlined: xl=24, lg=20, sm=8, md=12, xxl=32 spacing,
 *  md=12 / xxl=24 / full radius). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationPermissionSheet(
    requesting: Boolean,
    onNotNow: () -> Unit,
    onEnable: () -> Unit,
) {
    val colors = LocalAppColors.current

    ModalBottomSheet(tonalElevation = 0.dp,
        onDismissRequest = onNotNow,
        containerColor = colors.surface,
        shape = com.example.chat.ui.kit.ChatyModalShape,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(
                        colors.foregroundSecondary.copy(alpha = 0.25f),
                        RoundedCornerShape(999.dp),
                    ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle, com.example.chat.ui.kit.ChatyModalShape)
                .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .background(colors.primary.copy(alpha = 0.12f), CircleShape)
                    .padding(20.dp),
            ) {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Stay Connected with Chaty",
                color = colors.foreground,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Receive realtime notifications for private direct messages, group chats, reactions, and incoming voice/video calls.",
                color = colors.foregroundSecondary,
                fontSize = 14.5.sp,
                lineHeight = (14.5f * 1.4f).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onNotNow,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                ) {
                    Text(
                        "Not Now",
                        color = colors.foregroundSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onEnable,
                    enabled = !requesting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    if (requesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            "Enable",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
