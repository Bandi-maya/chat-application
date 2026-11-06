package com.example.chat.data.links

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/** Chaty #34: client-side OpenGraph link previews. No Chaty backend, no paid
 *  service. Only public http(s) destinations are fetched: PublicUrlPolicy
 *  gates the initial URL and every redirect hop (manual redirects, max 5),
 *  and the client's own Dns re-filters every connect-time answer so a hostile
 *  DNS cannot swap a public answer for a private one after the pre-check
 *  (closes the TOCTOU gap). Pages are capped at 256 KB; preview images are
 *  fetched as BYTES through this same guarded client (2 MB cap, an image
 *  content-type required) so a public og:image cannot redirect an unguarded
 *  loader into a private/local target - the card renders decoded bytes,
 *  never a remote URL. Results and failures are cached in memory. */
class LinkPreviewService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        // Redirects are followed manually so every hop is re-validated.
        .followRedirects(false)
        .followSslRedirects(false)
        // Connect-time guard: every DNS answer OkHttp actually dials must be
        // public, regardless of what the pre-check saw.
        .dns(PublicDns())
        .build(),
) {

    /** OkHttp Dns that resolves through the system resolver and rejects the
     *  whole lookup when ANY answer is non-public. This is the connect-time
     *  enforcement layer; PublicUrlPolicy remains the request-time gate.
     *  The resolver is injectable so the guard is JVM-testable. */
    class PublicDns(
        private val resolver: (String) -> List<InetAddress> = { hostname -> Dns.SYSTEM.lookup(hostname) },
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val answers = resolver(hostname)
            if (answers.isEmpty()) throw UnknownHostException("no addresses for $hostname")
            for (address in answers) {
                if (!PublicUrlPolicy.isPublicAddress(address)) {
                    throw UnknownHostException("non-public address blocked for $hostname")
                }
            }
            return answers
        }
    }

    /** Byte-budget LRU for encoded image data: evicts least-recently-used
     *  entries when the total encoded bytes exceed the budget, so the cache
     *  cannot grow to hundreds of MB. Pure Kotlin: JVM-testable. */
    internal class ByteBudgetCache(private val maxBytes: Int) {
        private val lock = Any()
        private var bytes = 0
        private val map = LinkedHashMap<String, ByteArray>(16, 0.75f, true)

        fun get(key: String): ByteArray? = synchronized(lock) { map[key] }

        fun put(key: String, value: ByteArray) {
            if (value.size > maxBytes) return // never cache a single oversize entry
            synchronized(lock) {
                map.remove(key)?.let { bytes -= it.size }
                map[key] = value
                bytes += value.size
                val it = map.entries.iterator()
                while (bytes > maxBytes && it.hasNext()) {
                    val eldest = it.next()
                    it.remove()
                    bytes -= eldest.value.size
                }
            }
        }

        fun clear() = synchronized(lock) {
            map.clear()
            bytes = 0
        }

        internal fun totalBytes(): Int = synchronized(lock) { bytes }
    }

    data class Preview(
        val url: String,
        val title: String?,
        val description: String?,
        val imageUrl: String?,
        val siteName: String?,
    )

    private data class Fetched(
        val finalUrl: String,
        val bytes: ByteArray,
        val contentType: String?,
    )

    private sealed class Entry {
        data class Ready(val preview: Preview) : Entry()
        data class Failed(val atMillis: Long) : Entry()
    }

    private sealed class Hop {
        data class RedirectTo(val next: String?) : Hop()
        data class Page(val fetched: Fetched) : Hop()
    }

    private val cache = ConcurrentHashMap<String, Entry>()
    private val imageCache = ByteBudgetCache(IMAGE_CACHE_BYTES)

    suspend fun preview(url: String): Preview = withContext(Dispatchers.IO) {
        when (val hit = cache[url]) {
            is Entry.Ready -> return@withContext hit.preview
            is Entry.Failed -> if (System.currentTimeMillis() - hit.atMillis < FAIL_TTL_MS) {
                throw IOException("Preview unavailable")
            }
            null -> Unit
        }
        try {
            val fetched = fetch(url)
            if (cache.size > 300) cache.clear()
            cache[url] = Entry.Ready(fetched)
            fetched
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            cache[url] = Entry.Failed(System.currentTimeMillis())
            throw IOException("Preview unavailable", e)
        }
    }

    /** Loads a preview image as bytes through the same validated-hop client.
     *  Callers decode the bytes locally; remote image URLs never reach an
     *  unguarded loader. Throws IOException when blocked or unavailable.
     *  Encoded results are cached under a byte budget. */
    suspend fun image(url: String): ByteArray = withContext(Dispatchers.IO) {
        imageCache.get(url)?.let { return@withContext it }
        val fetched = getValidated(url, "image/*", IMAGE_MAX_BYTES)
        val type = fetched.contentType
        if (type != null && !type.startsWith("image/")) {
            throw IOException("not an image: $type")
        }
        imageCache.put(url, fetched.bytes)
        fetched.bytes
    }

    private fun fetch(url: String): Preview {
        val page = getValidated(url, "text/html", MAX_BYTES)
        val content = page.bytes.toString(Charsets.UTF_8)
        return gateImage(parse(page.finalUrl, content))
    }

    /** Validated GET: checks the initial URL, follows up to MAX_REDIRECTS
     *  manually with a fresh PublicUrlPolicy check on each hop, enforces the
     *  byte cap and rejects bodies whose declared length exceeds it. The
     *  client Dns re-checks every answer again at connect time. */
    private fun getValidated(url: String, accept: String, maxBytes: Int): Fetched {
        var current = PublicUrlPolicy.requirePublicHttpUrl(url)
        var redirects = 0
        while (true) {
            val request = Request.Builder().url(current).get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .build()
            val hop = client.newCall(request).execute().use { response ->
                when {
                    response.code in 300..399 ->
                        Hop.RedirectTo(response.header("Location"))
                    !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                    else -> {
                        val body = response.body ?: throw IOException("empty body")
                        if (body.contentLength() > maxBytes) throw IOException("too large")
                        Hop.Page(
                            Fetched(
                                finalUrl = current,
                                bytes = body.byteStream().readNBytes(maxBytes),
                                contentType = body.contentType()?.toString()?.substringBefore(';')?.trim(),
                            ),
                        )
                    }
                }
            }
            when (hop) {
                is Hop.Page -> return hop.fetched
                is Hop.RedirectTo -> {
                    redirects++
                    if (redirects > MAX_REDIRECTS) throw IOException("too many redirects")
                    val target = hop.next ?: throw IOException("redirect without Location")
                    val next = resolve(current, target) ?: throw IOException("bad redirect target")
                    // Re-validate scheme and re-resolve DNS on every hop.
                    current = PublicUrlPolicy.requirePublicHttpUrl(next)
                }
            }
        }
    }

    /** Only advertise an image URL that itself passes the public-URL check
     *  (scheme + DNS). The actual load goes through image(), never Coil. */
    private fun gateImage(preview: Preview): Preview {
        val image = preview.imageUrl ?: return preview
        val allowed = runCatching { PublicUrlPolicy.requirePublicHttpUrl(image) }.isSuccess
        return if (allowed) preview else preview.copy(imageUrl = null)
    }

    internal fun parse(url: String, html: String): Preview {
        val head = html.substring(0, minOf(html.length, HEAD_CHARS))
        fun meta(property: String): String? {
            // Handles both attribute orders: property/content and name/content.
            val patterns = listOf(
                Regex("""<meta[^>]+(?:property|name)=["']$property["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE),
                Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+(?:property|name)=["']$property["']""", RegexOption.IGNORE_CASE),
            )
            return patterns.firstNotNullOfOrNull { it.find(head)?.groupValues?.get(1) }
                ?.let(::decodeEntities)?.takeIf { it.isNotBlank() }
        }
        val titleTag = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)?.let(::decodeEntities)
        val image = meta("og:image")?.let { resolve(url, it) }
        return Preview(
            url = url,
            title = meta("og:title") ?: titleTag,
            description = meta("og:description") ?: meta("description"),
            imageUrl = image,
            siteName = meta("og:site_name"),
        )
    }

    private fun resolve(base: String, maybeRelative: String): String? {
        if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) return maybeRelative
        return try {
            java.net.URI(base).resolve(maybeRelative).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeEntities(raw: String): String = raw
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .trim()

    private companion object {
        const val MAX_BYTES = 256 * 1024
        const val IMAGE_MAX_BYTES = 2 * 1024 * 1024
        const val IMAGE_CACHE_BYTES = 4 * 1024 * 1024
        const val HEAD_CHARS = 64 * 1024
        const val FAIL_TTL_MS = 60_000L
        const val MAX_REDIRECTS = 5
        const val USER_AGENT = "Chaty/1.0 (Android; link preview)"
    }
}

/** Process-wide shared instance; created on first use. */
val sharedLinkPreviewService: LinkPreviewService by lazy { LinkPreviewService() }
