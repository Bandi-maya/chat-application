package com.example.chat.ui.camera

internal fun shouldRecordAudio(requested: Boolean, microphoneGranted: Boolean): Boolean = requested && microphoneGranted
internal fun canReviewVideo(hasFinalizeError: Boolean, bytes: Long, durationMs: Long?, hasVideo: Boolean): Boolean =
    !hasFinalizeError && bytes > 0 && durationMs != null && durationMs > 0 && hasVideo
