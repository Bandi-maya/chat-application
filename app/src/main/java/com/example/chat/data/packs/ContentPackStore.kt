package com.example.chat.data.packs

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * On-demand content packs (animated emoji sets, sticker packs) served from the
 * user's own server (see ChatyPackServer). The APK ships only the base assets;
 * other packs are listed by a small JSON manifest, downloaded per pack on first
 * use (a single deflate zip when the manifest offers one, else per file), and
 * cached on disk with an LRU bound. Like WhatsApp/Telegram: nothing downloads
 * until the user picks the pack.
 *
 * Trust boundaries (v3 hardening): the manifest is network input - pack ids,
 * versions and file paths are allow-listed, duplicates rejected, paths resolved
 * safely under the pack root; the manifest is validated BEFORE it is persisted;
 * every download is sha256 verified while streaming; sizes are capped (manifest,
 * per-file, zip entry, zip total including unknown entries, expansion ratio);
 * HTTPS to the pinned host only, with the base URL captured per operation so a
 * config change mid-download cannot mix hosts; blocking loops check coroutine
 * cancellation actively.
 */
class ContentPackStore private constructor(context: Context) {

    private val sourceBase = ChatyPackServer.baseUrl
    private val sourceUri = java.net.URI(sourceBase)
    private val sourceHost = sourceUri.host
    private val sourceHash = MessageDigest.getInstance("SHA-256").digest(sourceBase.toByteArray()).joinToString("") { "%02x".format(it) }
    private val root = File(context.filesDir, "packs/$sourceHash")
    private val rootCanonical = root.canonicalPath
    private val manifestCache = File(root, "manifest.json")
    private val packLocks = ConcurrentHashMap<String, Mutex>()

    /** Serializes whole-cache eviction against promotion/staging in every pack. */
    private val evictionLock = Mutex()
    private val progressLock = Any()
    private val manifestLock = Mutex()

    /** Bumped on every install/remove/manifest refresh so Compose observers re-read. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    /** packId -> 0f..1f while a download runs. */
    private val _downloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloads: StateFlow<Map<String, Float>> = _downloads

    /** Last manifest fetched (or loaded from the disk cache); empty until then. */
    @Volatile
    var manifest: List<PackInfo> = emptyList()
        private set

    data class PackFile(val path: String, val sha256: String, val bytes: Long) {
        val name: String get() = path.substringAfterLast('/')
    }
    data class PackArchive(val path: String, val sha256: String, val bytes: Long)
    data class PackInfo(
        val id: String,
        val name: String,
        val version: String,
        val files: List<PackFile>,
        val archive: PackArchive?,
    ) {
        val totalBytes: Long get() = archive?.bytes ?: files.sumOf { it.bytes }
    }

    enum class PackInstallState { NOT_INSTALLED, PARTIAL, INSTALLED }

    // ---------- validation (manifest is untrusted input) ----------

    private val packIdRegex = Regex("[a-z0-9_-]{1,48}")
    private val versionRegex = Regex("[0-9]{1,8}")
    private val fileNameRegex = Regex("[A-Za-z0-9_.-]{1,96}")
    private val sha256Regex = Regex("[0-9a-fA-F]{64}")

    private fun safePackDir(packId: String, version: String): File? {
        if (!packIdRegex.matches(packId) || !versionRegex.matches(version)) return null
        val f = File(root, "$packId/$version")
        return if (f.canonicalPath.startsWith("$rootCanonical/")) f else null
    }

    private fun safeChild(dir: File, name: String): File? {
        if (!fileNameRegex.matches(name) || name.startsWith(".")) return null
        val f = File(dir, name)
        return if (f.canonicalPath.startsWith(dir.canonicalPath + "/")) f else null
    }

    /** Manifest path must be exactly "<packId>/<version>/<file>" with allow-listed parts. */
    private fun validPath(path: String, packId: String, version: String): Boolean {
        val parts = path.split('/')
        return parts.size == 3 && parts[0] == packId && parts[1] == version &&
            fileNameRegex.matches(parts[2]) && !parts[2].startsWith(".")
    }

