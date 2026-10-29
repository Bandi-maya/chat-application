package com.example.chat.ui.settings.universal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
            encryptor = { plain -> BackupCrypto.encrypt(plain) },
            decryptor = { envelope -> BackupCrypto.decrypt(envelope) },
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


    // ---- Portable passphrase backup (settings + downloaded media) ----
    // In-app feature: the passphrase is typed and confirmed here, never
    // stored by Chaty. Forgetting it makes the archive unrecoverable; the
    // dialog says so. Chat history itself is server-backed and returns after
    // sign-in; this archive carries settings and downloaded media only.
    val apiBackend = remember { com.example.chat.ServiceLocator.apiBackend }
    val mediaCache = remember { com.example.chat.ServiceLocator.mediaCache }
    val prefsStorage = remember { com.example.chat.ServiceLocator.preferencesStorage }
    val currentAccountId: String? get() = apiBackend.currentUser?.id

    var exportPass1 by remember { mutableStateOf("") }
    var exportPass2 by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var restorePass by remember { mutableStateOf("") }
    var pendingRestoreText by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupPortableLogic.Preview?>(null) }
    var pendingRestoreParsed by remember { mutableStateOf<BackupArchiveLogic.ParsedArchive?>(null) }
    var portableBusy by remember { mutableStateOf(false) }

    fun exportTo(uri: android.net.Uri, passphrase: CharArray) {
        scope.launch {
            portableBusy = true
            val message = withContext(Dispatchers.IO) {
                try {
                    val account = currentAccountId ?: return@withContext "Sign in before creating a backup."
                    var mediaNote = ""
                    val collector = BackupSectionCollector(
                        context, prefsStorage, account,
                        mediaExporter = {
                            val media = exportMediaSections(apiBackend, mediaCache)
                            val skipped = media.skippedOversizeFiles + media.skippedOverQuotaFiles
                            if (skipped > 0) {
                                mediaNote = " ${media.includedFiles} media files included; " +
                                    "$skipped skipped (over the per-file or total size limit)."
                            }
                            media.sections
                        },
                    )
                    val sections = collector.collectSections()
                    val created = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
                    val envelope = BackupPortableLogic.buildEncryptedArchive(account, created, sections, passphrase)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
                        ?: return@withContext "Could not write the backup file."
                    "Portable backup saved.$mediaNote Keep your passphrase safe - without it this backup cannot be restored."
                } catch (e: Exception) {
                    "Backup failed: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    passphrase.fill('\u0000')
                }
            }
            portableBusy = false
            onMessage(message)
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val p1 = exportPass1
        if (uri != null && p1.isNotEmpty()) exportTo(uri, p1.toCharArray())
        exportPass1 = ""; exportPass2 = ""
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
                    catch (_: Exception) { null }
                }
                if (text == null) onMessage("Could not read that file.") else pendingRestoreText = text
            }
        }
    }

    fun runPortableRestore() {
        val envelope = pendingRestoreText ?: return
        val pass = restorePass.toCharArray()
        scope.launch {
            portableBusy = true
            val parsed = withContext(Dispatchers.IO) {
                try { BackupPortableLogic.readEncryptedArchive(envelope, pass) }
                catch (e: Exception) { null }
                finally { pass.fill('\u0000') }
            }
            portableBusy = false
            if (parsed == null) {
                onMessage("Could not open the backup: wrong passphrase or damaged file.")
            } else {
                preview = BackupPortableLogic.preview(parsed, currentAccountId, BackupSectionCollector(context, prefsStorage, currentAccountId ?: "", { emptyList() }).storeFor())
                pendingRestoreParsed = parsed
            }
            restorePass = ""
        }
    }

    fun confirmPortableRestore() {
        val parsed = pendingRestoreParsed ?: return
        scope.launch {
            portableBusy = true
            val result = withContext(Dispatchers.IO) {
                BackupPortableLogic.importArchive(
                    parsed, currentAccountId,
                    BackupSectionCollector(context, prefsStorage, currentAccountId ?: "", { emptyList() }).storeFor(),
                )
            }
            portableBusy = false
            preview = null; pendingRestoreParsed = null; pendingRestoreText = null
            onMessage(when (result) {
                is BackupPortableLogic.ImportResult.Ok -> result.message
                is BackupPortableLogic.ImportResult.WrongAccount ->
                    "This backup belongs to a different account. Sign in to that account first, then restore."
                is BackupPortableLogic.ImportResult.Failure -> result.message
            })
        }
    }

    SettingsPage(title = "Backup and restore", onBack = onBack) {
        // SECTION: Chaty Data
        SettingsSection(title = "Chaty Data") {
            BackupRow(
                icon = Icons.Rounded.CloudUpload,
                title = "Backup Chaty settings",
                subtitle = "Encrypted local backup of appearance and settings only. Chats, media and account data are not included. Nothing is uploaded.",
                busy = isBackingUp,
                onClick = { if (!isBackingUp) runBackup("Chaty database") },
            )
            BackupRow(
                icon = Icons.Rounded.SettingsBackupRestore,
                title = "Restore Chaty settings",
                subtitle = "Restores appearance and settings from your newest encrypted backup. Readable only on this device while Chaty stays installed. Chats and media are not changed.",
                busy = isRestoring,
                onClick = { if (!isRestoring) runRestore("Chaty database") },
            )
        }


        // SECTION: Portable backup
        SettingsSection(title = "Portable backup (settings + downloaded media)") {
            BackupRow(
                icon = Icons.Rounded.CloudUpload,
                title = "Create portable backup",
                subtitle = "Encrypted with a passphrase you choose. Carries settings and downloaded media; it does not contain chat history. Works across devices and reinstalls.",
                busy = portableBusy,
                onClick = { if (!portableBusy) showExportDialog = true },
            )
            BackupRow(
                icon = Icons.Rounded.RestorePage,
                title = "Restore portable backup",
                subtitle = "Pick a backup file, enter its passphrase, review what will be restored and where, then confirm. Only restores into the same account.",
                busy = portableBusy,
                onClick = { if (!portableBusy) openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            )
        }

        // SECTION: Media
        SettingsSection(title = "Media") {
            BackupRow(
                icon = Icons.Rounded.PermMedia,
                title = "Backup Media",
                subtitle = "Not available yet. Media is not included in any backup.",
                busy = false,
                onClick = { runBackup("Media archives") },
            )
            BackupRow(
                icon = Icons.Rounded.RestorePage,
                title = "Restore Media",
                subtitle = "Not available yet.",
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
                subtitle = "Deletes temporary files and all but the newest old backup file in the folder below",
                busy = false,
                showChevron = false,
                onClick = { runClear() },
            )
        }

        // SECTION: Location
        SettingsSection(title = "Location") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "• Old (unencrypted) backup files, if any: ${logic.backupPath}",
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

    // Passphrase dialog for export (entry + confirmation + recovery warning).
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false; exportPass1 = ""; exportPass2 = "" },
            title = { Text("Choose a backup passphrase") },
            text = {
                Column {
                    Text(
                        "This passphrase encrypts your backup. Chaty never stores it. " +
                            "If you forget it, the backup cannot be recovered - there is no reset.",
                        color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exportPass1, onValueChange = { exportPass1 = it },
                        label = { Text("Passphrase (min. 8 characters)") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportPass2, onValueChange = { exportPass2 = it },
                        label = { Text("Repeat passphrase") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    )
                    if (exportPass1.isNotEmpty() && exportPass2.isNotEmpty() && exportPass1 != exportPass2) {
                        Text("Passphrases do not match.", color = colors.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = exportPass1.length >= 8 && exportPass1 == exportPass2,
                    onClick = {
                        showExportDialog = false
                        createLauncher.launch("chaty_backup_${System.currentTimeMillis()}.chb")
                    },
                ) { Text("Create backup") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false; exportPass1 = ""; exportPass2 = "" }) { Text("Cancel") } },
        )
    }

    // Passphrase dialog for restore.
    if (pendingRestoreText != null && preview == null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreText = null; restorePass = "" },
            title = { Text("Enter backup passphrase") },
            text = {
                OutlinedTextField(
                    value = restorePass, onValueChange = { restorePass = it },
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                )
            },
            confirmButton = {
                Button(enabled = restorePass.isNotEmpty() && !portableBusy, onClick = { runPortableRestore() }) { Text("Open backup") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreText = null; restorePass = "" }) { Text("Cancel") } },
        )
    }

    // Preview + explicit confirmation before any restore mutation.
    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null; pendingRestoreParsed = null; pendingRestoreText = null },
            title = { Text("Restore this backup?") },
            text = {
                Column {
                    Text("Created: ${p.createdAt}", color = colors.foregroundSecondary, fontSize = 12.sp)
                    if (!p.matchesCurrentAccount) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "This backup belongs to a different account. Sign in to that account first - restore is blocked here.",
                            color = colors.error, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    p.entries.forEach { e ->
                        Text(
                            "• ${e.sectionName} (${"%.1f".format(e.bytes / 1024.0)} KB) -> ${e.destinationDescription}",
                            color = colors.foreground, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This backup does not contain or restore chat history; what the server can provide after sign-in may vary. " +
                            "Settings and wallpaper choices REPLACE your current ones. Media is added as ordinary copies in Downloads/ChatyRestore - nothing there is overwritten.",
                        color = colors.foregroundSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = p.matchesCurrentAccount && p.entries.all { it.restorable } && !portableBusy,
                    onClick = { confirmPortableRestore() },
                ) { Text("Overwrite settings and restore") }
            },
            dismissButton = {
                TextButton(onClick = { preview = null; pendingRestoreParsed = null; pendingRestoreText = null }) { Text("Cancel") }
            },
        )
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
