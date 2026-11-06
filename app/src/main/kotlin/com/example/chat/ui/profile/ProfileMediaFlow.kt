package com.example.chat.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.chat.data.services.ProfileMediaCancelledException
import com.example.chat.data.services.ProfileMediaService
import com.example.chat.data.services.ProfileMediaSource
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/**
 * Suspend-state surface of [rememberProfileMediaFlow]: the Dart
 * ProfileMediaService._pickCompressUpload pipeline as callable functions.
 * Both throw [ProfileMediaCancelledException] when the user cancels the pick
 * or the crop (Dart _CancelledException; callers match on "cancelled").
 */
class ProfileMediaFlowState internal constructor(
    /** Dart uploadAvatar: pick (512 * 1.8 bound, q95) -> 1:1 crop -> upload. */
    val avatar: suspend (ProfileMediaSource) -> String,
    /** Dart uploadBanner: pick (1280 * 1.8 bound, q95) -> 16:9 crop -> upload. */
    val banner: suspend (ProfileMediaSource) -> String,
)

/**
 * Hosts the actual reachable Dart profile-media flow that the current Kotlin
 * AppRoot was missing: image_picker-style bounded pick, then the
 * ImageEditorCropperDialog port ([ProfileImageCropperDialog]), then the
 * Kotlin [ProfileMediaService] validation/compression/upload pipeline.
 *
 * [service] must hand back a ProfileMediaService whose imageCompressor seam
 * is wired to [ProfileImageCompressor.compress]; with the default seam the
 * bytes upload uncompressed (Dart compresses every time it can).
 *
 * Compose this once in any host that offers avatar/banner picking and call
 * the returned functions from button handlers.
 */
@Composable
fun rememberProfileMediaFlow(
    service: () -> ProfileMediaService,
): ProfileMediaFlowState {
    val waiter = remember { mutableStateOf<CompletableDeferred<File?>?>(null) }
    val picker = rememberProfileMediaPicker(
        onResult = { file ->
            waiter.value?.complete(file)
            waiter.value = null
        },
        onError = { message ->
            waiter.value?.completeExceptionally(Exception(message))
            waiter.value = null
        },
    )

    suspend fun run(source: ProfileMediaSource, isAvatar: Boolean): String {
        val deferred = CompletableDeferred<File?>()
        waiter.value = deferred
        picker.launch(source, isAvatar, if (isAvatar) 512 else 1280)
        val file = deferred.await() ?: throw ProfileMediaCancelledException()
        try {
            val bytes = file.readBytes()
            // Dart: avatar 512/q88 -> avatars, banner 1280/q85 -> banners.
            return if (isAvatar) service().uploadAvatar(bytes) else service().uploadBanner(bytes)
        } finally {
            file.delete()
        }
    }

    // Cheap value object; run() only touches remember-stable picker/waiter.
    return ProfileMediaFlowState(
        avatar = { source -> run(source, true) },
        banner = { source -> run(source, false) },
    )
}

/** Local-only replacement for profile editor picking. Save/upload remains with the scoped host.
 * Replaces and deletes the previous draft only after a new crop succeeds; cancellation keeps it.
 * This flow is avatar-only. A banner backend contract is not available. */
class ProfileAvatarDraftFlow internal constructor(
    val pick: suspend (ProfileMediaSource) -> ProfileAvatarDraft?,
    val discard: () -> Unit,
)

@Composable
fun rememberProfileAvatarDraftFlow(isCurrent: () -> Boolean): ProfileAvatarDraftFlow {
    val currentCheck = androidx.compose.runtime.rememberUpdatedState(isCurrent)
    val waiter = remember { mutableStateOf<CompletableDeferred<File?>?>(null) }
    val delivered = remember { mutableStateOf<File?>(null) }
    val draft = remember { mutableStateOf<ProfileAvatarDraft?>(null) }
    // A cancelled native picker cannot be correlated to a new launch. Retire this host instead
    // of allowing a late callback to fulfil a later account/launch's waiter.
    val retired = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val picker = rememberProfileMediaPicker(
        isCurrent = { !retired.get() && currentCheck.value() },
        onResult = { file ->
            val pending = waiter.value
            waiter.value = null
            try {
                if (retired.get() || !currentCheck.value() || pending == null) file?.let(ProfileLocalOutputs::delete)
                else {
                    delivered.value = file
                    if (!pending.complete(file)) { delivered.value = null; file?.let(ProfileLocalOutputs::delete) }
                }
            } catch (error: Exception) {
                ProfileLocalOutputs.markBlocked(error)
                pending?.completeExceptionally(error)
            }
        },
        onError = { message ->
            waiter.value?.completeExceptionally(IllegalStateException(message))
            waiter.value = null
        },
    )
    fun discard() {
        retired.set(true)
        waiter.value?.cancel()
        waiter.value = null
        runCatching { delivered.value?.let(ProfileLocalOutputs::delete) }.onFailure {
            if (it is Exception) ProfileLocalOutputs.markBlocked(it)
        }
        delivered.value = null
        runCatching { draft.value?.close() }.onFailure {
            if (it is Exception) ProfileLocalOutputs.markBlocked(it)
        }
        draft.value = null
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { discard() } }
    return ProfileAvatarDraftFlow(
        pick = { source ->
            ProfileLocalOutputs.requireReady()
            check(!retired.get() && currentCheck.value()) { "Account changed; profile photo cancelled." }
            check(waiter.value == null) { "A profile photo picker is already open." }
            val pending = CompletableDeferred<File?>()
            waiter.value = pending
            try {
                picker.launch(source, true, 512)
                val file = pending.await()
                if (file == null) null
                else if (retired.get() || !currentCheck.value()) {
                    ProfileLocalOutputs.delete(file)
                    error("Account changed; profile photo cancelled.")
                } else {
                    delivered.value = null
                    val next = ProfileAvatarDraft(file, ProfileLocalOutputs::delete)
                    draft.value?.close()
                    draft.value = next
                    next
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                retired.set(true)
                throw cancelled
            } finally {
                runCatching { delivered.value?.let(ProfileLocalOutputs::delete) }.onFailure {
                    if (it is Exception) ProfileLocalOutputs.markBlocked(it)
                }
                delivered.value = null
                if (waiter.value === pending) waiter.value = null
                // Normal null cancellation is safe to retry. Coroutine cancellation retires the
                // host so an outstanding ActivityResult cannot target a future request.
                if (!pending.isCompleted) { retired.set(true); pending.cancel() }
            }
        },
        discard = ::discard,
    )
}
