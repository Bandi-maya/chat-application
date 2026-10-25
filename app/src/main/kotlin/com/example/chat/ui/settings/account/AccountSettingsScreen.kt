package com.example.chat.ui.settings.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySettingsTile
import com.example.chat.ui.settings.ChatySwitchTile
import com.example.chat.ui.theme.ChatyAsyncImage
import com.example.chat.ui.theme.LocalAppColors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

/** Current-user snapshot for the account screen (ChatyDataStore.currentUser
 *  equivalent; the host maps ApiUserProfile / its user store onto this). */
data class AccountUserInfo(
    val id: String,
    val displayName: String,
    val username: String,
    val about: String,
    val avatarInitials: String,
    val avatarUrl: String?,
)

private val DangerRed = Color(0xFFFF5252) // Colors.redAccent

/** Port of lib/features/settings/account/account_settings_screen.dart.
 *
 *  All backend effects are injected (the Dart screen reads them from the
 *  locator): the host wires ApiBackendService + the account delete/logout
 *  orchestration. [onOpenSubscription] replaces the PlanSubscriptionScreen
 *  push with the host's route change. The QR tile is a no-op in the Dart
 *  source (onTap: {}); kept as a no-op for parity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    user: AccountUserInfo,
    organization: () -> Pair<String, String>,
    onBack: () -> Unit,
    onOpenSubscription: (organizationId: String, organizationName: String) -> Unit,
    checkOrganization: suspend (workspace: String) -> Map<String, Any?>,
    updateRegistrationSupport: suspend (organizationIdOrTenantId: String, allow: Boolean) -> Boolean,
    updateProfile: suspend (displayName: String, about: String, username: String, avatarUrl: String?) -> Unit,
    uploadProfilePhoto: suspend (filename: String, bytes: ByteArray) -> String,
    deleteAccountData: suspend (userId: String) -> Unit,
    logout: suspend () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    fun snack(message: String, background: Color? = null) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    // Dart _confirmDeleteAccount: confirm dialog -> deleteUser + local purge
    // -> confirmChatyLogout. The logout confirm is a second dialog, exactly
    // like the source.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account?") },
            text = {
                Text(
                    "This will permanently delete your user profile, message history, and remove you from all workspaces. This action is irreversible.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        isDeleting = true
                        scope.launch {
                            try {
                                deleteAccountData(user.id)
                                isDeleting = false
                                showLogoutDialog = true
                            } catch (e: Exception) {
                                isDeleting = false
                                snack("Failed to delete account: $e", DangerRed)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                ) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Dart confirmChatyLogout (profile_actions.dart).
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out of Chaty?") },
            text = {
                Text("Your account will be signed out on this device. Your chats and account data remain on the server.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            try {
                                logout()
                            } catch (e: Exception) {
                                snack("Could not log out: $e")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showEditSheet) {
        EditProfileSheet(
            user = user,
            onDismiss = { showEditSheet = false },
            uploadProfilePhoto = uploadProfilePhoto,
            updateProfile = updateProfile,
            snack = { msg, bg -> snack(msg, bg) },
        )
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ChatySettingsPage(
                title = "Account & Profile",
                subtitle = "Profile photo, display name, username & bio",
                onBack = onBack,
            ) {
                // 1. PROFILE DETAILS
                ChatySettingsSection(title = "Profile Information") {
                    ChatySettingsTile(
                        leading = { AccountAvatar(user, colors.primary, 42) },
                        title = user.displayName.ifEmpty { "Display Name" },
                        subtitle = "@${user.username}",
                        trailing = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onTap = { showEditSheet = true },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Info,
                        iconColor = colors.primary,
                        title = "About / Bio",
                        subtitle = if (user.about.isNotEmpty()) user.about else "No bio set (Tap to edit)",
                        onTap = { showEditSheet = true },
                    )
                }

                // 2. WORKSPACE & SUBSCRIPTION
                ChatySettingsSection(title = "Workspace & Registration") {
                    ChatySettingsTile(
                        icon = Icons.Rounded.WorkspacePremium,
                        iconColor = colors.accent,
                        title = "Subscription & Plans",
                        subtitle = "Manage workspace plan, billing & feature limits",
                        onTap = {
                            val (orgId, orgName) = organization()
                            onOpenSubscription(orgId, orgName)
                        },
                    )
                    WorkspaceRegistrationToggle(
                        organization = organization,
                        checkOrganization = checkOrganization,
                        updateRegistrationSupport = updateRegistrationSupport,
                        snack = { msg, bg -> snack(msg, bg) },
                        accentColor = colors.accent,
                    )
                }

                // 3. ACCOUNT ACTIONS
                ChatySettingsSection(title = "Account Actions") {
                    ChatySettingsTile(
                        icon = Icons.Rounded.QrCode,
                        iconColor = colors.primary,
                        title = "My QR Code Identity",
                        subtitle = "Share your public contact card",
                        onTap = { /* no-op in the Dart source (onTap: {}) */ },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Logout,
                        iconColor = colors.error,
                        title = "Log Out",
                        subtitle = "Sign out of your current session on this device",
                        onTap = { showLogoutDialog = true },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.DeleteForever,
                        iconColor = DangerRed,
                        title = "Delete Account",
                        subtitle = "Permanently delete your profile and account data",
                        enabled = !isDeleting,
                        onTap = { showDeleteDialog = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountAvatar(user: AccountUserInfo, color: Color, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (!user.avatarUrl.isNullOrBlank()) {
            ChatyAsyncImage(
                url = user.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = { AvatarInitials(user.avatarInitials, sizeDp) },
                error = { AvatarInitials(user.avatarInitials, sizeDp) },
            )
        } else {
            AvatarInitials(user.avatarInitials, sizeDp)
        }
    }
}

