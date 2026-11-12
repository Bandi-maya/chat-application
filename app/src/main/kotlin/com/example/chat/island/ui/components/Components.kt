package com.example.chat.island.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.chat.R
import com.example.chat.island.core.Trigger

private data class ActivityPresentation(val symbol: ImageVector, val description: Int)
private val presentations = mapOf(
    Trigger.Notifications to ActivityPresentation(Icons.Outlined.ChatBubbleOutline, R.string.hint_notifications),
    Trigger.Media to ActivityPresentation(Icons.Outlined.LibraryMusic, R.string.hint_media),
    Trigger.Calls to ActivityPresentation(Icons.Outlined.Phone, R.string.hint_calls),
    Trigger.Charging to ActivityPresentation(Icons.Outlined.Bolt, R.string.hint_charging),
    Trigger.Timer to ActivityPresentation(Icons.Outlined.HourglassBottom, R.string.hint_timer),
    Trigger.Devices to ActivityPresentation(Icons.Outlined.Headset, R.string.hint_devices),
    Trigger.Battery to ActivityPresentation(Icons.Outlined.BatteryAlert, R.string.hint_battery),
    Trigger.Toggles to ActivityPresentation(Icons.Outlined.Settings, R.string.hint_toggles),
    Trigger.Navigation to ActivityPresentation(Icons.Outlined.NearMe, R.string.hint_navigation),
    Trigger.Tracking to ActivityPresentation(Icons.Outlined.LocalShipping, R.string.hint_tracking),
    Trigger.Downloads to ActivityPresentation(Icons.Outlined.FileDownload, R.string.hint_downloads),
    Trigger.Quiet to ActivityPresentation(Icons.Outlined.NotificationsPaused, R.string.hint_quiet),
)
internal fun Trigger.icon(): ImageVector = presentations.getValue(this).symbol
internal fun Trigger.hint(): Int = presentations.getValue(this).description
