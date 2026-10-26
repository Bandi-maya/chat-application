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
