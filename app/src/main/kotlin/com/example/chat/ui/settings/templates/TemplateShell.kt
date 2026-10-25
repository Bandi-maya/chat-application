package com.example.chat.ui.settings.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind

/** Port of ui/core/templates/template_shell.dart TemplateShellHeader: the
 *  single, measured header row owned by a structural template shell. Feature
 *  screens provide their content below this header; they must not position
 *  global navigation controls over that content. Navigation, title and
 *  actions stay in explicit slots so drawer controls and long titles never
 *  compete for the same pixels. UNCOMPILED (Compose; owner CI verifies). */
@Composable
fun TemplateShellHeader(
    title: String,
    navigation: @Composable () -> Unit,
    actions: List<@Composable () -> Unit> = emptyList(),
    backgroundColor: Color? = null,
    foregroundColor: Color? = null,
    dividerColor: Color? = null,
) {
    val effectiveBackground = backgroundColor ?: MaterialTheme.colorScheme.surface
    val effectiveForeground = foregroundColor ?: MaterialTheme.colorScheme.onSurface
    val effectiveDivider = dividerColor ?: MaterialTheme.colorScheme.outlineVariant

    Surface(color = effectiveBackground) {
        // SafeArea(bottom: false) equivalent: the caller places this header
        // inside its own status-bar padding; the 0.8dp bottom border is the
        // Dart DecoratedBox underline.
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 0.8.dp.toPx()
                    drawLine(
                        color = effectiveDivider,
                        start = Offset(0f, size.height - stroke / 2f),
                        end = Offset(size.width, size.height - stroke / 2f),
                        strokeWidth = stroke,
                    )
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    MinTouchTarget { navigation() }
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = effectiveForeground,
                        fontWeight = FontWeight.W700,
                    ),
                )
                if (actions.isNotEmpty()) {
                    Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (action in actions) {
                            MinTouchTarget { action() }
                        }
                    }
                }
                Box(Modifier.padding(end = 8.dp))
            }
        }
    }
}

/** Guarantees any caller-provided control meets the 48dp accessibility
 *  floor. The shell owns this contract so feature screens cannot regress
 *  touch-target size by supplying compact controls such as a default
 *  Material IconButton. */
@Composable
private fun MinTouchTarget(child: @Composable () -> Unit) {
    Box(Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), contentAlignment = Alignment.Center) {
        child()
    }
}
