package com.example.chat.ui.kit

import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.chat.ui.theme.LocalAppColors

/**
 * Theme-token switch colors with a visible OFF state in light and dark: the thumb is the secondary
 * text token, the track is a tint of it, and the outline is a stronger tint, so thumb, track and
 * card stay distinct. Checked colors are passed in so each screen keeps its existing ON look.
 */
@Composable
fun chatySwitchColors(
    checkedThumbColor: Color = LocalAppColors.current.onPrimary,
    checkedTrackColor: Color = LocalAppColors.current.primary,
): SwitchColors {
    val c = LocalAppColors.current
    val offThumb = c.foregroundSecondary
    val offTrack = c.foregroundSecondary.copy(alpha = 0.22f)
    val offBorder = c.foregroundSecondary.copy(alpha = 0.7f)
    return SwitchDefaults.colors(
        checkedThumbColor = checkedThumbColor, checkedTrackColor = checkedTrackColor, checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = offThumb, uncheckedTrackColor = offTrack, uncheckedBorderColor = offBorder,
        disabledCheckedThumbColor = checkedThumbColor, disabledCheckedTrackColor = checkedTrackColor,
        disabledCheckedBorderColor = Color.Transparent,
        disabledUncheckedThumbColor = offThumb, disabledUncheckedTrackColor = offTrack,
        disabledUncheckedBorderColor = offBorder,
    )
}
