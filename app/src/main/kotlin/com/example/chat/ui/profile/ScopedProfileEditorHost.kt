package com.example.chat.ui.profile

import androidx.compose.runtime.*
import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.api.ApiScopeChangedException
import com.example.chat.domain.models.UserProfile
import kotlinx.coroutines.channels.Channel


@Composable
fun ScopedProfileEditorHost(
    operations: ProfileEditorOperations,
    onBack: () -> Unit,
    picker: @Composable (() -> Boolean) -> ProfileAvatarDraftFlow = { current -> rememberProfileAvatarDraftFlow(current) },
    compress: (ByteArray) -> ByteArray = { raw ->
        ProfileImageCompressor.compress(raw, 512, 88) ?: error("Could not process profile photo.")
    },
) {
    val scopeChannel = remember(operations) { Channel<ApiAccountScope>(Channel.CONFLATED) }
    val scopeState = produceState(operations.snapshot(), operations) { for (next in scopeChannel) value = next }
    DisposableEffect(operations) {
        val listener: (ApiAccountScope) -> Unit = { scopeChannel.trySend(it); Unit }
        operations.addListener(listener)
        onDispose { operations.removeListener(listener); scopeChannel.close() }
    }
    val expected = scopeState.value
    if (operations.snapshot() !== expected) return
    val user = operations.user()?.takeIf { it.id == expected.userId }
    if (user == null || expected.token.isNullOrBlank()) { LaunchedEffect(Unit) { onBack() }; return }
    key(expected) {
        val current = { operations.snapshot() === expected }
        val flow = picker(current)
        val session = remember(expected, operations) { ProfileEditorSaveSession(expected, operations, compress) }
        var draft by remember { mutableStateOf<ProfileAvatarDraft?>(null) }
        ProfileEditScreen(
            user = user,
            avatarDraftPath = draft?.let { "file://" + it.previewPath },
            avatarDraft = draft,
            isCurrentPreview = current,
            onBack = { flow.discard(); onBack() },
            onPickAvatar = { source ->
                val next = flow.pick(source)
                if (!current()) { next?.close(); throw ApiScopeChangedException() }
                if (next != null) { draft = next; session.replace(next) }
                null
            },
            onPickBanner = null,
            onSave = { name, username, about, _, _ ->
                session.save(name, username, about, user.avatarUrl)
                flow.discard(); draft = null
            },
        )
    }
}
