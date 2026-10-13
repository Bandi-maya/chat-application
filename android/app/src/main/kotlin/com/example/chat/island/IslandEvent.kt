package com.example.chat.island

import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

sealed interface IslandActivity {
    val key: String
    val priority: Int
}

enum class PrivacyLevel {
    PUBLIC,
    SENSITIVE_CONCEALED,
    FULLY_HIDDEN,
}

data class MessageActivity(
    override val key: String,
    val packageName: String,
    val appIcon: Drawable?,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val contentIntent: PendingIntent? = null,
    val actions: List<IslandActionItem> = emptyList(),
    val replyAction: IslandReplyActionItem? = null,
    val privacy: PrivacyLevel = PrivacyLevel.PUBLIC,
    override val priority: Int = 300,
) : IslandActivity

data class CallActivity(
    override val key: String,
    val packageName: String,
    val callerName: String,
    val callState: String, // e.g. "Ringing", "Ongoing"
    val chronometerBase: Long? = null,
    val appIcon: Drawable? = null,
    val contentIntent: PendingIntent? = null,
    val actions: List<IslandActionItem> = emptyList(),
    override val priority: Int = 100, // Highest
) : IslandActivity

data class MediaActivity(
    override val key: String,
    val sessionToken: String,
    val title: String,
    val artist: String,
    val art: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val lastUpdateTimeMs: Long = System.currentTimeMillis(),
    val controllerPackage: String = "",
    override val priority: Int = 400,
) : IslandActivity

data class ChargingActivity(
    override val key: String = "system:charging",
    val percent: Int,
    val isCharging: Boolean,
    val isFast: Boolean,
    val pluggedType: String,
    override val priority: Int = 500,
) : IslandActivity

data class ChatyLiveActivity(
    override val key: String,
    val title: String,
    val subtitle: String,
    val iconKey: String? = null,
    override val priority: Int = 200,
) : IslandActivity

data class IslandActionItem(
    val title: String,
    val pendingIntent: PendingIntent,
)

data class IslandReplyActionItem(
    val title: String,
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
)

enum class IslandMode {
    HIDDEN,
    MINIMAL,
    COMPACT,
    EXPANDED_STACK,
    EXPANDED_MEDIA,
    EXPANDED_CALL,
    EXPANDED_CHARGING,
    REPLY,
}
