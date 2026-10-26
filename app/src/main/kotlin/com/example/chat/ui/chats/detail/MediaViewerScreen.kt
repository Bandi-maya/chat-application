package com.example.chat.ui.chats.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port of lib/features/messages/media_viewer_screen.dart: signed-url
 *  resolution via ChatMediaService.createSignedUrl (3600s expiry), image
 *  viewer with InteractiveViewer zoom (0.7..5), video playback with tap
 *  toggle / play overlay / scrubbable progress, and the audio/document/file
 *  fallback card with open-externally + share actions.
 *
 *  Seams: [createSignedUrl] must be wired to ChatMediaService.createSignedUrl
 *  (integration owner); the default pass-through matches that method's
 *  http(s)/data: branches so direct URLs work without the service. Video
 *  uses android.widget.VideoView (no new Gradle dependency; media3 is not
 *  on the branch). */
@Composable
fun MediaViewerScreen(
    theme: ThemeConfig,
    title: String,
    type: String,
    size: String,
    storagePath: String? = null,
    modifier: Modifier = Modifier,
    createSignedUrl: suspend (objectPath: String, expiresInSeconds: Int) -> String = { path, _ -> path },
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var error by remember { mutableStateOf<String?>(null) }
    var signedUrl by remember { mutableStateOf<String?>(null) }

    // Video state (Dart VideoPlayerController value).
    var videoReady by remember { mutableStateOf(false) }
    var videoFailed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var bufferedPct by remember { mutableIntStateOf(0) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    // Dart _load.
    LaunchedEffect(storagePath, type) {
        error = null
        signedUrl = null
        videoReady = false
        videoFailed = false
        isPlaying = false
        val path = storagePath
        if (path.isNullOrEmpty()) {
            error = "This attachment does not have a valid storage path."
            return@LaunchedEffect
        }
        try {
            // Dart: video keeps the spinner until controller.initialize()
            // resolves; the video branch overlays its own spinner until
            // onPrepared fires.
            signedUrl = createSignedUrl(path, 3600)
        } catch (e: Exception) {
            error = (e.message ?: e.toString()).removePrefix("Exception: ")
        }
    }

    // Poll playback position/state (Dart controller value listener).
    LaunchedEffect(videoView, videoReady) {
        val vv = videoView
        if (vv == null || !videoReady) return@LaunchedEffect
        while (true) {
            positionMs = vv.currentPosition
            bufferedPct = vv.bufferPercentage
            isPlaying = vv.isPlaying
            delay(250)
        }
    }

    DisposableEffect(videoView) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    // Dart _openExternally (url_launcher externalApplication).
    fun openExternally() {
        val url = signedUrl ?: return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            scope.launch {
                snackbarHostState.showSnackbar("No application could open this attachment.")
            }
        }
    }

    // Dart _share (share_plus text + subject).
    fun share() {
        val url = signedUrl ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            this.type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        try {
            context.startActivity(
                Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            // share_plus silently no-ops without a target; keep parity.
        }
    }

    // Dart _toggleVideo.
    fun toggleVideo() {
        val vv = videoView ?: return
        if (!videoReady) return
        if (vv.isPlaying) vv.pause() else vv.start()
        isPlaying = vv.isPlaying
    }

    val elevated = Color(theme.cardColor)
    val foreground = Color(theme.primaryTextColor)
    val foregroundSecondary = Color(theme.secondaryTextColor)
    val primary = Color(theme.accentColor)
    val borderSubtle = Color(theme.dividerColor)

    Scaffold(
        modifier = modifier,
        containerColor = elevated,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(elevated.copy(alpha = 0.85f))
                    .padding(start = 4.dp, end = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = "Back",
                        tint = foreground,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = foreground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W600,
                    )
                    Text(
                        size,
                        color = foregroundSecondary,
                        fontSize = 11.5.sp,
                    )
                }
                IconButton(onClick = ::openExternally, enabled = signedUrl != null) {
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = "Open with app",
                        tint = foreground,
                    )
                }
                IconButton(onClick = ::share, enabled = signedUrl != null) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = "Share file link",
                        tint = foreground,
                    )
                }
                Spacer(Modifier.width(4.dp)) // ChatySpacing.xs
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> Text(
                    error!!,
                    textAlign = TextAlign.Center,
                    color = foregroundSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp), // ChatySpacing.lg
                )

                signedUrl == null -> CircularProgressIndicator(
                    strokeWidth = 2.2.dp,
                    color = primary,
                )

                type == "image" -> ImageViewerContent(
                    url = signedUrl!!,
                    title = title,
                    primary = primary,
                    foregroundSecondary = foregroundSecondary,
                    theme = theme,
                )

                type == "video" -> {
                    val url = signedUrl!!
                    key(url) {
                    Column(
                        Modifier.fillMaxSize(),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val aspect = if (videoWidth == 0 || videoHeight == 0) {
                                16f / 9f
                            } else {
                                videoWidth.toFloat() / videoHeight.toFloat()
                            }
                            Box(
                                Modifier
                                    .aspectRatio(aspect)
                                    .clipToBounds()
                                    .pointerInput(videoReady) {
                                        detectTapGestures { toggleVideo() }
                                    },
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(Uri.parse(url))
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = false
                                                videoWidth = mp.videoWidth
                                                videoHeight = mp.videoHeight
                                                durationMs = mp.duration
                                                videoReady = true
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                videoFailed = true
                                                true
                                            }
                                            setOnCompletionListener {
                                                isPlaying = false
                                            }
                                            videoView = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (videoFailed) {
                                    // Dart fallback when the controller never
                                    // initializes.
                                    MediaViewerFallbackCard(
                                        icon = Icons.Rounded.VideocamOff,
                                        text = "Unable to initialize this video.",
                                        foregroundSecondary = foregroundSecondary,
                                        theme = theme,
                                        actionLabel = "Open Securely",
                                        onAction = ::openExternally,
                                    )
                                } else if (!videoReady) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.2.dp,
                                        color = primary,
                                    )
                                } else {
                                    // Dart AnimatedOpacity play overlay (160ms).
                                    // The unscoped AnimatedVisibility is
                                    // deprecated-hidden in animation 1.7, so
                                    // a content-sized Column provides the
                                    // ColumnScope receiver at the same spot.
                                    Column(modifier = Modifier.align(Alignment.Center)) {
                                        AnimatedVisibility(
                                            visible = !isPlaying,
                                            enter = fadeIn(tween(160)),
                                            exit = fadeOut(tween(160)),
                                        ) {
                                            Box(
                                                Modifier
                                                    .background(
                                                        Color(theme.surfaceColor).copy(alpha = 0.7f),
                                                        CircleShape,
                                                    )
                                                    .padding(12.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.PlayArrow,
                                                    contentDescription = "Play",
                                                    tint = foreground,
                                                    modifier = Modifier.size(54.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (videoReady && !videoFailed) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 18.dp),
                            ) {
                                IconButton(onClick = ::toggleVideo) {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = foreground,
                                    )
                                }
                                Spacer(Modifier.width(8.dp)) // ChatySpacing.sm
                                VideoProgressBar(
                                    position = if (durationMs > 0) {
                                        positionMs.toFloat() / durationMs.toFloat()
                                    } else {
                                        0f
                                    },
                                    buffered = bufferedPct / 100f,
                                    playedColor = primary,
                                    bufferedColor = primary.copy(alpha = 0.3f),
                                    backgroundColor = borderSubtle,
                                    onSeek = { fraction ->
                                        val vv = videoView ?: return@VideoProgressBar
                                        val target = (fraction.coerceIn(0f, 1f) * durationMs).toInt()
                                        vv.seekTo(target)
                                        positionMs = target
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    }
                }

                else -> {
                    val icon = when (type) {
                        "audio" -> Icons.Rounded.GraphicEq
                        "document" -> Icons.Rounded.Description
                        else -> Icons.Rounded.InsertDriveFile
                    }
                    MediaViewerFallbackCard(
                        icon = icon,
                        text = "This private $type is ready to open securely.",
                        foregroundSecondary = foregroundSecondary,
                        theme = theme,
                        actionLabel = "Open Securely",
                        onAction = ::openExternally,
                    )
                }
            }
        }
    }
}


/** Dart InteractiveViewer (minScale 0.7, maxScale 5) + Image.network with
 *  loading spinner and error fallback.
 *
 *  Transform parity with Flutter's InteractiveViewer (verified against
 *  packages/flutter/lib/src/widgets/interactive_viewer.dart, stable):
 *  the viewer maps a scene point x to f(x) = s*x + t with the child laid
 *  out at viewport size (constrained=true). graphicsLayer with
 *  transformOrigin (0,0) is exactly f(x) = s*x + t, so the same (s, t)
 *  state reproduces it.
 *  Scale (_matrixScale): totalScale = max(current * gesture,
 *  max(viewport.w / boundary.w, viewport.h / boundary.h)), then clamped to
 *  [minScale, maxScale]. boundaryMargin is zero and the child is
 *  viewport-sized, so the cover factor is 1.0 and the effective minimum
 *  scale is 1.0 — the declared minScale 0.7 never permits sub-viewport
 *  scaling ("scaling below 1.0 will not be allowed ... without first
 *  increasing the boundaryMargin", minScale doc).
 *  Zoom keeps the gesture centroid fixed: t' = c - s'*(c - t)/s;
 *  incremental pan adds.
 *  Translation (_exceedsBy): the transformed viewport must stay inside the
 *  child rect, so per axis t in [(1-s)*W, 0] (reachable domain s >= 1),
 *  W the viewport extent. Rotation is hard-disabled upstream
 *  (_rotateEnabled = false); dropped here too. */
@Composable
private fun ImageViewerContent(
    url: String,
    title: String,
    primary: Color,
    foregroundSecondary: Color,
    theme: ThemeConfig,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(viewportW, viewportH) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        // _matrixScale: max(current * gesture, cover) then
                        // clamp to [minScale, maxScale]. Child is
                        // viewport-sized (constrained=true) and
                        // boundaryMargin is zero, so cover ==
                        // max(viewport.w / child.w, viewport.h / child.h)
                        // == 1f and the declared 0.7 minimum never applies.
                        val coverScale = 1f
                        val newScale = (oldScale * zoom)
                            .coerceAtLeast(coverScale)
                            .coerceIn(0.7f, 5f)
                        scale = newScale
                        val factor = newScale / oldScale
                        val zoomed = Offset(
                            x = centroid.x - (centroid.x - offset.x) * factor,
                            y = centroid.y - (centroid.y - offset.y) * factor,
                        ) + pan
                        // _exceedsBy clamp: transformed viewport must stay
                        // inside the child rect; on the reachable domain
                        // (s >= cover == 1) this is t in [(1-s)*W, 0].
                        val minX = minOf(0f, (1f - newScale) * viewportW)
                        val maxX = maxOf(0f, (1f - newScale) * viewportW)
                        val minY = minOf(0f, (1f - newScale) * viewportH)
                        val maxY = maxOf(0f, (1f - newScale) * viewportH)
                        offset = Offset(
                            zoomed.x.coerceIn(minX, maxX),
                            zoomed.y.coerceIn(minY, maxY),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
        ChatyAsyncImage(
            url = url,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            placeholder = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.2.dp, color = primary)
                }
            },
            error = {
                MediaViewerFallbackCard(
                    icon = Icons.Rounded.BrokenImage,
                    text = "Unable to display this image.",
                    foregroundSecondary = foregroundSecondary,
                    theme = theme,
                )
            },
        )
        }
    }
}

