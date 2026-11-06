package com.example.chat.core.audio

import kotlinx.coroutines.Job

/** Cancellation requests an exit; only completion proves the reader closed its codec/file. */
internal fun releaseAfterReader(reader: Job?, release: () -> Unit) {
    if (reader == null) release()
    else {
        reader.cancel()
        reader.invokeOnCompletion { release() }
    }
}
