package com.example.chat.ui.calls.bundles

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.data.services.CallSessionState
import com.example.chat.ui.settings.CallContactOption

/**
 * Interactive preview and atomic selection screen for all Call Experience Bundles.
 * Accessible from: Settings -> Calls -> Call Experience Bundles
 */
@Composable
fun CallExperienceBundlePickerScreen(
    currentBundleId: String,
    onApplyBundle: (String) -> Unit,
    onBack: () -> Unit,
    onApplyScopedBundle: (suspend (String, com.example.chat.ui.calls.BundleApplyTarget) -> Unit)? = null,
) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(currentBundleId) }
    var previewBundleId by remember { mutableStateOf<CallBundleId?>(null) }
    var pendingBundle by remember { mutableStateOf<CallBundleId?>(null) }
    var applying by remember { mutableStateOf(false) }
    var applyError by remember { mutableStateOf<String?>(null) }
    val applyScope = rememberCoroutineScope()
    pendingBundle?.let { pending ->
        AlertDialog(onDismissRequest = { if (!applying) pendingBundle = null },
            title = { Text("Apply ${pending.displayName}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose which layouts to change. Your other selection stays unchanged.")
                com.example.chat.ui.calls.BundleApplyTarget.entries.forEach { target ->
                    TextButton(enabled = !applying, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                        applying = true; applyError = null
                        applyScope.launch {
                            try {
                                val scoped = onApplyScopedBundle
                                if (scoped != null) scoped(pending.id, target)
                                else if (target == com.example.chat.ui.calls.BundleApplyTarget.CALLS) onApplyBundle(pending.id)
                                else error("Message bundle application is unavailable on this screen.")
                                if (target != com.example.chat.ui.calls.BundleApplyTarget.MESSAGES) selectedId = pending.id
                                pendingBundle = null; previewBundleId = null
                            } catch (error: Exception) { applyError = error.message ?: "Unable to apply bundle" }
                            finally { applying = false }
                        }
                    }) { Text(when (target) {
                        com.example.chat.ui.calls.BundleApplyTarget.CALLS -> "Calls only"
                        com.example.chat.ui.calls.BundleApplyTarget.MESSAGES -> "Messages only"
                        com.example.chat.ui.calls.BundleApplyTarget.BOTH -> "Both"
                    }) }
                }
                applyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { TextButton(enabled = !applying, onClick = { pendingBundle = null }) { Text("Cancel") } })
    }

    if (previewBundleId != null) {
        CallBundleLivePreviewScreen(
            bundleId = previewBundleId!!,
            onClose = { previewBundleId = null },
            onApply = {
                pendingBundle = previewBundleId
            },
        )
        return
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Call Experience Bundles",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "One-tap global calling redesign across all call states",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(CallBundleId.all, key = { it.id }) { bundle ->
                val isSelected = bundle.id == selectedId
                val tokens = com.example.chat.ui.calls.themedCallBundle(bundle)

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.border(2.dp, tokens.primaryAccent, RoundedCornerShape(20.dp))
                            else Modifier
                        )
                        .clickable {
                            pendingBundle = bundle
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                    ) {
                        // Title + Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(tokens.primaryAccent),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = bundle.displayName,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (bundle.badge != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = tokens.primaryAccent.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = bundle.badge,
                                        color = tokens.primaryAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = bundle.subtitle,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )

                        Spacer(Modifier.height(16.dp))

                        // Actions: Preview & Immediate Apply
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { previewBundleId = bundle },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Rounded.Visibility, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Interactive Preview", fontSize = 13.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    pendingBundle = bundle
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) tokens.primaryAccent else MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Applied", fontSize = 13.sp)
                                } else {
                                    Text("Apply", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview of production 1:1 rendering plus explicitly labelled group concept fixtures.
 */
@Composable
fun CallBundleLivePreviewScreen(
    bundleId: CallBundleId,
    onClose: () -> Unit,
    onApply: () -> Unit,
) {
    var previewTab by remember { mutableIntStateOf(0) }
    // 0: Incoming, 1: Active 1:1, 2: Group Meeting, 3: Invite Screen, 4: Compact Cards

    val mockSession = remember {
        ChatyCallSession(
            callId = "preview_call_101",
            remoteUserId = "user_42",
            remoteDisplayName = "Sophia Martinez",
            remoteAvatarInitials = "SM",
            remoteAvatarColorHex = "#8B5CF6",
            isVideo = true,
            isOutgoing = false,
            state = CallSessionState.connected,
            startedAt = java.time.Instant.EPOCH,
        )
    }

    val mockParticipants = remember {
        listOf(
            GroupMeetingParticipant("1", "Sophia Martinez", "smartinez", isSpeaking = true),
            GroupMeetingParticipant("2", "Alexander Wright", "awright", isSpeaking = false),
            GroupMeetingParticipant("3", "Emily Chen", "echen", isSpeaking = false),
            GroupMeetingParticipant("4", "David Kim", "dkim", isSpeaking = false),
        )
    }

    val mockContacts = remember {
        listOf(
            CallContactOption("c1", "Miracle Donin"),
            CallContactOption("c2", "Skylar Workman"),
            CallContactOption("c3", "Ann Calzoni"),
            CallContactOption("c4", "James Workman"),
            CallContactOption("c5", "Mira Bergson"),
        )
    }

    var previewMuted by remember(bundleId) { mutableStateOf(false) }
    var previewCameraOff by remember(bundleId) { mutableStateOf(false) }
    var previewFrontCamera by remember(bundleId) { mutableStateOf(true) }
    var previewSharing by remember(bundleId) { mutableStateOf(false) }
    var previewRoute by remember(bundleId) { mutableStateOf(com.example.chat.data.services.AudioRouteType.speaker) }
    var invitedIds by remember { mutableStateOf(setOf<String>()) }
    var previewSpeakerFocused by remember(bundleId) { mutableStateOf(true) }

    Scaffold(
        topBar = {
            Surface(color = Color(0xFF1E1E24)) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                            }
                            Text(
                                text = "Preview: ${bundleId.displayName}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                        Button(
                            onClick = onApply,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            Text("Apply Bundle")
                        }
                    }

                    // Mode Switcher Tabs
                    ScrollableTabRow(
                        selectedTabIndex = previewTab,
                        edgePadding = 12.dp,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                    ) {
                        Tab(selected = previewTab == 0, onClick = { previewTab = 0 }, text = { Text("Incoming") })
                        Tab(selected = previewTab == 1, onClick = { previewTab = 1 }, text = { Text("Active 1:1") })
                        Tab(selected = previewTab == 2, onClick = { previewTab = 2 }, text = { Text("Group concept") })
                        Tab(selected = previewTab == 3, onClick = { previewTab = 3 }, text = { Text("Invite concept") })
                        Tab(selected = previewTab == 4, onClick = { previewTab = 4 }, text = { Text("Production PiP") })
                        Tab(selected = previewTab == 5, onClick = { previewTab = 5 }, text = { Text("Outgoing") })
                        Tab(selected = previewTab == 6, onClick = { previewTab = 6 }, text = { Text("Connecting") })
                        Tab(selected = previewTab == 7, onClick = { previewTab = 7 }, text = { Text("Reconnecting") })
                        Tab(selected = previewTab == 8, onClick = { previewTab = 8 }, text = { Text("Active video") })
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (previewTab) {
                0 -> BundleIncomingCallView(
                    bundleId = bundleId,
                    session = mockSession.copy(state = CallSessionState.incoming),
                    onAccept = { previewTab = 1 },
                    onDecline = onClose,
                )
                1, 5, 6, 7, 8 -> {
                    val state = when (previewTab) {
                        5 -> CallSessionState.ringing
                        6 -> CallSessionState.connecting
                        7 -> CallSessionState.reconnecting
                        else -> CallSessionState.connected
                    }
                    val presentation = resolveCallBundlePresentation(bundleId.id, state)
                    val previewSession = mockSession.copy(
                        state = state, isVideo = previewTab == 8, isOutgoing = previewTab == 5,
                        isMuted = previewMuted, isCameraOff = previewCameraOff,
                        isFrontCamera = previewFrontCamera, isSharingScreen = previewSharing,
                        audioRoute = previewRoute, isDemo = true,
                    )
                    com.example.chat.ui.calls.OngoingCallScreen(
                        session = previewSession,
                        callDurationSeconds = if (presentation.showConnectedDuration) 142 else 0,
                        hasLocalMedia = false, videoSurfaces = null, setupError = null,
                        template = presentation.template, bundleId = presentation.bundleId,
                        pictureInPictureEnabled = false, autoHideControls = false, observeNativePip = false,
                        onBack = onClose, onSessionGone = onClose, onEndCall = onClose,
                        onToggleMute = { previewMuted = !previewMuted },
                        onToggleCamera = { previewCameraOff = !previewCameraOff },
                        onSwitchCamera = { previewFrontCamera = !previewFrontCamera },
                        onToggleScreenShare = { previewSharing = !previewSharing },
                        onToggleAudioRoute = { previewRoute = it }, onEnterNativePip = onClose,
                    )
                }
                2 -> BundleGroupMeetingView(
                    bundleId = bundleId,
                    meetingTitle = "Weekly Sync",
                    durationSeconds = 240,
                    participants = mockParticipants,
                    isSpeakerFocused = previewSpeakerFocused,
                    onToggleSpeakerFocus = { previewSpeakerFocused = !previewSpeakerFocused },
                    onInviteClicked = { previewTab = 3 },
                    onLeaveMeeting = onClose,
                )
                3 -> BundleParticipantInviteSheet(
                    bundleId = bundleId,
                    contacts = mockContacts,
                    invitedContactIds = invitedIds,
                    onToggleInvite = { id ->
                        invitedIds = if (invitedIds.contains(id)) invitedIds - id else invitedIds + id
                    },
                    onClose = { previewTab = 2 },
                )
                4 -> com.example.chat.ui.calls.InAppCallPip(
                    bundleId = bundleId,
                    session = mockSession.copy(state = CallSessionState.connected),
                    videoSurfaces = null,
                    durationSeconds = 142,
                    onTap = { previewTab = 8 },
                    onCollapseToIsland = { previewTab = 1 },
                    onEndCall = onClose,
                )
            }
            if (previewTab == 2 || previewTab == 3) {
                Text(
                    text = "Concept fixture only: group calling is not available in the current runtime.",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xFF1E1E24)).padding(8.dp),
                )
            }
        }
    }
}