/** Dart VideoProgressIndicator (allowScrubbing) with played/buffered/
 *  background colors. */
@Composable
private fun VideoProgressBar(
    position: Float,
    buffered: Float,
    playedColor: Color,
    bufferedColor: Color,
    backgroundColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                detectTapGestures { tap -> onSeek((tap.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            drawRect(backgroundColor)
            drawRect(
                bufferedColor,
                size = Size(size.width * buffered.coerceIn(0f, 1f), size.height),
            )
            drawRect(
                playedColor,
                size = Size(size.width * position.coerceIn(0f, 1f), size.height),
            )
        }
    }
}

/** Dart _fallbackCard: 76dp icon, message, optional 'Open Securely'
 *  primary button (width 180, open_in_new icon). */
@Composable
private fun MediaViewerFallbackCard(
    icon: ImageVector,
    text: String,
    foregroundSecondary: Color,
    theme: ThemeConfig,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp), // ChatySpacing.xl
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = foregroundSecondary,
            modifier = Modifier.size(76.dp),
        )
        Spacer(Modifier.height(20.dp)) // ChatySpacing.lg
        Text(
            text,
            textAlign = TextAlign.Center,
            color = foregroundSecondary,
            fontSize = 14.5.sp,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(theme.accentColor),
                    contentColor = Color(theme.onAccentColor),
                ),
                modifier = Modifier.width(180.dp),
            ) {
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}
