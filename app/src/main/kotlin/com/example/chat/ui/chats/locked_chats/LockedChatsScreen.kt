package com.example.chat.ui.chats.locked_chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Port of locked_chats_screen.dart (api branch anchor 3bee780): the locked
 *  & hidden conversations vault, secret search-code dialog, and the per-chat
 *  lock settings sheet (hide/unhide, unlock completely).
 *
 *  Entry gating: the Dart screen is only pushed after
 *  ProtectedResourceGate.authorizeGeneralAction succeeds (biometric/PIN).
 *  That gate lands with the app-lock slice; the integration owner must gate
 *  navigation to this screen the same way. Secret-phrase storage lands on
 *  LocalLockService (PBKDF2-HMAC-SHA256, 120k rounds, 16-byte salt, keys
 *  chaty.local_lock.v2.secret_phrase.hash/.salt); the callbacks below take
 *  the raw phrase and this screen applies Dart's normalization first via
 *  [SecretPhraseSupport]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedChatsScreen(
    lockedConversations: List<Conversation>,
    lockMethod: String,
    isConversationHidden: (String) -> Boolean,
    hasSecretPhrase: suspend () -> Boolean,
    setSecretPhrase: suspend (String) -> Unit,
    clearSecretPhrase: suspend () -> Unit,
    onSetEntryBySecretPhrase: (Boolean) -> Unit,
    onToggleHide: (conversationId: String, hide: Boolean) -> Unit,
    onUnlockCompletely: (String) -> Unit,
    onOpenChat: (Conversation) -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var phrasePresent by remember { mutableStateOf(false) }
    var showSecretDialog by remember { mutableStateOf(false) }
    var settingsTarget by remember { mutableStateOf<Conversation?>(null) }

    LaunchedEffect(Unit) {
        phrasePresent = try {
            hasSecretPhrase()
        } catch (_: Exception) {
            false
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.foreground)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Locked & Hidden Chats",
                        color = colors.foreground,
                        fontSize = (19 * theme.fontScale).sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${lockedConversations.size} protected conversations",
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { showSecretDialog = true }) {
                    Icon(
                        if (phrasePresent) Icons.Rounded.Key else Icons.Outlined.KeyOff,
                        contentDescription = "Secret Search Code Settings",
                        tint = if (phrasePresent) colors.primary else colors.foregroundSecondary,
                    )
                }
                IconButton(onClick = onOpenSecuritySettings) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = "Lock Settings",
                        tint = colors.foreground,
                    )
                }
            }
        },
    ) { padding ->
        if (lockedConversations.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = colors.foregroundSecondary,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "No Locked Chats",
                    color = colors.foreground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "To lock a chat, open chat options or Contact Info and toggle Chat Lock with PIN or biometric protection.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Row(
                        Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(16.dp))
                            .border(1.dp, colors.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Box(
                            Modifier.background(colors.primary.copy(alpha = 0.12f), CircleShape).padding(10.dp),
                        ) {
                            Icon(
                                Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "Hidden Chat Discovery",
                                color = colors.foreground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (phrasePresent) {
                                    "Type your secret code in the Chats search bar, or tap the Chaty header title to open this vault."
                                } else {
                                    "Tap the key icon above to set a secret phrase or emoji for hidden chat search discovery."
                                },
                                color = colors.foregroundSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
                items(lockedConversations, key = { it.id }) { conversation ->
                    val isHidden = isConversationHidden(conversation.id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(conversation) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Box(
                                modifier = Modifier.size(50.dp).background(
                                    Color(com.example.chat.ui.chats.contact_info.parseAvatarColorArgb(conversation.avatarColorHex, LocalThemeConfig.current.accentColor)),
                                    CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    conversation.avatarInitials ?: "CH",
                                    color = colors.onPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(18.dp)
                                    .background(
                                        if (isHidden) colors.primary else colors.surface,
                                        CircleShape,
                                    )
                                    .border(2.dp, colors.background, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = if (isHidden) Color.White else colors.primary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                conversation.title,
                                color = colors.foreground,
                                fontWeight = FontWeight.Bold,
                                fontSize = (16 * theme.fontScale).sp,
                            )
                            Text(
                                if (isHidden) "Hidden & Locked" else "Locked with $lockMethod",
                                color = if (isHidden) colors.primary else colors.foregroundSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isHidden) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        IconButton(onClick = { settingsTarget = conversation }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Chat lock settings",
                                tint = colors.foregroundSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    // Secret code dialog (Set / Change / Remove), exact Dart copy.
    if (showSecretDialog) {
        var secretInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSecretDialog = false },
            containerColor = colors.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(8.dp),
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (phrasePresent) "Change Secret Code" else "Set Secret Code",
                        color = colors.foreground,
                        fontSize = 18.sp,
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "Enter a secret word, phrase, or emoji. Typing this secret into the chat search bar will instantly reveal your locked chats.",
                        color = colors.foregroundSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    BasicTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.foreground, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.inputFill, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        decorationBox = { inner ->
                            if (secretInput.isEmpty()) {
                                Text("e.g. 🔒 secret or my-vault", color = colors.foregroundSecondary, fontSize = 14.sp)
                            }
                            inner()
                        },
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showSecretDialog = false }) {
                        Text("Cancel", color = colors.foregroundSecondary)
                    }
                    if (phrasePresent) {
                        TextButton(onClick = {
                            showSecretDialog = false
                            scope.launch {
                                clearSecretPhrase()
                                onSetEntryBySecretPhrase(false)
                                phrasePresent = try {
                                    hasSecretPhrase()
                                } catch (_: Exception) {
                                    false
                                }
                                snackbar.showSnackbar("Secret code removed.")
                            }
                        }) {
                            Text("Remove Code", color = colors.error)
                        }
                    }
                    Button(
                        onClick = {
                            val secret = secretInput.trim()
                            if (secret.isEmpty()) return@Button
                            showSecretDialog = false
                            scope.launch {
                                setSecretPhrase(SecretPhraseSupport.requireValidSecretPhrase(secret))
                                onSetEntryBySecretPhrase(true)
                                phrasePresent = try {
                                    hasSecretPhrase()
                                } catch (_: Exception) {
                                    false
                                }
                                snackbar.showSnackbar(
                                    if (phrasePresent) {
                                        "Secret code active. Type it into search to reveal locked chats."
                                    } else {
                                        "Secret code removed."
                                    },
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) {
                        Text("Save Code")
                    }
                }
            },
        )
    }

    // Per-chat lock settings sheet: hide/unhide + unlock completely.
    val target = settingsTarget
    if (target != null) {
        val isHidden = isConversationHidden(target.id)
        ModalBottomSheet(
            onDismissRequest = { settingsTarget = null },
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .background(colors.foregroundSecondary.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            settingsTarget = null
                            onToggleHide(target.id, !isHidden)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .background(
                                (if (isHidden) colors.primary else colors.foregroundSecondary).copy(alpha = 0.15f),
                                CircleShape,
                            )
                            .padding(10.dp),
                    ) {
                        Icon(
                            if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = if (isHidden) colors.primary else colors.foreground,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isHidden) "Unhide Chat" else "Hide Chat from Chat List",
                            color = colors.foreground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (isHidden) "Show in standard chat list with locked badge"
                            else "Keep hidden and only discoverable via secret code or title tap",
                            color = colors.foregroundSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            settingsTarget = null
                            onUnlockCompletely(target.id)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.background(colors.error.copy(alpha = 0.15f), CircleShape).padding(10.dp)) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = colors.error)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Unlock & Remove Protection",
                            color = colors.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Removes PIN / biometric requirement from this conversation",
                            color = colors.foregroundSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
