package com.example.chat.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.chat.ui.theme.LocalAppColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure date math keeps date selection local and preserves the existing due time. */
object ChatyTaskDateRules {
    fun cells(month: YearMonth): List<LocalDate?> {
        val offset = month.atDay(1).dayOfWeek.value - 1
        return List(42) { i -> (i - offset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay) }
    }
    fun selectable(date: LocalDate, today: LocalDate): Boolean = !date.isBefore(today) && !date.isAfter(today.plusDays(3650))
    fun replaceDate(due: Instant, chosen: LocalDate, zone: ZoneId): Instant {
        val time = due.atZone(zone).toLocalTime().withSecond(0).withNano(0)
        return chosen.atTime(time).atZone(zone).toInstant()
    }
}

/** Chaty-owned month grid, not the Material calendar. Confirmation alone changes the due date. */
@Composable
fun ChatyTaskCalendar(due: Instant, onDismiss: () -> Unit, onConfirm: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var selected by remember { mutableStateOf(due.atZone(zone).toLocalDate()) }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val first = YearMonth.from(today)
    val last = YearMonth.from(today.plusDays(3650))
    val colors = LocalAppColors.current
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose due date") },
        dismissButton = { ChatyChoiceTile("Cancel", onDismiss) },
        confirmButton = { ChatyChoiceTile("Use date", { onConfirm(ChatyTaskDateRules.replaceDate(due, selected, zone)) },
            enabled = ChatyTaskDateRules.selectable(selected, today), prominent = true) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChatyChoiceTile("Previous month", { month = month.minusMonths(1) }, Modifier.weight(1f), enabled = month > first)
                    ChatyChoiceTile("Next month", { month = month.plusMonths(1) }, Modifier.weight(1f), enabled = month < last)
                }
                Spacer(Modifier.height(12.dp))
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = colors.foreground)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), color = colors.foregroundSecondary) }
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChatyTaskDateRules.cells(month).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            week.forEach { day ->
                                if (day == null) Spacer(Modifier.weight(1f).height(48.dp))
                                else ChatyChoiceTile(day.dayOfMonth.toString(), { selected = day }, Modifier.weight(1f),
                                    selected = selected == day, enabled = ChatyTaskDateRules.selectable(day, today), role = Role.RadioButton, compact = true)
                            }
                        }
                    }
                }
            }
        },
    )
}
