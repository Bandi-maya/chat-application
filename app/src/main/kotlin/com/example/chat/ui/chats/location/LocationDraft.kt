package com.example.chat.ui.chats.location

/** Validated WGS84 coordinates. Deliberately has no service or Android dependencies. */
data class LocationCoordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
    }
}

enum class LocationSource { manualPin, currentFix }
enum class LocationPrecision { approximate, precise }

/** A draft, never a message. Creating or moving it must not perform a send. */
data class LocationDraft(
    val coordinate: LocationCoordinate,
    val source: LocationSource,
    val accuracyMeters: Double? = null,
    val precision: LocationPrecision? = null,
) {
    init {
        require(accuracyMeters == null || (accuracyMeters.isFinite() && accuracyMeters >= 0.0))
        require(source != LocationSource.manualPin || (accuracyMeters == null && precision == null)) {
            "A manual pin must not claim device accuracy or permission precision"
        }
        require(source != LocationSource.currentFix || (accuracyMeters != null && precision != null)) {
            "A current fix must describe its accuracy and permission precision"
        }
    }

    /** Compatibility fields only. Integrator must carry these INSIDE the encrypted message payload. */
    fun messageMetadata(): Map<String, Any?> = buildMap {
        put("latitude", coordinate.latitude)
        put("longitude", coordinate.longitude)
        put("location_schema_version", 1)
        put("location_source", source.name)
        accuracyMeters?.let { put("accuracy_meters", it) }
        precision?.let { put("location_precision", it.name) }
    }
}

/** Recheck on every locate tap/resume. Never infer permanent denial before a request was made. */
data class ForegroundLocationAccess(
    val fineGranted: Boolean,
    val coarseGranted: Boolean,
    val requestedBefore: Boolean,
    val shouldShowRationale: Boolean,
    val locationServicesEnabled: Boolean,
)

enum class LocateAction { usePreciseFix, useApproximateFix, requestForeground, explainRationale, offerAppSettings, offerLocationSettings }

fun nextLocateAction(access: ForegroundLocationAccess): LocateAction = when {
    !access.fineGranted && !access.coarseGranted && access.shouldShowRationale -> LocateAction.explainRationale
    !access.fineGranted && !access.coarseGranted && access.requestedBefore -> LocateAction.offerAppSettings
    !access.fineGranted && !access.coarseGranted -> LocateAction.requestForeground
    !access.locationServicesEnabled -> LocateAction.offerLocationSettings
    access.fineGranted -> LocateAction.usePreciseFix
    else -> LocateAction.useApproximateFix
}

/** One-shot send token bound to an exact preview. A changed pin invalidates confirmation. */
class LocationSendGate {
    private var draft: LocationDraft? = null
    private var revision = 0L
    private var confirmedRevision: Long? = null
    private var consumed = false

    fun select(value: LocationDraft) {
        draft = value
        revision++
        confirmedRevision = null
        consumed = false
    }

    fun preview(): LocationDraft? = draft

    /** Only bind this to an explicit Send tap in the preview UI. */
    fun confirmSend() {
        check(draft != null) { "Choose a location first" }
        confirmedRevision = revision
    }

    /** Returns at most once. Sending/queueing failures remain the normal transport's responsibility. */
    fun consumeConfirmed(): LocationDraft? {
        if (consumed || confirmedRevision != revision) return null
        val selected = draft ?: return null
        consumed = true
        return selected
    }

    fun cancel() {
        draft = null
        revision++
        confirmedRevision = null
        consumed = false
    }
}
