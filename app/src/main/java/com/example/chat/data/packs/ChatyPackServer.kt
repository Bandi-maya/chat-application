package com.example.chat.data.packs

/**
 * Own-server content-pack endpoint (Bandi, 2026-11-14): all emoji/sticker pack
 * art is served from HIS server, never third-party CDNs. The host sets [baseUrl]
 * once at startup (application init). HTTPS only: a non-https or userinfo URL is
 * treated as NOT configured, and every picker/render falls back to the bundled
 * lightweight OpenMoji/system style.
 */
object ChatyPackServer {
    // Paused by the owner on 2026-11-14. Keep implementation/IDs/preferences recoverable for later.
    const val DOWNLOADABLE_PACKS_ENABLED = false
    @Volatile
    var baseUrl: String = ""
        set(value) {
            field = value.trim().trimEnd('/')
        }

    private val parsed: java.net.URI?
        get() = runCatching { java.net.URI(baseUrl) }.getOrNull()

    /** True only for an absolute https URL without userinfo. */
    val configured: Boolean
        get() = DOWNLOADABLE_PACKS_ENABLED && parsed?.let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null && it.query == null && it.fragment == null } == true

    /** Host that pack traffic is pinned to; redirects off this host are refused. */
    val pinnedHost: String? get() = if (configured) parsed?.host else null

    fun manifestUrl(): String? =
        if (configured) "$baseUrl/chaty-packs/manifest.json" else null

    /** [path] is the server-relative path carried by the manifest (e.g. "ios_animated/1/pack.zip"). */
    fun fileUrl(path: String): String? =
        if (configured) "$baseUrl/chaty-packs/$path" else null
}