    // ---------- queries (all disk IO, all suspending on IO) ----------

    /** Installed version marker for [packId] (numeric ordering), or null. */
    suspend fun installedVersion(packId: String): String? = withContext(Dispatchers.IO) {
        if (!packIdRegex.matches(packId)) return@withContext null
        File(root, packId).listFiles { f -> f.isDirectory && File(f, ".complete").isFile }
            ?.maxByOrNull { it.name.toLongOrNull() ?: -1L }
            ?.name
    }

    suspend fun stateFor(packId: String): PackInstallState = withContext(Dispatchers.IO) {
        if (installedVersion(packId) != null) return@withContext PackInstallState.INSTALLED
        if (!packIdRegex.matches(packId)) return@withContext PackInstallState.NOT_INSTALLED
        val hasFiles = File(root, packId).listFiles()?.any { it.isDirectory && (it.listFiles()?.size ?: 0) > 0 } == true
        if (hasFiles) PackInstallState.PARTIAL else PackInstallState.NOT_INSTALLED
    }

    /** Manifest version for [packId] when it is newer than the installed one. */
    suspend fun updateAvailable(packId: String): String? {
        val remote = manifest.firstOrNull { it.id == packId } ?: return null
        val installed = installedVersion(packId) ?: return null
        return if (remote.version != installed) remote.version else null
    }

    /** Local file for [name] in the installed version of [packId], or null (caller falls back). */
    suspend fun localPath(packId: String, name: String): String? = withContext(Dispatchers.IO) {
        val version = installedVersion(packId) ?: return@withContext null
        val dir = safePackDir(packId, version) ?: return@withContext null
        val f = safeChild(dir, name) ?: return@withContext null
        if (f.isFile) f.absolutePath else null
    }

    /** LRU touch for a served file (its pack dir's timestamp drives eviction). */
    suspend fun touch(packId: String, name: String) = withContext(Dispatchers.IO) {
        val version = installedVersion(packId) ?: return@withContext
        val dir = safePackDir(packId, version) ?: return@withContext
        val now = System.currentTimeMillis()
        safeChild(dir, name)?.takeIf { it.isFile }?.setLastModified(now)
        dir.setLastModified(now)
    }

    /** Remove a pack from the cache. */
    suspend fun removePack(packId: String) = withContext(Dispatchers.IO) {
        if (!packIdRegex.matches(packId)) return@withContext
        packLocks.getOrPut(packId) { Mutex() }.withLock {
            evictionLock.withLock {
                File(root, packId).deleteRecursively()
            }
        }
        _revision.value++
    }

