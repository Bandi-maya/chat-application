package com.example.chat.ui.chats.contact_info

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.ContactPrivacyOverride
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Port of contact_privacy_screen.dart (api branch anchor 3bee780):
 *  per-contact privacy override toggles with optimistic save + rollback on
 *  failure, sourced from ContactRelationshipService.privacyFor/savePrivacy
 *  (arriving here as lambdas). */
@Composable
fun ContactPrivacyScreen(
    contact: UserProfile,
    privacyFor: suspend (String) -> ContactPrivacyOverride,
    savePrivacy: suspend (ContactPrivacyOverride) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf<ContactPrivacyOverride?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            value = privacyFor(contact.id)
            loading = false
        } catch (e: Exception) {
            loading = false
            error = e.toString()
        }
    }

    // Dart _save: optimistic apply, rollback + error on failure.
    fun save(next: ContactPrivacyOverride) {
        if (saving) return
        val previous = value
        value = next
        saving = true
        error = null
        scope.launch {
            try {
                savePrivacy(next)
            } catch (e: Exception) {
                value = previous
                error = (e.message ?: e.toString()).removePrefix("Exception: ")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = colors.foreground)
                }
                Text(
                    "Privacy: ${contact.displayName}",
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { padding ->
        val current = value
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.2.dp, color = colors.primary)
            }
            current == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Unable to load contact privacy.", color = colors.error)
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "These controls change what ${contact.displayName} can observe from you. They are cryptographically and server-enforced.",
                        color = colors.foreground.copy(alpha = 0.85f),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = colors.error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))

                SectionTitle("Message Receipts", colors)
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                    PrivacyTile(Icons.Rounded.Done, "Hide delivery receipt (second tick)",
                        "Messages received by you will not expose the delivered state to this contact.",
                        current.hideDeliveryReceipts, saving) { save(current.copy(hideDeliveryReceipts = it)) }
                    PrivacyTile(Icons.Rounded.DoneAll, "Hide read receipt (blue ticks)",
                        "Reading this contact’s messages will not expose read receipts to them.",
                        current.hideReadReceipts, saving) { save(current.copy(hideReadReceipts = it)) }
                }
                Spacer(Modifier.height(12.dp))

                SectionTitle("Live Indicators", colors)
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                    PrivacyTile(Icons.Rounded.KeyboardAlt, "Hide typing state",
                        "Do not publish your typing state to this contact.",
                        current.hideTyping, saving) { save(current.copy(hideTyping = it)) }
                    PrivacyTile(Icons.Rounded.MicNone, "Hide voice recording state",
                        "Do not publish \"recording…\" while recording audio messages.",
                        current.hideRecording, saving) { save(current.copy(hideRecording = it)) }
                }
                Spacer(Modifier.height(12.dp))

                SectionTitle("Presence & Timestamp", colors)
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                    PrivacyTile(Icons.Outlined.Circle, "Hide online status",
                        "This contact will receive an offline status projection even while active.",
                        current.hideOnline, saving) { save(current.copy(hideOnline = it)) }
                    PrivacyTile(Icons.Rounded.Schedule, "Hide last seen",
                        "This contact will not receive your last-seen timestamp.",
                        current.hideLastSeen, saving) { save(current.copy(hideLastSeen = it)) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PrivacyTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Boolean,
    saving: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = colors.foregroundSecondary.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = if (saving) null else onChanged,
            colors = com.example.chat.ui.kit.chatySwitchColors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary),
        )
    }
}
