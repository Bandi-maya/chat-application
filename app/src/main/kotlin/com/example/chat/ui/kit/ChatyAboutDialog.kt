package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.ui.theme.LocalAppColors

/** One package's licenses for the licenses page (Flutter LicenseRegistry entry: packages + license text paragraphs). */
data class ChatyLicenseEntry(val packageName: String, val licenseTexts: List<String>)

/**
 * Port of Flutter showAboutDialog / AboutDialog (as called by settings_screen.dart L974):
 * content = [icon?] + name (headlineSmall 24) / version (bodyMedium 14) / 18dp gap / legalese (bodySmall 12), then [children];
 * actions "View licenses" (opens the licenses page) and "Close".
 * The Dart call passes no applicationIcon, so no icon is drawn unless [icon] is supplied.
 * [licenses] must be provided by the owner (e.g. a build-time generated list of dependency licenses); there is no
 * Flutter LicenseRegistry equivalent in this port. Uncompiled; Flutter's own "Powered by Flutter" header line is
 * intentionally not reproduced because this is a native app (decision to confirm).
 */
@Composable
fun ChatyAboutDialog(
    applicationName: String,
    applicationVersion: String,
    applicationLegalese: String?,
    onDismiss: () -> Unit,
    licenses: List<ChatyLicenseEntry> = emptyList(),
    icon: (@Composable () -> Unit)? = null,
    children: @Composable () -> Unit = {},
) {
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        ChatyLicensePage(applicationName, applicationVersion, applicationLegalese, licenses) { showLicenses = false }
        return
    }
    val colors = LocalAppColors.current
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    if (icon != null) { icon(); Spacer(Modifier.width(24.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(applicationName, color = colors.foreground, fontSize = 24.sp, lineHeight = 32.sp)
                        Text(applicationVersion, color = colors.foreground, fontSize = 14.sp)
                        if (!applicationLegalese.isNullOrEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            Text(applicationLegalese, color = colors.foreground, fontSize = 12.sp)
                        }
                    }
                }
                children()
            }
        },
        dismissButton = { TextButton(onClick = { showLicenses = true }) { Text("View licenses") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** LicensePage: "Licenses" app bar with "N licenses" caption, app header, then one row per package with its license count. */
@Composable
private fun ChatyLicensePage(
    name: String,
    version: String,
    legalese: String?,
    licenses: List<ChatyLicenseEntry>,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    var selected by remember { mutableStateOf<ChatyLicenseEntry?>(null) }
    Dialog(onDismissRequest = { if (selected != null) selected = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (selected != null) selected = null else onClose() }) {
                    Icon(Icons.Rounded.ChevronLeft, "Back", tint = colors.foreground)
                }
                Column {
                    Text(selected?.packageName ?: "Licenses", color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    if (selected == null) Text("${licenses.size} licenses", color = colors.foregroundSecondary, fontSize = 12.sp)
                }
            }
            val sel = selected
            if (sel != null) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    sel.licenseTexts.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.divider)
                        Text(t, color = colors.foreground, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, color = colors.foreground, fontSize = 24.sp)
                            Text(version, color = colors.foreground, fontSize = 14.sp)
                            if (!legalese.isNullOrEmpty()) Text(legalese, color = colors.foreground, fontSize = 12.sp)
                        }
                        HorizontalDivider(color = colors.divider)
                    }
                    items(licenses) { e ->
                        Column(Modifier.fillMaxWidth().clickable { selected = e }.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
                            Text(e.packageName, color = colors.foreground, fontSize = 16.sp)
                            Text("${e.licenseTexts.size} license" + if (e.licenseTexts.size == 1) "" else "s", color = colors.foregroundSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
