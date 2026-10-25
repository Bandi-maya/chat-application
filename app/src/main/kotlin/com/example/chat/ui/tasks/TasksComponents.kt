package com.example.chat.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.TaskPriority
import com.example.chat.domain.models.TaskStatus

typealias TaskColors = com.example.chat.ui.core.theme.ChatyColors

/** Exact port of status_badge.dart StatusBadge. */
@Composable
fun TaskStatusBadge(status: TaskStatus, colors: TaskColors) {
    val (bg, fg, label) = when (status) {
        TaskStatus.inbox -> Triple(
            colors.foregroundSecondary.copy(alpha = 0.15f), colors.foregroundSecondary, "Inbox",
        )
        TaskStatus.assigned -> Triple(colors.info.copy(alpha = 0.15f), colors.info, "Assigned")
        TaskStatus.inProgress -> Triple(colors.warning.copy(alpha = 0.15f), colors.warning, "In Progress")
        TaskStatus.blocked -> Triple(colors.error.copy(alpha = 0.15f), colors.error, "Blocked")
        TaskStatus.completed -> Triple(colors.success.copy(alpha = 0.15f), colors.success, "Done")
        TaskStatus.archived -> Triple(
            colors.foregroundTertiary.copy(alpha = 0.15f), colors.foregroundTertiary, "Archived",
        )
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(0.8.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Exact port of status_badge.dart PriorityBadge. */
@Composable
fun TaskPriorityBadge(priority: TaskPriority, colors: TaskColors) {
    val (col, text) = when (priority) {
        TaskPriority.low -> colors.foregroundSecondary to "Low"
        TaskPriority.medium -> colors.info to "Med"
        TaskPriority.high -> colors.warning to "High"
        TaskPriority.urgent -> colors.error to "Urgent"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(col.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Flag, contentDescription = null, tint = col, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = col, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Port of app_avatar.dart's initials+color paint (presence badge omitted;
 *  the tasks surfaces never show it). */
@Composable
fun TaskAvatar(initials: String, colorHex: String?, size: Int, colors: TaskColors) {
    val parsed = runCatching {
        val clean = (colorHex ?: "").replace("#", "").replace("0x", "")
        Color((if (clean.length == 6) "FF$clean" else clean).toLong(16))
    }.getOrNull() ?: colors.primary
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(parsed),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = Color.White,
            fontSize = (size * 0.38).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Exact port of the delete confirmation from task_detail_screen.dart. */
@Composable
fun DeleteTaskDialog(onCancel: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete Task") },
        text = { Text("Are you sure you want to delete this task? This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFF44336)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

@Composable
fun TaskLoadingIndicator() {
    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.2.dp)
}
