package com.example.chat.data.media

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Seam for [MediaDownloadManager] so manager behavior (claims, resume, purge) is testable without a network. */
interface PartDownloader {
    suspend fun download(url: String, part: File, isCurrent: () -> Boolean, onProgress: (done: Long, total: Long?, bytesPerSecond: Long) -> Unit): ResumableMediaDownloader.Result
    fun discard(part: File)
}

/**
 * Resumable download of an already authorized media URL into an account-private ".part" file.
 *  - https only, no redirects, hard byte bound;
 *  - resumes with `Range: bytes=<partLen>-` + `If-Range: <strong etag>` ONLY when a strong ETag was saved beside the
 *    part; otherwise the part is discarded and the download starts at 0. [ResumePlan] decides append / restart /
 *    complete and only appends when the server proves the same bytes (strong equal ETag + exact offset);
 *  - [isCurrent] is checked on every chunk: when the account session or media policy changed, the part file is
 *    deleted and the download stops;
 *  - pause = cancel the coroutine, part file kept; user cancel = [discard]; nothing here logs URLs.
 * The finished part file is NOT trusted media: the caller hands it to the encrypted store, then deletes it.
 * One writer per part file is the caller's job ([MediaDownloadManager] serializes runs).
 */
class ResumableMediaDownloader internal constructor(
    private val client: OkHttpClient,
    private val maxBytes: Long,
    private val headers: suspend () -> Map<String, String>,
    private val requireHttps: Boolean,
) : PartDownloader {
    constructor(client: OkHttpClient, maxBytes: Long, headers: suspend () -> Map<String, String> = { emptyMap() }) :
        this(client, maxBytes, headers, true)

    data class Result(val file: File, val totalBytes: Long, val etag: String?)

    override suspend fun download(
        url: String,
        part: File,
        isCurrent: () -> Boolean,
        onProgress: (done: Long, total: Long?, bytesPerSecond: Long) -> Unit,
    ): Result {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || (requireHttps && !parsed.isHttps)) throw IOException("unsupported url")
        val extra = headers()
        return withContext(Dispatchers.IO) {
            part.parentFile?.mkdirs()
            val etagFile = File(part.path + ".etag")
            var savedEtag = etagFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            // No proof available that the part belongs to the same bytes: do not even try a Range request.
            if (part.exists() && !ResumePlan.canTryResume(part.length(), savedEtag)) { discard(part); savedEtag = null }
            var attempt = 0
            while (true) {
                ensureActive()
                val partLen = if (part.exists()) part.length() else 0L
                val req = Request.Builder().url(parsed).get().apply {
                    extra.forEach { (k, v) -> header(k, v) }
                    if (partLen > 0) { header("Range", "bytes=$partLen-"); savedEtag?.let { header("If-Range", it) } }
                }.build()
                val outcome = client.newBuilder().followRedirects(false).followSslRedirects(false).build().newCall(req).execute().use { resp ->
                    val newEtag = resp.header("ETag")
                    val action = ResumePlan.decide(partLen, resp.code, resp.header("Content-Range"),
                        resp.body?.contentLength()?.takeIf { it >= 0 }, savedEtag, newEtag)
                    var total: Long? = null
                    when (action) {
                        is ResumePlan.Action.Fail -> throw DownloadFailure(action.reason)
                        ResumePlan.Action.AlreadyComplete -> return@use Result(part, partLen, savedEtag)
                        ResumePlan.Action.RestartFresh -> { discard(part); savedEtag = null; return@use null }
                        is ResumePlan.Action.Restart -> { discard(part); total = action.total }
                        is ResumePlan.Action.Append -> total = action.total
                    }
                    if (total != null && total > maxBytes) { discard(part); throw DownloadFailure(MediaDownloadState.FailReason.TOO_LARGE) }
                    // Only a strong validator is persisted; without one a later attempt restarts instead of resuming.
                    if (ResumePlan.isStrong(newEtag)) etagFile.writeText(newEtag!!) else etagFile.delete()
                    val body = resp.body ?: throw IOException("empty body")
                    var done = if (action is ResumePlan.Action.Append) partLen else 0L
                    val base = done; val startNs = System.nanoTime(); var lastEmit = 0L
                    RandomAccessFile(part, "rw").use { raf ->
                        raf.seek(done)
                        val buf = ByteArray(32 * 1024)
                        body.byteStream().use { input ->
                            while (true) {
                                ensureActive()
                                if (!isCurrent()) { discard(part); throw CancellationException("session changed") }
                                val n = input.read(buf)
                                if (n < 0) break
                                if (done + n > maxBytes) { discard(part); throw DownloadFailure(MediaDownloadState.FailReason.TOO_LARGE) }
                                raf.write(buf, 0, n); done += n
                                val now = System.nanoTime()
                                if (now - lastEmit >= 100_000_000L) {
                                    lastEmit = now
                                    onProgress(done, total, (done - base) * 1_000_000_000L / (now - startNs).coerceAtLeast(1L))
                                }
                            }
                        }
                    }
                    if (total != null && done != total) throw IOException("incomplete")   // keep part, resumable
                    onProgress(done, total ?: done, 0)
                    Result(part, done, if (ResumePlan.isStrong(newEtag)) newEtag else null)
                }
                if (outcome != null) return@withContext outcome
                if (++attempt >= 2) throw DownloadFailure(MediaDownloadState.FailReason.NETWORK)   // never loop on a bad server
            }
            error("unreachable")
        }
    }

    /** User cancel / logout / policy change / unproven resume: remove partial bytes and validator. */
    override fun discard(part: File) { part.delete(); File(part.path + ".etag").delete() }

    class DownloadFailure(val reason: MediaDownloadState.FailReason) : IOException(reason.name)
}
