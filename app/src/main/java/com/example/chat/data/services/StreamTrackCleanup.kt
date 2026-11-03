package com.example.chat.data.services

/** WebRTC MediaStream.dispose owns added tracks, but detached tracks still need disposal. */
internal object StreamTrackCleanup {
    fun dispose(stream: (() -> Unit)?, tracks: List<Pair<Boolean, () -> Unit>>) {
        stream?.invoke()
        tracks.forEach { (ownedByStream, disposeTrack) -> if (!ownedByStream) disposeTrack() }
    }
}
