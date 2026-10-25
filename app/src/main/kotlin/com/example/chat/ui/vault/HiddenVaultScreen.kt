package com.example.chat.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.LockOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Exact port of lib/features/vault/hidden_vault_screen.dart.
 *
 *  Dedicated Biometric and PBKDF2 PIN Hidden Chat Vault Screen.
 *  Features shake-on-error physics, decoy mode support, and an isolated
 *  encrypted thread list.
 *
 *  Seams:
 *  - ChatyDataStore.conversations -> [conversationsProvider].
 *  - Navigation into ChatDetailScreen -> [onOpenConversation]; the
 *    integration layer routes to its own ChatDetail host.
 *  - SpringInteractiveScale / BentoCard / AppAvatar are design-system
 *    widgets owned by the shared theme layer; simple Compose equivalents are
 *    used here and can be swapped when that layer lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenVaultScreen(
    controller: HiddenVaultController,
    conversationsProvider: () -> List<Conversation>,
    onOpenConversation: (conversationId: String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // AnimatedBuilder port: recompose whenever the controller notifies.
    var listenTick by remember { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val listener: () -> Unit = { listenTick++ }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") listenTick

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun triggerBiometricAuth() {
        if (isAuthenticating) return
        isAuthenticating = true
        scope.launch {
            val authed = controller.unlockVault()
            isAuthenticating = false
            if (authed) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    fun verifyPinAttempt() {
        val pin = enteredPin
        scope.launch {
            val verified = controller.verifyPin(pin)
            if (verified) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                enteredPin = ""
                errorMessage = null
            } else if (pin.length >= 4) {
                // Shake animation and haptic rejection
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                shake.snapTo(0f)
                shake.animateTo(1f, animationSpec = tween(durationMillis = 400))
                errorMessage = "Incorrect PIN. Try again or use biometrics."
                enteredPin = ""
            }
        }
    }

    fun onDigitPressed(digit: String) {
        if (enteredPin.length >= 6) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        enteredPin += digit
        errorMessage = null
        if (enteredPin.length == 4 || enteredPin.length == 6) {
            verifyPinAttempt()
        }
    }

    fun onBackspacePressed() {
        if (enteredPin.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        enteredPin = enteredPin.dropLast(1)
        errorMessage = null
    }

    // Prompt biometric on screen load if vault is locked
    LaunchedEffect(Unit) {
        if (!controller.isUnlocked) {
            triggerBiometricAuth()
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    if (controller.isUnlocked) {
        UnlockedVaultView(
            controller = controller,
            conversationsProvider = conversationsProvider,
            onOpenConversation = onOpenConversation,
            onBack = onBack,
            isDark = isDark,
            onLock = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                controller.lockVault()
            },
        )
    } else {
        LockScreenView(
            isDark = isDark,
            onBack = onBack,
            enteredPinLength = enteredPin.length,
            errorMessage = errorMessage,
            shakeOffsetPx = { sin(shake.value * Math.PI * 6) * 12 * 3 }, // 12 logical px shake
            onDigit = ::onDigitPressed,
            onBackspace = ::onBackspacePressed,
            onBiometric = ::triggerBiometricAuth,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockScreenView(
    isDark: Boolean,
    onBack: (() -> Unit)?,
    enteredPinLength: Int,
    errorMessage: String?,
    shakeOffsetPx: () -> Double,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Scaffold(
        containerColor = if (isDark) Color(0xFF0D1117) else Color(0xFFF0F4F8),
        topBar = {
            TopAppBar(
                title = { Text("Hidden Vault", fontWeight = FontWeight.W700) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            // Vault icon with ambient glow
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = CircleShape,
                        ambientColor = primary.copy(alpha = 0.25f),
                        spotColor = primary.copy(alpha = 0.25f),
                    )
                    .background(primary.copy(alpha = 0.14f), CircleShape)
                    .border(1.5.dp, primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.LockOutline,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = primary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Unlock Vault",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter your secure PIN or use biometrics",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.54f),
            )
            Spacer(Modifier.height(24.dp))

            // PIN Indicator dots with shake animation
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.offset { IntOffset(shakeOffsetPx().roundToInt(), 0) },
            ) {
                repeat(4) { index ->
                    val isFilled = index < enteredPinLength
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(16.dp)
                            .background(
                                if (isFilled) primary
                                else if (isDark) Color.White.copy(alpha = 0.24f)
                                else Color.Black.copy(alpha = 0.12f),
                                CircleShape,
                            ),
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    errorMessage,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(1f))

            NumericPad(
                isDark = isDark,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onBiometric = onBiometric,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NumericPad(
    isDark: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "back"),
    )

    Column {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { cell ->
                    when (cell) {
                        "bio" -> Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = 0.12f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onBiometric,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Fingerprint,
                                contentDescription = "Unlock with biometrics",
                                modifier = Modifier.size(32.dp),
                                tint = primary,
                            )
                        }
                        "back" -> Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.06f)
                                    else Color.Black.copy(alpha = 0.04f),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onBackspace,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = "Backspace",
                                modifier = Modifier.size(22.dp),
                                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.54f),
                            )
                        }
                        else -> Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDark) Color(0xFF1C2128) else Color.White,
                                )
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f)
                                    else Color.Black.copy(alpha = 0.06f),
                                    CircleShape,
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onDigit(cell) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cell,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.W600,
                                color = if (isDark) Color.White else Color.Black.copy(alpha = 0.87f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockedVaultView(
    controller: HiddenVaultController,
    conversationsProvider: () -> List<Conversation>,
    onOpenConversation: (String) -> Unit,
    onBack: (() -> Unit)?,
    isDark: Boolean,
    onLock: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lockedIds = controller.lockedConversationIds
    val lockedConversations = conversationsProvider().filter { lockedIds.contains(it.id) }

    Scaffold(
        containerColor = if (isDark) Color(0xFF0D1117) else Color(0xFFF0F4F8),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (controller.isDecoyMode) "Decoy Vault" else "Hidden Vault",
                            fontWeight = FontWeight.W700,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Rounded.LockOutline, contentDescription = "Lock Vault")
                    }
                },
            )
        },
    ) { padding ->
        if (lockedConversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.26f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (controller.isDecoyMode) "No active chats in decoy mode" else "No Hidden Conversations",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "To hide a conversation into this encrypted vault, select it from the inbox and choose \"Lock to Vault\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.54f),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(lockedConversations, key = { it.id }) { conversation ->
                    Card(
                        onClick = { onOpenConversation(conversation.id) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // AppAvatar equivalent
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    conversation.avatarInitials
                                        ?: conversation.title.firstOrNull()?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    conversation.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.W700,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    conversation.lastMessageText.ifEmpty { "Encrypted conversation" },
                                    fontSize = 13.sp,
                                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.54f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { controller.toggleConversationLock(conversation.id) }
                            }) {
                                Icon(
                                    Icons.Rounded.LockOpen,
                                    contentDescription = "Unlock from Vault",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
