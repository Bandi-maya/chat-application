package com.example.chat.ui.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.templates.AppearanceVariantController
import com.example.chat.ui.kit.ChatyAppBar
import com.example.chat.ui.kit.ChatySingleChoiceModal
import com.example.chat.ui.settings.ChatyChoiceTile
import com.example.chat.ui.settings.templates.APPEARANCE_SAVE_ERROR
import com.example.chat.ui.settings.templates.appearanceOverviewChips
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

private enum class PreviewKind { typography, entryMotion, exitMotion }

@Composable
private fun rememberAppearanceVersion(controller: AppearanceVariantController): Int {
    var version by remember(controller) { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val l: () -> Unit = { version += 1 }
        controller.addListener(l)
        onDispose { controller.removeListener(l) }
    }
    return version
}

/**
 * Port of lib/features/settings/appearance/universal_appearance_screen.dart
 * ("Typography & Motion"). The Dart screen also required a
 * ChatyPreferencesController constructor param but never read it; the port
 * drops that dead param. Saves go through AppearanceVariantController's
 * persist-then-publish setters; a failed save keeps the previous choice and
 * surfaces the Dart snackbar text via [onMessage].
 *
 * Dart wrapped each option in ChatyChoiceTile(requireApply: true,
 * previewBuilder: ...); the Kotlin primitive hosts the modal at the call site,
 * so each section is a ChatyChoiceTile(requireApply) row plus a hosted
 * ChatySingleChoiceModal carrying the same previewBuilder.
 */
@Composable
fun UniversalAppearanceScreen(
    controller: AppearanceVariantController,
    onBack: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    rememberAppearanceVersion(controller)
    var openSection by remember { mutableStateOf<PreviewKind?>(null) }

    // Dart _saveAppearanceChoice: unawaited save, snackbar on failure.
    fun save(save: suspend (String) -> Unit, value: String) {
        scope.launch {
            try {
                save(value)
            } catch (t: Throwable) {
                onMessage(APPEARANCE_SAVE_ERROR)
            }
        }
    }

    Column(Modifier.background(colors.background)) {
        ChatyAppBar(
            title = "Typography & Motion",
            onBack = onBack,
            backgroundColor = colors.surface,
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            AppearanceOverview(controller)
            Spacer(Modifier.height(18.dp))
            Text(
                "Text Scaling & Motion Architecture",
                fontSize = 16.sp,
                fontWeight = FontWeight.W800,
                color = colors.foreground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Fine-tune system-wide typography density and animated page transition curves.",
                fontSize = 12.sp,
                lineHeight = 16.8.sp,
                color = colors.foregroundSecondary,
            )
            Spacer(Modifier.height(12.dp))
            VariantSection(
                title = "Typography style",
                subtitle = "Text density and scale across Chaty",
                value = controller.typographyStyle,
                options = AppearanceVariantController.typographyStyles,
                onOpenModal = { openSection = PreviewKind.typography },
            )
            VariantSection(
                title = "Screen entry animation",
                subtitle = "Motion applied when navigating forward into a screen",
                value = controller.entryAnimation,
                options = AppearanceVariantController.entryAnimations,
                onOpenModal = { openSection = PreviewKind.entryMotion },
            )
            VariantSection(
                title = "Screen exit animation",
                subtitle = "Motion applied when dismissing or popping a screen",
                value = controller.exitAnimation,
                options = AppearanceVariantController.exitAnimations,
                onOpenModal = { openSection = PreviewKind.exitMotion },
            )
        }
    }

    openSection?.let { kind ->
        val (title, value, options, icon) = when (kind) {
            PreviewKind.typography -> SectionSpec(
                "Typography style", controller.typographyStyle,
                AppearanceVariantController.typographyStyles, Icons.Rounded.TextFields,
            )
            PreviewKind.entryMotion -> SectionSpec(
                "Screen entry animation", controller.entryAnimation,
                AppearanceVariantController.entryAnimations, Icons.Rounded.Login,
            )
            PreviewKind.exitMotion -> SectionSpec(
                "Screen exit animation", controller.exitAnimation,
                AppearanceVariantController.exitAnimations, Icons.Rounded.Logout,
            )
        }
        ChatySingleChoiceModal(
            title = title,
            value = value,
            options = options,
            labelBuilder = { it },
            previewBuilder = { option -> VariantPreview(kind, icon, option) },
            onApply = { chosen ->
                openSection = null
                when (kind) {
                    PreviewKind.typography -> save(controller::setTypographyStyle, chosen)
                    PreviewKind.entryMotion -> save(controller::setEntryAnimation, chosen)
                    PreviewKind.exitMotion -> save(controller::setExitAnimation, chosen)
                }
            },
            onDismiss = { openSection = null },
        )
    }
}

private data class SectionSpec(
    val title: String,
    val value: String,
    val options: List<String>,
    val icon: ImageVector,
)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun AppearanceOverview(controller: AppearanceVariantController) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceSecondary, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Motion & Typography Profile",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = colors.foreground,
                )
                Text(
                    "Custom configured typography scale and responsive curves",
                    fontSize = 12.sp,
                    color = colors.foregroundSecondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            appearanceOverviewChips(
                controller.typographyStyle,
                controller.entryAnimation,
                controller.exitAnimation,
            ).forEach { label ->
                Text(
                    label,
                    fontSize = 11.sp,
                    color = colors.foregroundSecondary,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier
                        .background(colors.surfaceElevated.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun VariantSection(
    title: String,
    subtitle: String,
    value: String,
    options: List<String>,
    onOpenModal: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .padding(top = 14.dp)
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
    ) {
        ChatyChoiceTile(
            title = title,
            subtitle = subtitle,
            options = options,
            selectedOption = value,
            optionLabel = { it },
            onSelected = {},
            requireApply = true,
            onOpenModal = onOpenModal,
        )
    }
}

/** Dart _buildPreview: icon + label in a primary-tinted pill. The Dart
 *  AnimatedContainer only animates between drafts while color/padding are
 *  constant, so a static container is behaviorally identical. */
@Composable
private fun VariantPreview(kind: PreviewKind, icon: ImageVector, option: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .width(180.dp)
            .heightIn(min = 52.dp)
            .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (kind == PreviewKind.typography) "Aa · $option" else option,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.foreground,
            fontWeight = FontWeight.W700,
        )
    }
}