    /** Bytes currently cached. */
    suspend fun cachedBytes(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // ---------- manifest ----------

    /** Fetch the manifest; on any failure keep the disk-cached copy. */
    suspend fun refreshManifest(): List<PackInfo> = manifestLock.withLock { withContext(Dispatchers.IO) {
        if (!ChatyPackServer.DOWNLOADABLE_PACKS_ENABLED) return@withContext emptyList()
        val opBase = sourceBase
        val opHost = sourceHost ?: return@withContext manifest
        val body = try {
            httpGet("$opBase/chaty-packs/manifest.json", opHost, MAX_MANIFEST_BYTES)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
        // Validate BEFORE persisting: a bad manifest never reaches the disk cache.
        val parsed = body?.let { runCatching { parseManifest(it) }.getOrNull() }
        if (body != null && parsed != null) {
            runCatching {
                root.mkdirs()
                // Unique tmp per refresh: concurrent refreshes never share a file.
                val tmp = File(root, "manifest-${System.nanoTime()}.tmp")
                tmp.writeText(body)
                check(tmp.renameTo(manifestCache)) { "Cannot persist pack manifest" }
            }
            manifest = parsed
            _revision.value++
        } else if (manifest.isEmpty()) {
            runCatching { check(manifestCache.length() <= MAX_MANIFEST_BYTES); manifestCache.readText() }.getOrNull()
                ?.let { runCatching { parseManifest(it) }.getOrNull() }
                ?.let { manifest = it }
        }
        manifest
    } }

    /** Parse and validate; duplicate ids/names and invalid entries are dropped. */
    private fun parseManifest(body: String): List<PackInfo> {
        val out = mutableListOf<PackInfo>()
        val seenIds = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()
        val packs = JSONObject(body).optJSONArray("packs") ?: return emptyList()
        for (i in 0 until packs.length().coerceAtMost(MAX_PACKS)) {
            val p = packs.optJSONObject(i) ?: continue
            val id = p.optString("id")
            val version = p.optString("version", "")
            val name = p.optString("name").ifBlank { id }.take(48)
            if (!packIdRegex.matches(id) || !versionRegex.matches(version)) continue
            if (!seenIds.add(id) || !seenNames.add(name.lowercase())) continue // duplicates rejected
            val files = mutableListOf<PackFile>()
            val fileNames = mutableSetOf<String>()
            val fa = p.optJSONArray("files")
            if (fa != null) for (j in 0 until fa.length().coerceAtMost(MAX_FILES_PER_PACK)) {
                val f = fa.optJSONObject(j) ?: continue
                val path = f.optString("path")
                val sha = f.optString("sha256")
                val bytes = f.optLong("bytes", -1)
                if (validPath(path, id, version) && sha256Regex.matches(sha) &&
                    bytes in 1..MAX_FILE_BYTES && fileNames.add(path.substringAfterLast('/'))
                ) {
                    files.add(PackFile(path, sha, bytes))
                }
            }
            val a = p.optJSONObject("archive")
            val archive = a?.let {
                val path = it.optString("path")
                val sha = it.optString("sha256")
                val bytes = it.optLong("bytes", -1)
                if (validPath(path, id, version) && sha256Regex.matches(sha) &&
                    bytes in 1..MAX_ARCHIVE_BYTES
                ) PackArchive(path, sha, bytes) else null
            }
            if (files.isNotEmpty() && files.sumOf { it.bytes } <= MAX_CACHE_BYTES) out.add(PackInfo(id, name, version, files, archive))
        }
        return out
    }

    // ---------- download ----------

    /**
     * Download and install [packId]. One download per pack at a time (per-pack
     * mutex); eviction is serialized store-wide so it can never delete an
     * in-flight stage of another pack. The base URL and pinned host are captured
     * once per operation: a config change mid-download cannot mix servers.
     * Returns true only when the pack is fully verified and promoted.
     */
    suspend fun downloadPack(packId: String): Boolean = withContext(Dispatchers.IO) {
        downloadPackOnIo(packId)
    }

    private suspend fun downloadPackOnIo(packId: String): Boolean {
        if (!ChatyPackServer.DOWNLOADABLE_PACKS_ENABLED) return false
        if (!packIdRegex.matches(packId)) return false
        return packLocks.getOrPut(packId) { Mutex() }.withLock outer@{
            val opBase = sourceBase
            val opHost = sourceHost
            if (opBase.isBlank() || opHost == null) return@outer false
            val info = manifest.firstOrNull { it.id == packId }
                ?: refreshManifest().firstOrNull { it.id == packId }
            if (info == null) return@outer false
            val target = safePackDir(info.id, info.version) ?: return@outer false
            if (File(target, ".complete").isFile) return@outer true // already installed
            setProgress(packId, 0f)
            val stage = File(root, "$packId/.stage-$packId-${info.version}")
            try {
                evictionLock.withLock {
                    stage.deleteRecursively()
                    stage.mkdirs()
                }
                val ok = if (info.archive != null) downloadArchive(info, stage, opBase, opHost)
                    else downloadFiles(info, stage, opBase, opHost)
                if (!ok) return@outer false
                val allVerified = info.files.all { f ->
                    safeChild(stage, f.name)?.let { it.isFile && streamSha256(it).equals(f.sha256, ignoreCase = true) } == true
                }
                if (!allVerified) return@outer false
                evictionLock.withLock {
                    File(stage, ".complete").writeText(info.version)
                    // Only an incomplete stale same-version dir can exist here.
                    target.deleteRecursively()
                    if (!stage.renameTo(target)) return@outer false
                    File(root, info.id).listFiles { f ->
                        f.isDirectory && f.name != info.version && versionRegex.matches(f.name)
                    }?.forEach { it.deleteRecursively() }
                    evictIfNeeded()
                }
                _revision.value++
                true
            } catch (ce: CancellationException) {
                stage.deleteRecursively()
                throw ce
            } catch (_: Exception) {
                false
            } finally {
                setProgress(packId, null)
                evictionLock.withLock { evictIfNeeded() }
                if (!File(target, ".complete").isFile) evictionLock.withLock { stage.deleteRecursively() }
            }
        }
    }

    private suspend fun downloadArchive(info: PackInfo, stage: File, opBase: String, opHost: String): Boolean {
        val archive = info.archive ?: return false
        val zip = File(root, ".dl-${info.id}-${info.version}-${System.nanoTime()}.zip.tmp")
        return try {
            val hash = httpGetToFile("$opBase/chaty-packs/${archive.path}", opHost, zip, archive.bytes, MAX_ARCHIVE_BYTES) { done, total ->
                setProgress(info.id, done.toFloat() / total.coerceAtLeast(1))
            }
            if (!hash.equals(archive.sha256, ignoreCase = true)) return false
            var totalOut = 0L
            java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zin ->
                var entries = 0
                var entry = zin.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    if (++entries > MAX_FILES_PER_PACK) return false
                    if (!entry.isDirectory) {
                        val name = File(entry.name).name
                        val expected = info.files.firstOrNull { it.name == name }
                        val out = expected?.let { safeChild(stage, it.name) }
                        if (expected != null && out != null) {
                            val (bytes, sha) = streamToFile(zin, out, MAX_FILE_BYTES)
                            totalOut += bytes
                            if (totalOut > MAX_ZIP_TOTAL_BYTES) return false
                            if (totalOut > archive.bytes * MAX_ZIP_RATIO) return false
                            if (bytes != expected.bytes || !sha.equals(expected.sha256, ignoreCase = true)) return false
                        } else {
                            // Unknown entry: drained, bounded, counted toward the
                            // aggregate expanded bound, never written.
                            totalOut += drain(zin, MAX_FILE_BYTES)
                            if (totalOut > archive.bytes * MAX_ZIP_RATIO || totalOut > MAX_ZIP_TOTAL_BYTES) return false
                        }
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
            true
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        } finally {
            zip.delete()
        }
    }

    private suspend fun downloadFiles(info: PackInfo, stage: File, opBase: String, opHost: String): Boolean {
        val total = info.files.sumOf { it.bytes }.coerceAtLeast(1)
        var done = 0L
        for (f in info.files) {
            currentCoroutineContext().ensureActive()
            val out = safeChild(stage, f.name) ?: return false
            val tmp = File(stage, "${f.name}.tmp")
            val hash = httpGetToFile("$opBase/chaty-packs/${f.path}", opHost, tmp, f.bytes, MAX_FILE_BYTES) { d, _ ->
                setProgress(info.id, (done + d).toFloat() / total)
            }
            if (!hash.equals(f.sha256, ignoreCase = true)) return false
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) return false
            done += f.bytes
        }
        return true
    }

    // ---------- HTTP: https only, pinned host per operation, manual redirects ----------

    private fun openPinned(url: String, pinnedHost: String): java.net.HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) { hop ->
            val uri = java.net.URI(current)
            if (uri.scheme != "https" || uri.userInfo != null || uri.port != sourceUri.port) throw java.io.IOException("non-https refused")
            if (!uri.host.equals(pinnedHost, ignoreCase = true)) throw java.io.IOException("off-host refused")
            val conn = (uri.toURL().openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                useCaches = false
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null) throw java.io.IOException("redirect without location")
                if (hop == MAX_REDIRECTS) throw java.io.IOException("too many redirects")
                current = uri.resolve(location).toString()
            } else {
                if (code !in 200..299) {
                    conn.disconnect()
                    throw java.io.IOException("HTTP $code")
                }
                return conn
            }
        }
        throw java.io.IOException("unreachable")
    }

    private suspend fun httpGet(url: String, pinnedHost: String, maxBytes: Long): String {
        val conn = openPinned(url, pinnedHost)
        try {
            conn.inputStream.buffered().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw java.io.IOException("oversize")
                    out.write(buf, 0, n)
                }
                return out.toString(Charsets.UTF_8.name())
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Stream to file while hashing; aborts past [maxBytes]. Returns the hex sha256. */
    private suspend fun httpGetToFile(
        url: String,
        pinnedHost: String,
        out: File,
        expectedBytes: Long,
        maxBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ): String {
        if (expectedBytes > maxBytes) throw java.io.IOException("declared size over cap")
        val conn = openPinned(url, pinnedHost)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            conn.inputStream.buffered().use { input ->
                out.outputStream().buffered().use { fos ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        if (done > expectedBytes || done > maxBytes) throw java.io.IOException("oversize")
                        digest.update(buf, 0, n)
                        fos.write(buf, 0, n)
                        onProgress(done, expectedBytes)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (out.length() != expectedBytes) throw java.io.IOException("size mismatch")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Stream a zip entry to [out] while hashing; aborts past [maxBytes]. */
    private suspend fun streamToFile(
        input: java.io.InputStream,
        out: File,
        maxBytes: Long,
    ): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        out.outputStream().buffered().use { fos ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw java.io.IOException("entry oversize")
                digest.update(buf, 0, n)
                fos.write(buf, 0, n)
            }
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Drain up to [maxBytes]; returns the bytes actually drained. */
    private suspend fun drain(input: java.io.InputStream, maxBytes: Long): Long {
        var total = 0L
        val buf = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw java.io.IOException("entry oversize")
        }
        return total
    }

    private suspend fun streamSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun setProgress(packId: String, value: Float?) {
        synchronized(progressLock) {
            _downloads.value = if (value == null) _downloads.value - packId
            else _downloads.value + (packId to value.coerceIn(0f, 1f))
        }
    }

    /**
     * Evict least-recently-used WHOLE pack versions (including their .complete
     * marker) until under the cap, so an evicted pack is never shown installed.
     * Caller must hold [evictionLock]. Stage dirs (dot-prefixed) and packs with
     * an active download are never touched. The manifest cache is kept.
     */
    private fun evictIfNeeded(maxCacheBytes: Long = MAX_CACHE_BYTES) {
        val inFlight = _downloads.value.keys
        val versionDirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.flatMap { pack -> pack.listFiles { v -> v.isDirectory && versionRegex.matches(v.name) }?.toList() ?: emptyList() }
            ?: return
        var total = versionDirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        if (total <= maxCacheBytes) return
        versionDirs.sortedBy { it.lastModified() }.forEach { dir ->
            if (total <= maxCacheBytes) return
            if (dir.parentFile?.name in inFlight) return@forEach
            val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            dir.deleteRecursively()
            total -= size
        }
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 256L * 1024
        private const val MAX_PACKS = 64
        private const val MAX_FILES_PER_PACK = 5000
        private const val MAX_FILE_BYTES = 10L * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 50L * 1024 * 1024
        private const val MAX_ZIP_TOTAL_BYTES = 200L * 1024 * 1024
        private const val MAX_ZIP_RATIO = 100L
        private const val MAX_REDIRECTS = 2
        private const val MAX_CACHE_BYTES = 100L * 1024 * 1024

        @Volatile
        private var instance: ContentPackStore? = null

        /** One store per device: pack art is public, account-independent content. */
        fun get(context: Context): ContentPackStore = synchronized(this) {
            instance?.takeIf { it.sourceBase == ChatyPackServer.baseUrl }
                ?: ContentPackStore(context.applicationContext).also { instance = it }
        }
    }
}
