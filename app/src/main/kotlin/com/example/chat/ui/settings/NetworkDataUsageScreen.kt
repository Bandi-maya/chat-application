package com.example.chat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.ServiceLocator
import com.example.chat.data.network.TransferDirection
import com.example.chat.data.network.TransferItem
import com.example.chat.data.network.TransferStatus

@Composable
fun NetworkDataUsageScreen(onBack: () -> Unit) {
    val monitor = ServiceLocator.transferMonitor
    val transfers by monitor.items.collectAsState()
    val totals by monitor.totals.collectAsState()
    val appTraffic by ServiceLocator.appTrafficMonitor.state.collectAsState()
    val network by ServiceLocator.networkMonitor.state.collectAsState()
    val active = transfers.filter { it.status == TransferStatus.active || it.status == TransferStatus.paused || it.status == TransferStatus.queued }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Network & Data Usage", style = MaterialTheme.typography.titleLarge)
                Text("Live connection and transfer monitor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (network.online) "Internet connected" else "No validated internet", style = MaterialTheme.typography.titleMedium)
                Text("Transport: ${network.transport.uppercase()} · ${if (network.metered) "Metered" else "Unmetered"}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Downlink estimate")
                    Text(if (network.downstreamKbps > 0) "${network.downstreamKbps} kbps" else "Measuring…")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Uplink estimate")
                    Text(if (network.upstreamKbps > 0) "${network.upstreamKbps} kbps" else "Measuring…")
                }
                Text("These are Android link estimates. Transfer speeds below are measured from Chaty traffic.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data used", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Uploaded")
                    Text(formatBytes(totals.uploadedBytes))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Downloaded")
                    Text(formatBytes(totals.downloadedBytes))
                }
                Text("By media type", style = MaterialTheme.typography.labelLarge)
                UsageRow("Messages", totals.messageBytes)
                UsageRow("Images", totals.imageBytes)
                UsageRow("Videos", totals.videoBytes)
                UsageRow("Audio", totals.audioBytes)
                UsageRow("Documents", totals.documentBytes)
                UsageRow("Calls", totals.callBytes)
                UsageRow("Other", totals.otherBytes)
                OutlinedButton(onClick = { monitor.resetTotals() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset usage totals")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total Chaty network traffic", style = MaterialTheme.typography.titleMedium)
                if (!appTraffic.supported) {
                    Text("Android does not expose per-app traffic counters on this device.")
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("All uploaded data")
                        Text(formatBytes(appTraffic.uploadedBytes))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("All downloaded data")
                        Text(formatBytes(appTraffic.downloadedBytes))
                    }
                    Text(
                        "Includes realtime sockets, call signaling, API traffic, media and protocol overhead. Counters are measured by Android for Chaty's app UID.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { ServiceLocator.appTrafficMonitor.resetTotals() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reset total app traffic")
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Active transfers", style = MaterialTheme.typography.titleMedium)
            if (active.isEmpty()) {
                Text("No active uploads or downloads.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                active.forEach { TransferCard(it) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent transfers", style = MaterialTheme.typography.titleMedium)
            val recent = transfers.filter { it.status == TransferStatus.completed || it.status == TransferStatus.failed || it.status == TransferStatus.cancelled }
            if (recent.isEmpty()) Text("Completed and interrupted transfers will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            recent.take(10).forEach { TransferCard(it) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun UsageRow(label: String, bytes: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatBytes(bytes))
    }
}

@Composable
private fun TransferCard(item: TransferItem) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    (if (item.direction == TransferDirection.upload) "↑ Upload" else "↓ Download") + " · " + item.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            val total = item.totalBytes?.let(::formatBytes) ?: "Unknown"
            Text("${formatBytes(item.transferredBytes)} of $total transferred")
            Text(
                buildString {
                    append("Remaining: ")
                    append(item.remainingBytes?.let(::formatBytes) ?: "Unknown")
                    append(" · ")
                    append(formatBytes(item.bytesPerSecond))
                    append("/s")
                    item.etaSeconds?.let { append(" · ETA "); append(formatDuration(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = listOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var index = -1
    while (amount >= 1024 && index < units.lastIndex) {
        amount /= 1024.0
        index++
    }
    return "%.1f %s".format(amount, units[index])
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes < 60) "${minutes}m ${rest}s" else "${minutes / 60}h ${minutes % 60}m"
}
