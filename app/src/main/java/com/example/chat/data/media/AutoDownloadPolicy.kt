package com.example.chat.data.media

/** Per-network auto-download rules (media matrix, WhatsApp parity + Chaty
 *  data saver). Pure policy: JVM-testable, no Android imports.
 *
 *  Defaults mirror WhatsApp: Wi-Fi downloads everything, cellular downloads
 *  images/audio only, roaming downloads nothing. Chaty additions:
 *   - a data-saver switch that caps cellular auto-downloads by size;
 *   - per-conversation overrides (always / never / follow defaults).
 */
object AutoDownloadPolicy {

    enum class Network { WIFI, CELLULAR, ROAMING, OFFLINE }
    enum class MediaKind { IMAGE, AUDIO, VIDEO, DOCUMENT }
    enum class Override { ALWAYS, NEVER, DEFAULT }

    data class Rules(
        val wifiKinds: Set<MediaKind> = setOf(MediaKind.IMAGE, MediaKind.AUDIO, MediaKind.VIDEO, MediaKind.DOCUMENT),
        val cellularKinds: Set<MediaKind> = setOf(MediaKind.IMAGE, MediaKind.AUDIO),
        val roamingKinds: Set<MediaKind> = emptySet(),
        /** Data saver: on cellular, auto-download only up to this many bytes. */
        val dataSaverEnabled: Boolean = false,
        val dataSaverMaxBytes: Long = 512 * 1024,
    )

    fun shouldAutoDownload(
        network: Network,
        kind: MediaKind,
        sizeBytes: Long?,
        rules: Rules = Rules(),
        conversationOverride: Override = Override.DEFAULT,
    ): Boolean {
        if (network == Network.OFFLINE) return false
        when (conversationOverride) {
            Override.ALWAYS -> return true
            Override.NEVER -> return false
            Override.DEFAULT -> Unit
        }
        val allowed = when (network) {
            Network.WIFI -> rules.wifiKinds
            Network.CELLULAR -> rules.cellularKinds
            Network.ROAMING -> rules.roamingKinds
            Network.OFFLINE -> emptySet()
        }
        if (kind !in allowed) return false
        if (network != Network.WIFI && rules.dataSaverEnabled) {
            val size = sizeBytes ?: return false // unknown size: never auto-download on saver
            if (size > rules.dataSaverMaxBytes) return false
        }
        return true
    }
}
