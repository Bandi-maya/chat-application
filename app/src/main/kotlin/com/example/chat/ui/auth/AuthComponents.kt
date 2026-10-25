package com.example.chat.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
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

/** Circular back button (AuthBackButton -> ChatyBackButton, size 34/chevron 24). */
@Composable
fun AuthBackButton(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = colors.foreground,
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
) {
    val colors = LocalAppColors.current
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
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    hintText,
                    color = c(argbWithAlpha(theme.secondaryTextColor, 0.5)),
                    fontSize = 14.sp,
                )
            },
            visualTransformation = if (obscureText) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            isError = error != null,
            supportingText = error?.let { { Text(it, color = c(theme.dangerColor), fontSize = 12.sp) } },
            leadingIcon = prefixIcon,
            trailingIcon = suffixIcon,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c(theme.primaryTextColor),
                unfocusedTextColor = c(theme.primaryTextColor),
                focusedContainerColor = colors.inputFill,
                unfocusedContainerColor = colors.inputFill,
                errorContainerColor = colors.inputFill,
                focusedBorderColor = c(theme.accentColor),
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
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
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
        modifier = Modifier.fillMaxWidth().height(52.dp),
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

