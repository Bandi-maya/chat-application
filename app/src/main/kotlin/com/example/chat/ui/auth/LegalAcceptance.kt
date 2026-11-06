package com.example.chat.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig

/** Play UGC policy (P-06): account creation must be gated on explicit terms
 *  acceptance. The row is shared by RegisterScreen and RegisterOrganizationScreen.
 *  The linked documents are bundled drafts (app/src/main/assets/legal) so the
 *  step works before the hosted copies exist; text is rendered, no WebView. */

internal const val TERMS_ANNOTATION = "terms"
internal const val PRIVACY_ANNOTATION = "privacy"

@Composable
fun TermsAcceptanceRow(
    theme: ThemeConfig,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = Color(theme.secondaryTextColor))) { append("I agree to the ") }
        pushStringAnnotation(tag = TERMS_ANNOTATION, annotation = TERMS_ANNOTATION)
        withStyle(SpanStyle(color = Color(theme.linkColor), fontWeight = FontWeight.SemiBold)) {
            append("Terms of Use")
        }
        pop()
        withStyle(SpanStyle(color = Color(theme.secondaryTextColor))) { append(" and ") }
        pushStringAnnotation(tag = PRIVACY_ANNOTATION, annotation = PRIVACY_ANNOTATION)
        withStyle(SpanStyle(color = Color(theme.linkColor), fontWeight = FontWeight.SemiBold)) {
            append("Privacy Policy")
        }
        pop()
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        ClickableText(
            text = annotated,
            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            onClick = { offset ->
                when {
                    annotated.getStringAnnotations(TERMS_ANNOTATION, offset, offset).isNotEmpty() -> onOpenTerms()
                    annotated.getStringAnnotations(PRIVACY_ANNOTATION, offset, offset).isNotEmpty() -> onOpenPrivacy()
                    else -> onCheckedChange(!checked)
                }
            },
        )
    }
}

/** Simple reader for a bundled legal document. Theme tokens only. */
@Composable
fun LegalTextDialog(
    theme: ThemeConfig,
    title: String,
    assetPath: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val body by produceState<String?>(initialValue = null, assetPath) {
        value = runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrElse { "Could not load this document from the app bundle." }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color(theme.linkColor)) } },
        title = { Text(title, color = Color(theme.primaryTextColor), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    body ?: "Loading...",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
        },
    )
}
