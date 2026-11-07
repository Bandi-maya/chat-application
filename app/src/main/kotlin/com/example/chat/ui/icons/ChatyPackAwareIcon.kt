package com.example.chat.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Keeps legacy model/callback APIs intact while resolving known meanings through pack selection.
 * Unknown meanings retain their existing vector until original per-pack art is supplied and previewed.
 * This is not a completeness claim and never substitutes a wrong-meaning glyph.
 */
@Composable
fun ChatyPackAwareIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) {
    val role = ChatyMaterialIconRoles.resolve(imageVector.name)
    if (role != null) {
        ChatyIcon(role, modifier = modifier, color = tint, semanticLabel = contentDescription)
    } else {
        androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint)
    }
}
