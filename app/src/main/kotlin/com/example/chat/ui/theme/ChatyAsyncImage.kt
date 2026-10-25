package com.example.chat.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

/** Shared remote-image loader for the port (Coil). All screens load avatar /
 *  media URLs through this wrapper so caching and placeholders stay uniform;
 *  [placeholder]/[error] are simple color boxes supplied by the caller until
 *  the media slice ports the original placeholder assets. */
@Composable
fun ChatyAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
) {
    if (url.isNullOrBlank()) {
        error?.invoke() ?: placeholder?.invoke()
        return
    }
    val painter = rememberAsyncImagePainter(model = url)
    when (painter.state) {
        is AsyncImagePainter.State.Loading -> placeholder?.invoke()
        is AsyncImagePainter.State.Error -> (error ?: placeholder)?.invoke()
        else -> Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
