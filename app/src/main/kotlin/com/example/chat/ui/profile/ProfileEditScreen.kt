package com.example.chat.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.data.services.ProfileMediaSource
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.ChatyAsyncImage
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/**
 * Port of lib/features/profile/profile_edit_screen.dart (api @ 3bee780d):
 * the dedicated full-screen profile editor (Dart Rule 21).
 *  - Banner + overlapping avatar, each with a change button and the
 *    camera/gallery source sheet (exact Dart row copy).
 *  - Display name / username / about validation, phone read-only row.
 *  - Unsaved-changes discard dialog on back (exact Dart copy).
 *  - Duplicate-submission prevention while saving.
 * Media picking and persistence stay outside: the caller supplies suspend
 * callbacks the integration owner wires to ApiBackendService.updateProfile
 * (PUT /app-users/profile) and the photo-upload path. checkUsernameAvailable
 * is nullable because the custom backend exposes no username-availability
 * endpoint on the api branch; when null, the live-availability row is
 * skipped and a taken username surfaces as the server's save error, which
 * is exactly what the api-branch custom-backend path does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    user: UserProfile,
    onBack: () -> Unit,
    /** Returns the new public URL, or null when the user cancelled picking. */
    onPickAvatar: suspend (ProfileMediaSource) -> String?,
    /** Null when banner upload has no backend path (button hidden). */
    onPickBanner: (suspend (ProfileMediaSource) -> String?)? = null,
    /** Throws with a user-readable message on failure. */
    onSave: suspend (displayName: String, username: String, about: String, avatarUrl: String?, bannerUrl: String?) -> Unit,
    checkUsernameAvailable: (suspend (String) -> Boolean)? = null,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var displayName by remember { mutableStateOf(user.displayName) }
    var username by remember { mutableStateOf(user.username) }
    var about by remember { mutableStateOf(user.about) }
    var isSaving by remember { mutableStateOf(false) }
    var usernameTaken by remember { mutableStateOf(false) }
    var newAvatarUrl by remember { mutableStateOf<String?>(null) }
    var newBannerUrl by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var sourceSheetFor by remember { mutableStateOf<String?>(null) } // "avatar" | "banner"

    // Exact _isDirty check from profile_edit_screen.dart.
    val isDirty = displayName.trim() != user.displayName ||
        username.trim() != user.username ||
        about.trim() != user.about ||
        newAvatarUrl != null ||
        newBannerUrl != null

    fun attemptBack() {
        if (!isDirty && !isSaving) onBack() else showDiscardDialog = true
    }
    BackHandler { attemptBack() }

    val activeAvatarUrl = newAvatarUrl ?: user.avatarUrl
    val activeBannerUrl = newBannerUrl ?: user.bannerUrl
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val bannerHeight = (screenWidth - 32) * 9 / 16

    fun pick(source: ProfileMediaSource) {
        val target = sourceSheetFor ?: return
        sourceSheetFor = null
        val picker = if (target == "avatar") onPickAvatar else onPickBanner
        if (picker == null) return
        scope.launch {
            isSaving = true
            try {
                val url = picker(source)
                if (url != null) {
                    if (target == "avatar") newAvatarUrl = url else newBannerUrl = url
                }
            } catch (error: Throwable) {
                if (error.message?.contains("cancelled") != true) {
                    snackbar.showSnackbar(
                        if (target == "avatar") "Could not upload profile photo: ${error.message}"
                        else "Could not upload banner photo: ${error.message}",
                    )
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun save() {
        if (isSaving) return
        // Inline validators from profile_edit_screen.dart.
        val name = displayName.trim()
        if (name.isEmpty()) {
            scope.launch { snackbar.showSnackbar("Display name cannot be empty") }
            return
        }
        if (name.length < 2) {
            scope.launch { snackbar.showSnackbar("Must be at least 2 characters") }
            return
        }
        ChatyValidators.validateUsername(username)?.let {
            scope.launch { snackbar.showSnackbar(it) }
            return
        }
        val normalized = ChatyValidators.normalizeUsername(username)
        val unchanged = normalized == ChatyValidators.normalizeUsername(user.username)
        if (!unchanged && usernameTaken) {
            scope.launch { snackbar.showSnackbar("Please select an available username before saving.") }
            return
        }
        scope.launch {
            isSaving = true
            try {
                // Live availability re-check at save time when a checker is
                // wired (mirrors the Dart save-time re-check).
                if (!unchanged && checkUsernameAvailable != null &&
                    checkUsernameAvailable(normalized) == false
                ) {
                    usernameTaken = true
                    isSaving = false
                    return@launch
                }
                onSave(name, normalized, about.trim(), newAvatarUrl, newBannerUrl)
                isSaving = false
                snackbar.showSnackbar("Profile updated successfully.")
                onBack()
            } catch (error: Throwable) {
                isSaving = false
                snackbar.showSnackbar("Could not save profile: ${error.message}")
            }
        }
    }

    Scaffold(
        containerColor = Color(theme.backgroundColor),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(theme.primaryTextColor),
                        )
                    }
                },
                title = { Text("Edit Profile", color = Color(theme.primaryTextColor)) },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 12.dp),
                            strokeWidth = 2.2.dp,
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { save() },
                            modifier = Modifier.padding(end = 12.dp),
                        ) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Banner & overlapping avatar (Dart Rule 20 layout).
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((bannerHeight + 72).dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(bannerHeight.dp)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(theme.cardColor)),
                ) {
                    val bannerPlaceholder: @Composable () -> Unit = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(theme.accentColor).copy(alpha = 0.18f),
                                            Color(theme.cardColor),
                                        ),
                                    ),
                                ),
                        )
                    }
                    ChatyAsyncImage(
                        url = activeBannerUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = bannerPlaceholder,
                        error = bannerPlaceholder,
                    )
                    if (onPickBanner != null) {
                        IconButton(
                            onClick = { sourceSheetFor = "banner" },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .background(Color(theme.surfaceColor).copy(alpha = 0.85f), CircleShape),
                        ) {
                            Icon(
                                Icons.Rounded.Wallpaper,
                                contentDescription = "Change banner photo",
                                tint = Color(theme.primaryTextColor),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                // Avatar overlapping the bottom-center edge.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 10.dp),
                ) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .shadow(12.dp, CircleShape)
                            .border(3.5.dp, Color(theme.surfaceColor), CircleShape)
                            .clip(CircleShape)
                            .background(Color(theme.accentColor).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val avatarFallback: @Composable () -> Unit = {
                            Text(
                                user.avatarInitials.ifEmpty { "U" },
                                color = Color(theme.accentColor),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        ChatyAsyncImage(
                            url = activeAvatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            placeholder = avatarFallback,
                            error = avatarFallback,
                        )
                    }
                    IconButton(
                        onClick = { sourceSheetFor = "avatar" },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(34.dp)
                            .border(2.5.dp, Color(theme.surfaceColor), CircleShape)
                            .background(Color(theme.accentColor), CircleShape),
                    ) {
                        Icon(
                            Icons.Rounded.PhotoCamera,
                            contentDescription = "Change avatar",
                            tint = Color(theme.onAccentColor),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 50) displayName = it },
                    enabled = !isSaving,
                    label = { Text("Display Name") },
                    placeholder = { Text("Enter your full name") },
                    leadingIcon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
                    supportingText = { Text("${displayName.length}/50") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        if (it.length <= 30) {
                            username = it
                            usernameTaken = false
                        }
                    },
                    enabled = !isSaving,
                    label = { Text("Username") },
                    placeholder = { Text("Choose a unique username") },
                    prefix = { Text("@") },
                    leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
                    isError = usernameTaken,
                    supportingText = {
                        Text(
                            if (usernameTaken) "This username is already taken"
                            else "${username.length}/30",
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = about,
                    onValueChange = { if (it.length <= 140) about = it },
                    enabled = !isSaving,
                    label = { Text("About / Status") },
                    placeholder = { Text("Say something about yourself or current mood") },
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    supportingText = { Text("${about.length}/140") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = user.phone.ifEmpty { "Not set" },
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                    supportingText = { Text("Phone number is managed via Account settings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Exact _promptImageSource sheet copy from profile_edit_screen.dart.
    sourceSheetFor?.let { target ->
        ModalBottomSheet(onDismissRequest = { sourceSheetFor = null }) {
            Text(
                if (target == "avatar") "Change Profile Photo" else "Change Banner Photo",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Capture Image") },
                supportingContent = { Text("Use camera to take a new photo") },
                leadingContent = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                modifier = Modifier.clickable { pick(ProfileMediaSource.camera) },
            )
            ListItem(
                headlineContent = { Text("Pick / Choose from Gallery") },
                supportingContent = { Text("Select an image from device gallery") },
                leadingContent = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                modifier = Modifier.clickable { pick(ProfileMediaSource.gallery) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDiscardDialog) {
        // Exact ChatyConfirmDialog copy from profile_edit_screen.dart.
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes that will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                ) { Text("Discard", color = Color(theme.dangerColor)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep Editing") }
            },
        )
    }
}
