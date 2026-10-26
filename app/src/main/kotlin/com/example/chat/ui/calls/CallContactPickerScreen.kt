package com.example.chat.ui.calls

import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PeopleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay

enum class CallContactPickerMode { contacts, dialpad }

private data class DialKey(val digit: String, val subtitle: String)

private val dialKeys = listOf(
    DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF"),
    DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
    DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
    DialKey("*", ""), DialKey("0", "+"), DialKey("#", ""),
)

/**
 * Port of features/calls/call_contact_picker_screen.dart: full-screen contact
 * picker shared by the Calls FABs. Dialpad mode filters registered Chaty
 * contacts live as the user enters a name or number; a call starts only for
 * one unambiguous registered-contact match.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallContactPickerScreen(
    contacts: List<UserProfile>,
    currentUserId: String,
    initialMode: CallContactPickerMode = CallContactPickerMode.contacts,
    onBack: () -> Unit,
    onStartCall: (contact: UserProfile, isVideo: Boolean) -> Unit,
    showToast: (message: String, isError: Boolean) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // Respect silent/vibrate and the device's dial-pad tone preference.
    val tones = remember(context) { runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() }
    DisposableEffect(tones) { onDispose { tones?.release() } }

    fun playDialTone(digit: String) {
        val audio = context.getSystemService(AudioManager::class.java)
        val enabled = Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) != 0
        if (!enabled || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val tone = when (digit) {
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            "+" -> ToneGenerator.TONE_DTMF_0
            else -> digit.toIntOrNull()?.let { ToneGenerator.TONE_DTMF_0 + it }
        } ?: return
        runCatching { tones?.startTone(tone, 100) }
    }

    var query by remember { mutableStateOf("") }
    var isDialpad by remember { mutableStateOf(initialMode == CallContactPickerMode.dialpad) }
    var isKeypadCollapsed by remember { mutableStateOf(false) }
    var startingContactId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Dart _handleScroll: scrolling down in dialpad mode collapses the keypad.
    LaunchedEffect(isDialpad) {
        if (!isDialpad) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemScrollOffset to listState.firstVisibleItemIndex }
            .collect { (offset, index) ->
                if ((index > 0 || offset > 24) && !isKeypadCollapsed) {
                    isKeypadCollapsed = true
                }
            }
    }

    val visibleContacts by remember(query, contacts, currentUserId) {
        derivedStateOf {
            contacts.filter { contact ->
                contact.id != currentUserId &&
                    matchesDialerContact(query, contact.displayName, contact.phone)
            }
        }
    }

    fun initials(name: String): String {
        val normalized = name.trim()
        return if (normalized.isEmpty()) "?" else normalized.substring(0, 1).uppercase()
    }

    fun startCall(contact: UserProfile, isVideo: Boolean) {
        if (startingContactId != null) return
        startingContactId = contact.id
        onStartCall(contact, isVideo)
        // The owner completes the async initiate; the spinner clears when the
        // screen recomposes with a fresh startingContactId (null) on return.
        startingContactId = null
    }

    fun appendDigit(digit: String) {
        if (startingContactId != null) return
        if (query.length >= 25) return
        query += digit
        playDialTone(digit)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun removeDigit() {
        if (query.isEmpty()) return
        query = query.substring(0, query.length - 1)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    var backspaceRepeating by remember { mutableStateOf(false) }
    LaunchedEffect(backspaceRepeating) {
        if (!backspaceRepeating) return@LaunchedEffect
        while (backspaceRepeating && query.isNotEmpty()) {
            delay(100)
            if (query.isEmpty()) break
            removeDigit()
        }
        backspaceRepeating = false
    }

    fun triggerCall() {
        val digits = query.replace(Regex("[^0-9]"), "")
        val matches = contacts.filter { contact ->
            if (contact.id == currentUserId) return@filter false
            val phoneDigits = contact.phone.replace(Regex("[^0-9]"), "")
            digits.length >= 7 && (phoneDigits == digits || phoneDigits.endsWith(digits))
        }
        if (matches.size != 1) {
            showToast(
                if (matches.isEmpty()) "Enter a registered Chaty number to call."
                else "More than one contact matches. Choose the right contact below.",
                matches.isEmpty(),
            )
            return
        }
        startCall(matches.single(), isVideo = false)
    }

    Scaffold(
        containerColor = Color(theme.backgroundColor),
        floatingActionButton = {
            if (isDialpad && isKeypadCollapsed) {
                FloatingActionButton(
                    onClick = { isKeypadCollapsed = false },
                    containerColor = Color(0xFF48D597),
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Rounded.Dialpad, contentDescription = "Show dialpad", modifier = Modifier.size(28.dp))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // App bar row (Dart AppBar: back, title, mode toggle).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = Color(theme.primaryTextColor))
                }
                Text(
                    if (isDialpad) "Add call" else "Contacts",
                    color = Color(theme.primaryTextColor),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { isDialpad = !isDialpad }) {
                    Icon(
                        if (isDialpad) Icons.Rounded.Contacts else Icons.Rounded.Dialpad,
                        contentDescription = if (isDialpad) "Show contacts" else "Open dialpad",
                        tint = Color(theme.accentColor),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            // Search / number field.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 6.dp),
            ) {
                TextField(
                    value = query,
                    onValueChange = { if (!isDialpad) query = it },
                    readOnly = isDialpad,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isDialpad) KeyboardType.Text else KeyboardType.Text,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(theme.primaryTextColor),
                        fontSize = if (isDialpad) 32.sp else 16.sp,
                        letterSpacing = if (isDialpad) 1.4.sp else 0.sp,
                        fontWeight = if (isDialpad) FontWeight.Normal else FontWeight.Medium,
                        textAlign = if (isDialpad) TextAlign.Center else TextAlign.Start,
                    ),
                    placeholder = {
                        Text(
                            if (isDialpad) "Enter number" else "Search contacts",
                            color = Color(theme.secondaryTextColor),
                        )
                    },
                    leadingIcon = if (!isDialpad) {
                        { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(theme.secondaryTextColor)) }
                    } else null,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isDialpad) Color.Transparent else Color(theme.cardColor),
                        unfocusedContainerColor = if (isDialpad) Color.Transparent else Color(theme.cardColor),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(theme.accentColor),
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                )
                if (isDialpad && query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear number", tint = Color(theme.secondaryTextColor))
                    }
                }
            }

            if (isDialpad && query.isNotEmpty()) {
                Text(
                    when {
                        visibleContacts.isEmpty() -> "No registered contact matches this number"
                        visibleContacts.size == 1 -> "1 matching Chaty contact"
                        else -> "${visibleContacts.size} matching Chaty contacts"
                    },
                    color = Color(theme.secondaryTextColor),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (!isDialpad) {
                Text(
                    if (visibleContacts.size == 1) "1 contact" else "${visibleContacts.size} contacts",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 8.dp),
                )
            }

            Box(Modifier.weight(1f)) {
                if (visibleContacts.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                    ) {
                        Spacer(Modifier.height(60.dp))
                        Icon(
                            if (query.isEmpty()) Icons.Rounded.PeopleOutline else Icons.Rounded.PersonSearch,
                            contentDescription = null,
                            tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                            modifier = Modifier.size(42.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (query.isEmpty()) "No contacts available" else "No matching contacts",
                            color = Color(theme.primaryTextColor),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (query.isEmpty()) "Your registered Chaty contacts will appear here."
                            else "Calls are available for registered Chaty contacts only.",
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 16.dp),
                    ) {
                        items(visibleContacts, key = { it.id }) { contact ->
                            val name = contact.displayName.trim()
                            val phone = contact.phone.trim()
                            val about = contact.about.trim()
                            val subtitle = when {
                                phone.isNotEmpty() -> phone
                                about.isNotEmpty() -> about
                                else -> "Chaty contact"
                            }
                            val isThisCallStarting = startingContactId == contact.id
                            val isStartingCall = startingContactId != null

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isThisCallStarting && !isStartingCall) {
                                        startCall(contact, isVideo = false)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                CallAvatar(initials = initials(name), colorHex = contact.avatarColorHex, size = 46.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name.ifEmpty { "Chaty contact" },
                                        color = Color(theme.primaryTextColor),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        subtitle,
                                        color = Color(theme.secondaryTextColor),
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isThisCallStarting) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.2.dp,
                                        color = Color(theme.accentColor),
                                        modifier = Modifier.size(40.dp).padding(10.dp),
                                    )
                                } else {
                                    Row {
                                        IconButton(
                                            onClick = { startCall(contact, isVideo = false) },
                                            enabled = !isStartingCall,
                                        ) {
                                            Icon(
                                                Icons.Rounded.Call,
                                                contentDescription = "Voice call",
                                                tint = Color(theme.accentColor),
                                                modifier = Modifier.size(21.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { startCall(contact, isVideo = true) },
                                            enabled = !isStartingCall,
                                        ) {
                                            Icon(
                                                Icons.Rounded.Videocam,
                                                contentDescription = "Video call",
                                                tint = Color(theme.accentColor),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = Color(theme.dividerColor),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 72.dp),
                            )
                        }
                    }
                }
            }

            if (isDialpad && !isKeypadCollapsed) {
                // Dialpad sheet (Dart _buildDialpad).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(theme.surfaceColor),
                            com.example.chat.ui.kit.ChatyModalShape,
                        )
                        .padding(start = 28.dp, top = 16.dp, end = 28.dp, bottom = 16.dp),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth().height(302.dp),
                        userScrollEnabled = false,
                    ) {
                        items(dialKeys) { key ->
                            // The grid gives each item its column width. Keep that outer
                            // cell separate so the actual key remains a 68dp circle.
                            Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .width(68.dp)
                                    .aspectRatio(1f)
                                        .testTag("dial-key-${key.digit}")
                                        .semantics(mergeDescendants = true) { contentDescription = "Dial ${key.digit}" }
                                        .clip(CircleShape)
                                        .background(
                                            Color(argbWithAlpha(theme.cardColor, 0.5)),
                                            CircleShape,
                                        )
                                        .combinedClickable(
                                            onClick = { appendDigit(key.digit) },
                                            onLongClick = if (key.digit == "0") {
                                                { appendDigit("+") }
                                            } else null,
                                        ),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            key.digit,
                                            color = Color(theme.primaryTextColor),
                                            fontSize = 27.sp,
                                            fontWeight = FontWeight.Normal,
                                            letterSpacing = (-0.2).sp,
                                        )
                                        if (key.digit == "1") {
                                            Icon(
                                                Icons.Rounded.Voicemail,
                                                contentDescription = null,
                                                tint = Color(theme.secondaryTextColor),
                                                modifier = Modifier.size(13.dp),
                                            )
                                        } else if (key.subtitle.isNotEmpty()) {
                                            Text(
                                                key.subtitle,
                                                color = Color(theme.secondaryTextColor),
                                                fontSize = 9.5.sp,
                                                letterSpacing = 1.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isKeypadCollapsed = true }) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Hide dialpad",
                                tint = Color(theme.secondaryTextColor),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("dial-call")
                                .semantics(mergeDescendants = true) { contentDescription = "Call number" }
                                .clip(CircleShape)
                                .background(Color(0xFF48D597), CircleShape)
                                .clickable { triggerCall() },
                        ) {
                            Icon(
                                Icons.Rounded.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { if (query.isNotEmpty()) removeDigit() },
                                        onLongPress = { backspaceRepeating = true },
                                        onPress = {
                                            awaitRelease()
                                            backspaceRepeating = false
                                        },
                                    )
                                },
                        ) {
                            Icon(
                                Icons.Rounded.Backspace,
                                contentDescription = "Delete digit",
                                tint = if (query.isEmpty()) {
                                    Color(argbWithAlpha(theme.secondaryTextColor, 0.5))
                                } else {
                                    Color(theme.secondaryTextColor)
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
