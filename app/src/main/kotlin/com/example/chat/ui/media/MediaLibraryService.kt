package com.example.chat.ui.media

import com.example.chat.BuildConfig
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact port of lib/features/media/services/media_library_service.dart.
 *
 *  path_provider's application documents + temporary directories are the
 *  [libraryDirectories] seam (Android: context.filesDir + context.cacheDir).
 *  ChangeNotifier is ported as the same listener pattern used by
 *  ChatyPreferencesController. The demo-asset fallback list is preserved
 *  verbatim from the Dart source.
 */
class MediaLibraryService(
    private val libraryDirectories: () -> List<File>,
    private val now: () -> Instant = { Instant.now() },
) {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private val _files = mutableListOf<MediaFileDescriptor>()
    private var _isLoading = false
    private var _searchQuery = ""
    private var _activeCategoryFilter: MediaCategory? = null

    val files: List<MediaFileDescriptor> get() = _files.toList()
    val isLoading: Boolean get() = _isLoading
    val searchQuery: String get() = _searchQuery
    val activeCategoryFilter: MediaCategory? get() = _activeCategoryFilter

    val filteredFiles: List<MediaFileDescriptor>
        get() = _files.filter { f ->
            if (_activeCategoryFilter != null && f.category != _activeCategoryFilter) {
                return@filter false
            }
            if (_searchQuery.isNotEmpty() &&
                !f.name.lowercase().contains(_searchQuery.lowercase())
            ) {
                return@filter false
            }
            true
        }

    fun setFilter(category: MediaCategory?) {
        _activeCategoryFilter = category
        notifyListeners()
    }

    fun setSearchQuery(query: String) {
        _searchQuery = query
        notifyListeners()
    }

    suspend fun refreshLibrary() {
        _isLoading = true
        notifyListeners()

        // Directory enumeration and file-header inspection can touch thousands
        // of files. Keep all disk I/O off the UI dispatcher.
        val scannedFiles = withContext(Dispatchers.IO) {
            val result = mutableListOf<MediaFileDescriptor>()
            try {
                for (dir in libraryDirectories()) {
                    if (!dir.exists() || !dir.isDirectory) continue
                    dir.listFiles()?.forEach entityLoop@{ entity ->
                        if (!entity.isFile) return@entityLoop
                        val name = entity.name
                        // Ignore hidden entries and Chaty transient transfer/cache
                        // files; they are implementation data, not user media.
                        if (name.startsWith(".") || name.startsWith("chaty-outgoing-") ||
                            name.endsWith(".part", ignoreCase = true) ||
                            name.endsWith(".tmp", ignoreCase = true)
                        ) return@entityLoop
                        val classification = FileTypeDetector.classifyFile(
                            headerBytesOf = {
                                try {
                                    entity.inputStream().use { stream ->
                                        val buffer = ByteArray(FileTypeDetector.HEADER_BYTES_TO_READ)
                                        val read = stream.read(buffer)
                                        if (read <= 0) ByteArray(0) else buffer.copyOf(read)
                                    }
                                } catch (_: Exception) {
                                    ByteArray(0)
                                }
                            },
                            filePath = entity.path,
                        )
                        result.add(
                            MediaFileDescriptor(
                                id = entity.path,
                                name = name,
                                path = entity.path,
                                sizeBytes = entity.length(),
                                mimeType = classification.mimeType,
                                category = classification.category,
                                modifiedAt = Instant.ofEpochMilli(entity.lastModified()),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                // A directory can disappear or become inaccessible during a scan.
            }

            if (result.isEmpty() && BuildConfig.DEBUG) {
                val n = now()
                result.addAll(
                    listOf(
                        MediaFileDescriptor(
                            id = "demo_doc_spec",
                            name = "Project_Architecture_Spec.pdf",
                            path = "demo/Project_Architecture_Spec.pdf",
                            sizeBytes = 2569011,
                            mimeType = "application/pdf",
                            category = MediaCategory.document,
                            modifiedAt = n.minusMillis(2 * 60 * 60 * 1000),
                            pageCount = 14,
                        ),
                        MediaFileDescriptor(
                            id = "demo_doc_sheet",
                            name = "Q4_Financial_Roadmap.xlsx",
                            path = "demo/Q4_Financial_Roadmap.xlsx",
                            sizeBytes = 1145728,
                            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            category = MediaCategory.document,
                            modifiedAt = n.minusMillis(5 * 60 * 60 * 1000),
                            pageCount = 3,
                        ),
                        MediaFileDescriptor(
                            id = "demo_audio_anirudh",
                            name = "Hangova - Anirudh.mp3",
                            path = "demo/Hangova - Anirudh.mp3",
                            sizeBytes = 5033164,
                            mimeType = "audio/mpeg",
                            category = MediaCategory.audio,
                            modifiedAt = n.minusMillis(8 * 60 * 60 * 1000),
                            durationSeconds = 225,
                        ),
                        MediaFileDescriptor(
                            id = "demo_audio_meeting",
                            name = "Design_Review_Audio.m4a",
                            path = "demo/Design_Review_Audio.m4a",
                            sizeBytes = 1843200,
                            mimeType = "audio/mp4",
                            category = MediaCategory.audio,
                            modifiedAt = n.minusMillis(24 * 60 * 60 * 1000),
                            durationSeconds = 98,
                        ),
                    ),
                )
            }
            result.sortByDescending { it.modifiedAt }
            result
        }

        _files.clear()
        _files.addAll(scannedFiles)
        _isLoading = false
        notifyListeners()
    }

    fun getStorageBreakdown(): Map<MediaCategory, Long> {
        val map = mutableMapOf<MediaCategory, Long>()
        for (f in _files) {
            map[f.category] = (map[f.category] ?: 0L) + f.sizeBytes
        }
        return map
    }
}
