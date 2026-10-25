package com.example.chat.ui.chats.detail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.domain.models.ConnectionHealth
import com.example.chat.domain.models.ConnectionHealthState
import com.example.chat.domain.models.NetworkTransportType
import com.example.chat.domain.models.RealtimeSocketStatus

/** Port of lib/ui/core/connection/connection_detail_sheet.dart: connection
 *  diagnostics dialog (transport, backend, realtime, latency, pending queue)
 *  with a Test Connection action. [onRunTest] is the service-owned probe seam
 *  (Dart ConnectionHealthService.probeConnection). */
@Composable
fun ConnectionDetailSheetContent(
    state: ConnectionHealthState,
    theme: ThemeConfig,
    testing: Boolean,
    onRunTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val statusColor = when (state.health) {
        ConnectionHealth.excellent -> connectionExcellent
        ConnectionHealth.weak -> connectionWeak
        ConnectionHealth.poor -> connectionPoor
        ConnectionHealth.offline -> connectionOffline
    }
    val foreground = Color(theme.primaryTextColor)
    val foregroundSecondary = Color(theme.secondaryTextColor)

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(theme.backgroundColor), RoundedCornerShape(24.dp))
            .border(1.dp, Color(theme.dividerColor), RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConnectionHealthIndicator(health = state.health, size = 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Connection Health",
                    color = foreground,
                    fontWeight = FontWeight.W700,
                    fontSize = 16.sp,
                )
                Text(
                    state.displaySubtitle,
                    color = foregroundSecondary,
                    fontSize = 12.sp,
                )
            }
            Box(
                Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    state.displayTitle,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W700,
                )
            }
        }

        Box(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(theme.dividerColor)))
        Box(Modifier.height(16.dp))

        MetricRow(
            label = "Network Transport",
            value = when (state.transport) {
                NetworkTransportType.wifi -> "Wi-Fi"
                NetworkTransportType.cellular -> "Mobile Data"
                NetworkTransportType.ethernet -> "Ethernet"
                NetworkTransportType.vpn -> "VPN"
                NetworkTransportType.other -> "Available"
                NetworkTransportType.none -> "Disconnected"
            },
            icon = when (state.transport) {
                NetworkTransportType.wifi -> Icons.Rounded.Wifi
                NetworkTransportType.cellular -> Icons.Rounded.SignalCellularAlt
                NetworkTransportType.ethernet -> Icons.Rounded.Lan
                NetworkTransportType.vpn -> Icons.Rounded.VpnKey
                else -> Icons.Rounded.SignalWifiOff
            },
            labelColor = foregroundSecondary,
            valueColor = foreground,
        )
        MetricRow(
            label = "Chat Backend Service",
            value = if (state.backendReachable) {
                "Operational"
            } else if (state.internetReachable) {
                "Unreachable"
            } else {
                "No Internet"
            },
            icon = Icons.Rounded.CloudDone,
            labelColor = foregroundSecondary,
            valueColor = if (state.backendReachable) connectionExcellent else connectionPoor,
        )
        MetricRow(
            label = "Realtime Sync",
            value = when (state.realtimeStatus) {
                RealtimeSocketStatus.connected -> "Connected"
                RealtimeSocketStatus.connecting -> "Connecting…"
                RealtimeSocketStatus.reconnecting -> "Reconnecting…"
                RealtimeSocketStatus.disconnected -> "Disconnected"
            },
            icon = Icons.Rounded.Sync,
            labelColor = foregroundSecondary,
            valueColor = when (state.realtimeStatus) {
                RealtimeSocketStatus.connected -> connectionExcellent
                RealtimeSocketStatus.reconnecting -> connectionWeak
                else -> foregroundSecondary
            },
        )
        MetricRow(
            label = "Latency",
            value = state.latency?.let { "${it.inWholeMilliseconds} ms" } ?: "—",
            icon = Icons.Rounded.Speed,
            labelColor = foregroundSecondary,
            valueColor = foreground,
        )
        MetricRow(
            label = "Pending Outgoing Messages",
            value = "${state.queuedMessagesCount}",
            icon = Icons.Rounded.MarkChatUnread,
            labelColor = foregroundSecondary,
            valueColor = if (state.queuedMessagesCount > 0) connectionWeak else foreground,
        )

        Box(Modifier.height(20.dp))

        Row {
            Button(
                onClick = onRunTest,
                enabled = !testing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (testing) "Testing…" else "Test Connection")
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    icon: ImageVector,
    labelColor: Color,
    valueColor: Color,
) {
    Row(
        Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = labelColor,
            fontSize = 13.5.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = valueColor,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.W600,
        )
    }
}
