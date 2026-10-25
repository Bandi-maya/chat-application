package com.example.chat.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.DeliveryState
import com.example.chat.ui.theme.LocalThemeConfig
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** First-slice port of chat_detail_screen.dart's message view + composer:
 *  bubbles aligned by sender, per-message time, sending/delivered state,
 *  text composer wired to the REST send. Bubble style presets, attachments,
 *  replies, reactions, ticks presets and media land in their own slices. */
@Composable
fun ChatDetailScreen(
    conversation: Conversation,
    messages: List<ChatMessage>,
    currentUserId: String,
    onBack: () -> Unit,
    onSend: suspend (String) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    var composerText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = Color(theme.primaryTextColor),
                    )
                }
                Box(
                    modifier = Modifier.size(38.dp).background(Color(theme.accentColor), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        conversation.avatarInitials ?: "C",
                        color = Color(theme.onAccentColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    conversation.title,
                    color = Color(theme.primaryTextColor),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isOwn = message.senderId == currentUserId,
                        theme = theme,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(theme.surfaceColor), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (composerText.isEmpty()) {
                        Text("Message", color = Color(argbWithAlpha(theme.secondaryTextColor, 0.7)), fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = composerText,
                        onValueChange = { composerText = it },
                        textStyle = TextStyle(color = Color(theme.primaryTextColor), fontSize = 15.sp),
                        cursorBrush = SolidColor(Color(theme.accentColor)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = composerText.trim()
                        if (text.isNotEmpty() && !sending) {
                            sending = true
                            composerText = ""
                            scope.launch {
                                try {
                                    onSend(text)
                                } finally {
                                    sending = false
                                }
                            }
                        }
                    },
                    enabled = !sending && composerText.isNotBlank(),
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(theme.accentColor), CircleShape),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = Color(theme.onAccentColor),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isOwn: Boolean, theme: ThemeConfig) {
    val bubbleColor = if (isOwn) theme.accentColor else theme.surfaceColor
    val textColor = if (isOwn) theme.onAccentColor else theme.primaryTextColor
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(Color(bubbleColor), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (message.text.isNotEmpty()) {
                    Text(message.text, color = Color(textColor), fontSize = 15.sp)
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        bubbleTimeFormatter.format(message.createdAt.atZone(ZoneId.systemDefault())),
                        color = Color(argbWithAlpha(textColor, 0.6)),
                        fontSize = 10.5.sp,
                    )
                    if (isOwn) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (message.deliveryState) {
                                DeliveryState.sending, DeliveryState.queued -> "\u23F1"   // ⏱
                                DeliveryState.failed -> "!"
                                DeliveryState.sent -> "\u2713"                            // ✓
                                DeliveryState.delivered -> "\u2713\u2713"                 // ✓✓
                                DeliveryState.read -> "\u2713\u2713"
                            },
                            color = Color(argbWithAlpha(textColor, if (message.deliveryState == DeliveryState.read) 1.0 else 0.6)),
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
        }
    }
}

private val bubbleTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
