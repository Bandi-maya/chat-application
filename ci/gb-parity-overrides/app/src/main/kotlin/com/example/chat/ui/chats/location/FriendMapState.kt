package com.example.chat.ui.chats.location

/**
 * Friend map connection. The Chaty backend has no location-sharing endpoints yet, so the
 * only state today is [NotConnected]; Connected states arrive with the backend wiring.
 * No mock or preview friend data exists anywhere in this feature.
 */
sealed interface FriendMapConnection {
    /** Friend locations cannot be loaded, requested or shared. The UI must say so plainly. */
    data object NotConnected : FriendMapConnection
}

/**
 * Privacy choices the future backend must honor. Shown DISABLED in the UI so people can see
 * what is coming. Nothing here is persisted, transmitted or treated as an active setting;
 * per-friend privacy rules are still an open product decision for the backend phase.
 */
enum class FriendMapVisibilityPreview(val label: String, val description: String) {
    Hidden("Hidden from everyone", "Your location never appears on anyone's map."),
    AllFriends("All friends", "Every friend who you accept can see your location."),
    ChosenFriends("Chosen friends only", "Only the friends you pick can see your location."),
}

/**
 * The signed-in user's own pin. Strictly local to this device in this build: it is rendered
 * on their own map preview and is never sent, published or shared.
 */
data class FriendMapOwnPin(
    val coordinate: LocationCoordinate,
    val precise: Boolean,
)

/** Copy anchors kept in one place so the not-connected story stays honest and consistent. */
object FriendMapCopy {
    const val NOT_CONNECTED_TITLE = "No friends on the map yet"
    const val NOT_CONNECTED_BODY =
        "Friend locations need the Chaty backend, which isn't connected yet. When it's ready, only friends who choose to share their location will appear here."
    const val OWN_LOCATION_CAPTION = "Only you can see your pin on this map. Nothing is shared."
    const val VISIBILITY_INACTIVE_CAPTION =
        "Sharing controls turn on when the Chaty backend is connected. Your location is never shared automatically."
    const val MAP_STATUS_CHIP = "Friend locations not connected"
}
