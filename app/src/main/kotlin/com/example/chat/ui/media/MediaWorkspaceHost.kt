package com.example.chat.ui.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Media-area navigation host replacing Flutter's imperative Navigator pushes
 *  across media_workspace_screen / custom_file_picker_screen /
 *  pdf_workspace_screen / document_reader_screen / chaty_audio_player_screen.
 *
 *  Faithful behaviors preserved:
 *  - returning from File Explorer or PDF Studio refreshes the library
 *    (Dart's Navigator.push(...).then((_) => _library.refreshLibrary()))
 *  - hardware/gesture back pops the media stack
 *  - the audio player is a fullscreen-dialog style overlay destination
 */
@Composable
fun MediaWorkspaceHost(
    playerCoordinator: MediaPlayerCoordinator,
    library: MediaLibraryService,
    sanitizer: FileSanitizationService,
    pdfService: PdfProcessingService,
    onSendAttachments: (List<MediaFileDescriptor>) -> Unit = {},
    onClose: () -> Unit = {},
) {
    var stack by remember { mutableStateOf<List<MediaDestination>>(listOf(MediaDestination.Workspace)) }

    fun push(d: MediaDestination) { stack = stack + d }
    fun pop(): Boolean {
        return if (stack.size > 1) {
            val popped = stack.last()
            stack = stack.dropLast(1)
            // Dart refresh-on-return for the two workspace pushes
            if (popped is MediaDestination.FileExplorer || popped is MediaDestination.PdfStudio) {
                library.refreshLibrary()
            }
            true
        } else false
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    AnimatedContent(targetState = stack.last(), label = "mediaNav") { dest ->
        when (dest) {
            is MediaDestination.Workspace -> MediaWorkspaceScreen(
                playerCoordinator = playerCoordinator,
                library = library,
                sanitizer = sanitizer,
                onOpenFileExplorer = { push(MediaDestination.FileExplorer) },
                onOpenPdfStudio = { push(MediaDestination.PdfStudio) },
                onOpenDocument = { f -> push(MediaDestination.Reader(f)) },
                onExpandPlayer = { push(MediaDestination.Player) },
            )
            is MediaDestination.FileExplorer -> CustomFilePickerScreen(
                libraryService = library,
                isWorkspaceMode = true,
                title = "All Media & Files",
                onOpenDocument = { f -> push(MediaDestination.Reader(f)) },
                onSendAttachments = onSendAttachments,
                onClose = { pop() },
            )
            is MediaDestination.PdfStudio -> PdfWorkspaceScreen(
                library = library,
                pdfService = pdfService,
                onOpenDocument = { f -> push(MediaDestination.Reader(f)) },
            )
            is MediaDestination.Reader -> DocumentReaderScreen(
                file = dest.file,
                pdfService = pdfService,
                onClose = { pop() },
            )
            is MediaDestination.Player -> ChatyAudioPlayerScreen(
                coordinator = playerCoordinator,
                onClose = { pop() },
            )
        }
    }
}

sealed interface MediaDestination {
    data object Workspace : MediaDestination
    data object FileExplorer : MediaDestination
    data object PdfStudio : MediaDestination
    data class Reader(val file: MediaFileDescriptor) : MediaDestination
    data object Player : MediaDestination
}