@Composable
private fun AvatarInitials(initials: String, sizeDp: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            initials,
            color = Color.White,
            fontSize = (sizeDp * 0.4).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Dart _WorkspaceRegistrationToggle. */
@Composable
private fun WorkspaceRegistrationToggle(
    organization: () -> Pair<String, String>,
    checkOrganization: suspend (workspace: String) -> Map<String, Any?>,
    updateRegistrationSupport: suspend (organizationIdOrTenantId: String, allow: Boolean) -> Boolean,
    snack: (String, Color?) -> Unit,
    accentColor: Color,
) {
    var loading by remember { mutableStateOf(false) }
    var allowRegistration by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val (orgId) = organization()
        if (orgId.isEmpty()) return@LaunchedEffect
        try {
            val data = checkOrganization(orgId)
            val flag = data["allow_user_registration"] ?: data["user_registration_enabled"]
            allowRegistration = when (flag) {
                is Boolean -> flag
                null -> true
                else -> flag.toString().lowercase() != "false"
            }
        } catch (_: Exception) {
        }
    }

    ChatySwitchTile(
        icon = Icons.Rounded.PersonAdd,
        iconColor = Color(0xFF6366F1),
        title = "Member Self-Registration",
        subtitle = when {
            loading -> "Updating workspace setting..."
            allowRegistration -> "New users can self-register using workspace ID"
            else -> "Self-registration disabled (invite-only)"
        },
        value = allowRegistration,
        enabled = !loading,
        onChanged = { value ->
            val (orgId) = organization()
            loading = true
            scope.launch {
                try {
                    val success = updateRegistrationSupport(orgId, value)
                    if (success) {
                        allowRegistration = value
                        loading = false
                        snack(
                            if (value) "User self-registration enabled for workspace."
                            else "User self-registration disabled for workspace.",
                            accentColor,
                        )
                    } else {
                        loading = false
                        snack("Failed to update registration settings. Admin rights required.", DangerRed)
                    }
                } catch (e: Exception) {
                    loading = false
                    snack("Error: ${(e.message ?: e.toString()).removePrefix("Exception: ")}", DangerRed)
                }
            }
        },
    )
}

/** Dart _showEditProfileSheet. Photo pick + resize mirrors image_picker
 *  (gallery, max 1024px, JPEG quality 85). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    user: AccountUserInfo,
    onDismiss: () -> Unit,
    uploadProfilePhoto: suspend (filename: String, bytes: ByteArray) -> String,
    updateProfile: suspend (displayName: String, about: String, username: String, avatarUrl: String?) -> Unit,
    snack: (String, Color?) -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(user.displayName) }
    var about by remember { mutableStateOf(user.about) }
    var avatarUrl by remember { mutableStateOf(user.avatarUrl) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploadingPhoto = true
        scope.launch {
            try {
                val (filename, bytes) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bytes = readDownscaledJpeg(context.contentResolver, uri, 1024, 85)
                    val name = queryDisplayName(context.contentResolver, uri) ?: "profile_photo.jpg"
                    name to bytes
                }
                val newUrl = uploadProfilePhoto(filename, bytes)
                // Dart: dataStore.updateProfile(currentUser.copyWith(avatarUrl:
                // avatarUrl)) - the stored profile fields, not the edit fields.
                updateProfile(user.displayName, user.about, user.username, newUrl)
                avatarUrl = newUrl
                snack("Profile photo updated!", colors.accent)
            } catch (e: Exception) {
                snack("Failed to upload photo: ${(e.message ?: e.toString()).removePrefix("Exception: ")}", DangerRed)
            } finally {
                isUploadingPhoto = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Edit Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.foreground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(16.dp))

            // Avatar with camera badge.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(80.dp)
                    .clickable(enabled = !isUploadingPhoto) { photoPicker.launch("image/*") },
            ) {
                AccountAvatar(user.copy(avatarUrl = avatarUrl), colors.primary, 80)
                if (isUploadingPhoto) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0x73000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .border(2.dp, colors.surface, CircleShape)
                            .padding(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { photoPicker.launch("image/*") },
                enabled = !isUploadingPhoto,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    "Change Profile Photo",
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display Name") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("About / Bio") },
                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 2,
            )
            Spacer(Modifier.height(22.dp))

            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        try {
                            // Dart: dataStore.updateProfile(copyWith(displayName, about))
                            updateProfile(name.trim(), about.trim(), user.username, avatarUrl)
                            onDismiss()
                            snack("Profile updated successfully!", colors.accent)
                        } catch (e: Exception) {
                            snack("Failed to update: ${(e.message ?: e.toString()).removePrefix("Exception: ")}", DangerRed)
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text("Save Profile", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** image_picker equivalent: gallery image, downscaled so the long edge is at
 *  most [maxEdge] px, re-encoded as JPEG at [quality]. */
private fun readDownscaledJpeg(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri,
    maxEdge: Int,
    quality: Int,
): ByteArray {
    val source = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: throw Exception("Could not read the selected image")
    val longEdge = maxOf(source.width, source.height)
    val scaled = if (longEdge > maxEdge) {
        val scale = maxEdge.toFloat() / longEdge
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        source
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: android.net.Uri): String? = try {
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (_: Exception) {
    null
}
