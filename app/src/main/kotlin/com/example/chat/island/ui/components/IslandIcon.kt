package com.example.chat.island.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.icons.ChatyMaterialIconRoles

/** Resolves island affordances through the same selected icon pack as Chaty. */
@Composable
fun IslandIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) {
    val name = imageVector.name.substringAfterLast('.')
    val role = when (name) {
        "LibraryMusic" -> ChatyIconId.musicNote
        "Headset" -> ChatyIconId.headphones
        "HourglassBottom" -> ChatyIconId.timer
        "NearMe" -> ChatyIconId.navigation
        "LocalShipping", "DeliveryDining" -> ChatyIconId.location
        "BatteryAlert" -> ChatyIconId.warning
        "BatteryStd" -> ChatyIconId.bolt
        "Bluetooth", "BluetoothDisabled" -> ChatyIconId.bluetooth
        "NotificationsPaused" -> ChatyIconId.notificationsOff
        "CenterFocusStrong" -> ChatyIconId.locate
        "Android" -> ChatyIconId.devices
        "RadioButtonUnchecked" -> ChatyIconId.minusCircle
        "FlashOn" -> ChatyIconId.cameraFlashOn
        "Vibration" -> ChatyIconId.ringVolume
        "SkipNext" -> ChatyIconId.arrowRight
        "SkipPrevious" -> ChatyIconId.back
        else -> ChatyMaterialIconRoles.resolve(imageVector.name)
    }
    requireNotNull(role) { "Missing Chaty icon role: $name" }
    ChatyIcon(role, modifier = modifier, color = tint, semanticLabel = contentDescription)
}
