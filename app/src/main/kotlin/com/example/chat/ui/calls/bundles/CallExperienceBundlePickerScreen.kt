package com.example.chat.ui.calls.bundles

import android.widget.Toast
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
 * Interactive preview and atomic selection screen for the Six Call Experience Bundles.
 * Accessible from: Settings -> Calls -> Call Experience Bundles
 */
@Composable
fun CallExperienceBundlePickerScreen(
    currentBundleId: String,
    onApplyBundle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(currentBundleId) }
    var previewBundleId by remember { mutableStateOf<CallBundleId?>(null) }

    if (previewBundleId != null) {
        CallBundleLivePreviewScreen(
            bundleId = previewBundleId!!,
            onClose = { previewBundleId = null },
            onApply = {
                selectedId = previewBundleId!!.id
                onApplyBundle(previewBundleId!!.id)
                Toast.makeText(context, "${previewBundleId!!.displayName} applied", Toast.LENGTH_SHORT).show()
                previewBundleId = null
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
            items(CallBundleId.all) { bundle ->
                val isSelected = bundle.id == selectedId
                val tokens = remember(bundle) { CallBundleRegistry.getTokens(bundle) }

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
                            selectedId = bundle.id
                            onApplyBundle(bundle.id)
                            Toast.makeText(context, "${bundle.displayName} applied", Toast.LENGTH_SHORT).show()
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
                                    selectedId = bundle.id
                                    onApplyBundle(bundle.id)
                                    Toast.makeText(context, "${bundle.displayName} applied", Toast.LENGTH_SHORT).show()
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
 * Interactive accurate Live Preview Screen of the selected bundle across all supported states.
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
            state = CallSessionState.active,
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

    var invitedIds by remember { mutableStateOf(setOf<String>()) }
    var isExpandedCard by remember { mutableStateOf(false) }

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
                        Tab(selected = previewTab == 2, onClick = { previewTab = 2 }, text = { Text("Group Video") })
                        Tab(selected = previewTab == 3, onClick = { previewTab = 3 }, text = { Text("Invite Sheet") })
                        Tab(selected = previewTab == 4, onClick = { previewTab = 4 }, text = { Text("Call Cards (Ref 3)") })
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
                    session = mockSession,
                    onAccept = { previewTab = 1 },
                    onDecline = onClose,
                )
                1 -> BundleOngoingCallView(
                    bundleId = bundleId,
                    session = mockSession,
                    callDurationSeconds = 142,
                    isMuted = false,
                    isSpeaker = true,
                    isCameraOn = true,
                    onToggleMute = {},
                    onToggleSpeaker = {},
                    onToggleCamera = {},
                    onSwitchCamera = {},
                    onEndCall = onClose,
                    onInviteParticipant = { previewTab = 3 },
                    onMinimize = {},
                )
                2 -> BundleGroupMeetingView(
                    bundleId = bundleId,
                    meetingTitle = "Weekly Sync",
                    durationSeconds = 240,
                    participants = mockParticipants,
                    isSpeakerFocused = true,
                    onToggleSpeakerFocus = {},
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
                4 -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Reference 3 Card Presentations",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tap the card below to toggle compact vs expanded presentation",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(28.dp))
                        BundleCompactCallCard(
                            bundleId = bundleId,
                            session = mockSession,
                            isExpanded = isExpandedCard,
                            onToggleExpand = { isExpandedCard = !isExpandedCard },
                            onAccept = { previewTab = 1 },
                            onDecline = onClose,
                        )
                    }
                }
            }
        }
    }
}

