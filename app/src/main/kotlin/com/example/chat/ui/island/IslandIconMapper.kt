package com.example.chat.ui.island

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardAlt
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OnlinePrediction
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.chat.data.realtime.EventNotificationIcon

/** Maps the enum-erased event-notification icon back to the exact Material
 *  icon the Dart call sites pass into ChatyNotificationService
 *  .triggerEventNotification (IconData param). Sources:
 *  - default param: notifications_rounded (notification_service.dart:42)
 *  - _iconForMessageType (rich_chat_realtime_service.dart:182-198):
 *    image/videocam/mic/attach_file/location_on/person/check_circle_outline,
 *    default chat_bubble_rounded
 *  - call sites: online_prediction_rounded (:319), keyboard_alt_outlined
 *    (:384), mic_none_rounded (:401), undo_rounded (:466),
 *    person_outline_rounded (:664), auto_awesome_rounded
 *    (status_service.dart:116), history_toggle_off_rounded (:148).
 *  All compose names verified against existing CI-green repo usage. */
object IslandIconMapper {
    fun map(icon: EventNotificationIcon): ImageVector = when (icon) {
        EventNotificationIcon.notifications -> Icons.Rounded.Notifications
        EventNotificationIcon.image -> Icons.Rounded.Image
        EventNotificationIcon.videocam -> Icons.Rounded.Videocam
        EventNotificationIcon.mic -> Icons.Rounded.Mic
        EventNotificationIcon.attachFile -> Icons.Rounded.AttachFile
        EventNotificationIcon.locationOn -> Icons.Rounded.LocationOn
        EventNotificationIcon.person -> Icons.Rounded.Person
        EventNotificationIcon.checkCircleOutline -> Icons.Rounded.CheckCircleOutline
        EventNotificationIcon.chatBubble -> Icons.Rounded.ChatBubble
        EventNotificationIcon.onlinePrediction -> Icons.Rounded.OnlinePrediction
        EventNotificationIcon.keyboardAlt -> Icons.Outlined.KeyboardAlt
        EventNotificationIcon.micNone -> Icons.Rounded.MicNone
        EventNotificationIcon.undo -> Icons.Rounded.Undo
        EventNotificationIcon.personOutline -> Icons.Rounded.PersonOutline
        EventNotificationIcon.autoAwesome -> Icons.Rounded.AutoAwesome
        EventNotificationIcon.historyToggleOff -> Icons.Rounded.HistoryToggleOff
    }
}
