package com.example.chat.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.theme.LocalAppColors

private fun c(argb: Long): Color = Color(argb)

/**48dp clickable accessibility target around a24dp decorative chevron. */
@Composable
fun AuthBackButton(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag("auth-back").semantics { contentDescription = "Back" }) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back,
                semanticLabel = null,
                color = colors.foreground,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Rounded text input field with label and placeholder (AuthTextField). */
@Composable
fun AuthTextField(
    label: String,
    hintText: String,
    theme: ThemeConfig,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    obscureText: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    prefixIcon: (@Composable () -> Unit)? = null,
    suffixIcon: (@Composable () -> Unit)? = null,
    capsule: Boolean = false,
    microMotion: Boolean = capsule,
) {
    val colors = LocalAppColors.current
    val focus = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val focusTint by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) c(theme.accentColor) else colors.border,
        animationSpec = androidx.compose.animation.core.tween(if (reduced) 0 else com.example.chat.ui.kit.ChatyMotion.fastMs),
        label = "auth-focus-outline",
    )
    val floatingLabel: (@Composable () -> Unit)? = if (capsule) null else ({ Text(label) })
    Column(modifier = modifier) {
        Text(
            label,
            color = c(theme.primaryTextColor),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = if (capsule) AuthCapsuleTokens.controlHeight else 0.dp).testTag("auth-field-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
                .semantics { contentDescription = label },
            label = floatingLabel,
            placeholder = {
                Text(
                    hintText,
                    color = c(argbWithAlpha(theme.secondaryTextColor, 0.5)),
                    fontSize = 14.sp,
                )
            },
            visualTransformation = if (obscureText) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { if (!focus.moveFocus(FocusDirection.Next)) focus.clearFocus() }),
            isError = error != null,
            supportingText = error?.let { message -> {
                Row(verticalAlignment = Alignment.Top, modifier = if (capsule) Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite } else Modifier) {
                    if (capsule) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline, size = 18.dp, color = c(theme.dangerColor))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(message, color = c(theme.dangerColor), fontSize = 12.sp)
                }
            } },
            leadingIcon = prefixIcon,
            trailingIcon = suffixIcon,
            singleLine = true,
            interactionSource = interaction,
            shape = if (capsule) AuthCapsuleTokens.shape else RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c(theme.primaryTextColor),
                unfocusedTextColor = c(theme.primaryTextColor),
                focusedContainerColor = colors.inputFill,
                unfocusedContainerColor = colors.inputFill,
                errorContainerColor = colors.inputFill,
                focusedBorderColor = if (microMotion) focusTint else c(theme.accentColor),
                unfocusedBorderColor = colors.border,
                errorBorderColor = c(theme.dangerColor),
                cursorColor = c(theme.accentColor),
            ),
        )
    }
}

/** Solid primary rounded button (AuthPrimaryButton). */
@Composable
fun AuthPrimaryButton(
    text: String,
    theme: ThemeConfig,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    capsule: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (capsule) AuthCapsuleTokens.controlHeight else 52.dp),
        shape = if (capsule) AuthCapsuleTokens.shape else RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c(theme.accentColor),
            contentColor = c(theme.onAccentColor),
            disabledContainerColor = c(argbWithAlpha(theme.accentColor, 0.6)),
            disabledContentColor = c(theme.onAccentColor),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.2.dp,
                color = c(theme.onAccentColor),
            )
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        }
    }
}

/** Outlined button for secondary actions (AuthSecondaryButton). */
@Composable
fun AuthSecondaryButton(text: String, theme: ThemeConfig, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            c(argbWithAlpha(theme.secondaryTextColor, 0.65)),
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c(theme.primaryTextColor)),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    }
}

/** Divider with centered 'Or' (AuthOrDivider). */
@Composable
fun AuthOrDivider(theme: ThemeConfig) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = colors.divider)
        Text(
            "Or",
            color = c(theme.secondaryTextColor),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = colors.divider)
    }
}

/** Custom vector-style illustration for auth screens (AuthIllustration).
 *  type: 'welcome', 'forgot_password', 'otp'. */
