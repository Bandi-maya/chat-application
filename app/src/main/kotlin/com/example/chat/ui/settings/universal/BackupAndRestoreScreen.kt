package com.example.chat.ui.settings.universal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.RestorePage
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.domain.models.UniversalPreferences
import com.example.chat.ui.settings.SettingsPage
import com.example.chat.ui.settings.SettingsSection
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Port of lib/features/settings/universal/backup_and_restore_screen.dart.
 * All file/manifest behavior lives in the pure [BackupRestoreLogic]; this
 * composable is wiring + rendering only.
 *
 * [conversationsSnapshot] supplies the serialized conversations array for the
 * backup payload (Dart read locator<ChatyDataStore>().conversations and mapped
 * the exact keys in BackupRestoreLogic.CONVERSATION_KEYS). Default empty
 * matches the Dart behavior when the data store is not registered.
 *
 * Directory mapping (flagged for owner verification on device):
 * Dart getApplicationDocumentsDirectory -> context.filesDir;
 * Dart getTemporaryDirectory -> context.cacheDir.
 */
@Composable
fun BackupAndRestoreScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onMessage: (String) -> Unit = {},
    conversationsSnapshot: () -> List<Map<String, Any?>> = { emptyList() },
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logic = remember {
        BackupRestoreLogic(
            docsDir = { context.filesDir },
            tempDir = { context.cacheDir },
            preferencesSnapshot = {
                linkedMapOf(
                    "universal" to preferences.universal.toMap(),
                    "home" to preferences.home.toMap(),
                    "conversation" to preferences.conversation.toMap(),
                    "privacy" to preferences.privacy.toMap(),
                    "security" to preferences.security.toMap(),
                    "notification" to preferences.notification.toMap(),
                )
            },
            conversationsSnapshot = conversationsSnapshot,
            applyUniversal = { map ->
                preferences.updateUniversal(UniversalPreferences.fromMap(map))
            },
        )
    }

    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var isCalculating by remember { mutableStateOf(false) }
    var totalStorageBytes by remember { mutableLongStateOf(0L) }

    fun refreshStorage() {
        scope.launch {
            isCalculating = true
            val total = withContext(Dispatchers.IO) {
                try {
                    logic.ensureBackupDir()
                    logic.totalStorageBytes()
                } catch (_: Exception) {
                    totalStorageBytes
                }
            }
            totalStorageBytes = total
            isCalculating = false
        }
    }

    // Dart initState: _initPathsAndStorage()
    LaunchedEffect(Unit) { refreshStorage() }

    fun runBackup(target: String) {
        scope.launch {
            isBackingUp = true
            val result = withContext(Dispatchers.IO) { logic.performBackup(target) }
            isBackingUp = false
            refreshStorage()
            onMessage(resultMessage(result))
        }
    }

    fun runRestore(target: String) {
        scope.launch {
            isRestoring = true
            val result = withContext(Dispatchers.IO) { logic.performRestore(target) }
            isRestoring = false
            onMessage(resultMessage(result))
        }
    }

    fun runClear() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { logic.clearTemporaryAndBackups() }
            refreshStorage()
            onMessage(resultMessage(result))
        }
    }

    SettingsPage(title = "Backup and restore", onBack = onBack) {
        // SECTION: Chaty Data
        SettingsSection(title = "Chaty Data") {
            BackupRow(
                icon = Icons.Rounded.CloudUpload,
                title = "Backup Chaty data",
                subtitle = "Create an authentic backup of Chaty chats and account preferences",
                busy = isBackingUp,
                onClick = { if (!isBackingUp) runBackup("Chaty database") },
            )
            BackupRow(
                icon = Icons.Rounded.SettingsBackupRestore,
                title = "Restore Chaty data",
                subtitle = "Restore the most recent local backup file",
                busy = isRestoring,
                onClick = { if (!isRestoring) runRestore("Chaty database") },
            )
        }

        // SECTION: Media
        SettingsSection(title = "Media") {
            BackupRow(
                icon = Icons.Rounded.PermMedia,
                title = "Backup Media",
                subtitle = "Media and documents configuration",
                busy = false,
                onClick = { runBackup("Media archives") },
            )
            BackupRow(
                icon = Icons.Rounded.RestorePage,
                title = "Restore Media",
                subtitle = "Restore the most recent media backup",
                busy = false,
                onClick = { runRestore("Media archives") },
            )
        }

        // SECTION: Chaty Storage Cleanup
        SettingsSection(title = "Storage Maintenance") {
            BackupRow(
                icon = Icons.Rounded.Storage,
                title = "Storage Footprint",
                subtitle = if (isCalculating) "Calculating…" else "Current Size: ${logic.formatMb(totalStorageBytes)} MB",
                busy = false,
                showChevron = false,
                onClick = { refreshStorage() },
            )
            BackupRow(
                icon = Icons.Rounded.DeleteSweep,
                title = "Clear",
                subtitle = "Erase old temporary backups and cache logs",
                busy = false,
                showChevron = false,
                onClick = { runClear() },
            )
        }

        // SECTION: Location
        SettingsSection(title = "Location") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "• Chaty backup location: ${logic.backupPath}",
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Database location: ${logic.databasePath}",
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

private fun resultMessage(result: BackupRestoreLogic.Result): String = when (result) {
    is BackupRestoreLogic.Result.BackupOk -> result.message
    is BackupRestoreLogic.Result.RestoreOk -> result.message
    is BackupRestoreLogic.Result.ClearedOk -> result.message
    is BackupRestoreLogic.Result.Failure -> result.message
}

@Composable
private fun BackupRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    busy: Boolean,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.W600)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else if (showChevron) {
            Text("›", color = colors.foregroundSecondary, fontSize = 22.sp)
        }
    }
}