@Composable
fun AuthIllustration(type: String, theme: ThemeConfig, height: Int = 180) {
    val colors = LocalAppColors.current
    val (mainIcon: ImageVector, badgeText: String) = when (type) {
        "forgot_password" -> Icons.Rounded.LockReset to "Reset Access"
        "otp" -> Icons.Rounded.VerifiedUser to "Secure OTP"
        else -> Icons.Rounded.Forum to "Fast & Private"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(vertical = 12.dp)
            .border(
                1.5.dp,
                c(argbWithAlpha(theme.accentColor, 0.15)),
                RoundedCornerShape(24.dp),
            )
            .background(
                c(argbWithAlpha(theme.cardColor, 0.6)),
                RoundedCornerShape(24.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(c(argbWithAlpha(theme.accentColor, 0.15)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(mainIcon, contentDescription = null, tint = c(theme.accentColor), modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .background(c(argbWithAlpha(theme.accentColor, 0.12)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(badgeText, color = c(theme.accentColor), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WorkspaceModeSelector(
    theme: ThemeConfig,
    useOrganization: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(argbWithAlpha(theme.surfaceColor, 0.6)),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                // Dart: dividerColor (secondaryText at 0.2) then withValues(alpha: 0.25) replaces the alpha
                Color(argbWithAlpha(theme.secondaryTextColor, 0.25)),
                RoundedCornerShape(14.dp),
            )
            .padding(4.dp),
    ) {
        ModeTab(
            theme = theme,
            label = "Normal / Default",
            selected = !useOrganization,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(false) },
        )
        ModeTab(
            theme = theme,
            label = "Organization",
            selected = useOrganization,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun ModeTab(
    theme: ThemeConfig,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) Color(theme.onAccentColor) else Color(theme.secondaryTextColor)
    Box(
        modifier = modifier
            .background(
                if (selected) Color(theme.accentColor) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = contentColor,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}


/** Approved capsule login tokens, opt-in until each later screen is approved. */
internal object AuthCapsuleTokens {
    val tapTarget = 48.dp
    const val checkboxMs = 180
    val controlHeight = 56.dp
    val shape = CircleShape
}

@Composable
internal fun AuthTextLink(
    text: String,
    theme: ThemeConfig,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = AuthCapsuleTokens.tapTarget),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = c(theme.accentColor)),
    ) { Text(text, fontSize = 13.5.sp) }
}

/** No destination, persistence or authentication changes: caller owns mode state. */
@Composable
internal fun AuthCapsuleModeSelector(
    theme: ThemeConfig,
    useOrganization: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    val labelStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widestLabel = with(density) {
        listOf("Normal", "Organization").maxOf {
            textMeasurer.measure(androidx.compose.ui.text.AnnotatedString(it), style = labelStyle,
                softWrap = false).size.width
        }.toDp()
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().semantics { isTraversalGroup = true }
            .background(c(argbWithAlpha(theme.secondaryTextColor, 0.06)), AuthCapsuleTokens.shape)
            .border(1.dp, c(argbWithAlpha(theme.secondaryTextColor, 0.2)), AuthCapsuleTokens.shape)
            .padding(3.dp).selectableGroup(),
    ) {
        // Measure with the actual device font scale. Keep whole labels and48dp targets,
        // stack instead of shrinking text or splitting Organization mid-word.
        val sideBySide = maxWidth >= (widestLabel + 20.dp + 8.dp + 20.dp) * 2
        if (sideBySide) {
            Row(Modifier.fillMaxWidth()) {
                listOf(false to "Normal", true to "Organization").forEach { (organization, label) ->
                    AuthCapsuleModeOption(theme, organization, label, useOrganization, onSelect,
                        Modifier.weight(1f), labelStyle)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                listOf(false to "Normal", true to "Organization").forEach { (organization, label) ->
                    AuthCapsuleModeOption(theme, organization, label, useOrganization, onSelect,
                        Modifier.fillMaxWidth(), labelStyle)
                }
            }
        }
    }
}

@Composable
private fun AuthCapsuleModeOption(
    theme: ThemeConfig,
    organization: Boolean,
    label: String,
    useOrganization: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier,
    labelStyle: androidx.compose.ui.text.TextStyle,
) {
    val selected = organization == useOrganization
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val fill by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) c(theme.accentColor) else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (reduced) 0 else com.example.chat.ui.kit.ChatyMotion.fastMs,
            easing = com.example.chat.ui.kit.ChatyMotion.standard,
        ), label = "auth-mode-fill",
    )
    val foreground = if (selected) c(theme.onAccentColor) else c(theme.secondaryTextColor)
    Row(
        modifier.heightIn(min = AuthCapsuleTokens.tapTarget)
            .background(fill, AuthCapsuleTokens.shape)
            .selectable(selected, role = androidx.compose.ui.semantics.Role.Tab, onClick = { onSelect(organization) })
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (organization) AuthRoleIcon("workspace", theme, color = foreground)
        else com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.profile, size = 20.dp, color = foreground)
        Spacer(Modifier.width(8.dp))
        Text(label, color = foreground, style = labelStyle,
            softWrap = false, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Resolve through the persisted selected pack, including per-icon variants. */
@Composable
internal fun AuthRoleIcon(role: String, theme: ThemeConfig, label: String? = null, color: Color = c(theme.secondaryTextColor)) {
    val id = when (role) {
        "workspace" -> com.example.chat.ui.icons.ChatyIconId.workspace
        "eye" -> com.example.chat.ui.icons.ChatyIconId.eye
        "eyeOff" -> com.example.chat.ui.icons.ChatyIconId.eyeOff
        "info" -> com.example.chat.ui.icons.ChatyIconId.info
        else -> com.example.chat.ui.icons.ChatyIconId.mail
    }
    com.example.chat.ui.icons.ChatyIcon(id, size = 20.dp, color = color, semanticLabel = label)
}
