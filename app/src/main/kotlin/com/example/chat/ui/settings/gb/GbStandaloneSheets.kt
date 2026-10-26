package com.example.chat.ui.settings.gb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ServiceLocator
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dart gb_settings_screen.dart _showWasteCleaningSheet / _showWidgetSettingsSheet bodies, shared by the GB
 * screen and by the settings-root rows (Dart openWasteCleaning / openHomeShortcuts show the same sheets).
 */
@Composable
fun GbWasteCleaningSheetContent(onDismissSheet: () -> Unit, toast: (String) -> Unit) {
    val theme = ServiceLocator.themeController.globalTheme
    val colors = LocalAppColors.current
    val accent = Color(theme.accentColor)
    val fs = theme.fontScale.toFloat()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
            Text("Expired Cache Cleanup", color = Color(theme.primaryTextColor), fontSize = (18f * fs).sp, fontWeight = FontWeight.W700)
            Spacer(Modifier.height(10.dp))
            Text("Only Chaty-generated compressed image copies older than 24 hours are eligible. Voice notes, recent uploads, unsent staged media, chat attachments and profile images are preserved.", color = Color(theme.secondaryTextColor), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            val stats by produceState<Result<GbSettingsLogic.CacheStats>?>(null) {
                value = withContext(Dispatchers.IO) { runCatching { GbSettingsLogic.scanExpiredCompressedImages(context.cacheDir) } }
            }
            val r = stats
            when {
                r == null -> Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                r.isFailure -> Text("Cache information is unavailable on this device.", color = Color(theme.secondaryTextColor))
                else -> {
                    val st = r.getOrThrow()
                    Row(
                        Modifier.fillMaxWidth().background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CleaningServices, null, tint = accent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Expired image cache", fontWeight = FontWeight.Bold, color = Color(theme.primaryTextColor))
                            Text("${st.count} file(s) \u2022 ${GbSettingsLogic.formatCacheSize(st.bytes)}", fontSize = 12.sp, color = Color(theme.secondaryTextColor))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = colors.onPrimary),
                        enabled = st.count != 0,
                        onClick = {
                            onDismissSheet()
                            scope.launch {
                                val removed = withContext(Dispatchers.IO) { GbSettingsLogic.cleanExpiredCompressedImages(context.cacheDir) }
                                toast("Removed ${removed.count} expired file(s), ${GbSettingsLogic.formatCacheSize(removed.bytes)}.")
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (st.count == 0) "No expired cache to clean" else "Clean ${st.count} expired file(s)")
                    }
                }
            }
}

@Composable
fun GbHomeShortcutsSheetContent(onDismissSheet: () -> Unit, onOpenHome: () -> Unit) {
    val theme = ServiceLocator.themeController.globalTheme
    val fs = theme.fontScale.toFloat()
            Text("Home shortcuts", color = Color(theme.primaryTextColor), fontSize = (18f * fs).sp, fontWeight = FontWeight.W700)
            Spacer(Modifier.height(10.dp))
            Text("Chaty applies home layout and shortcut settings inside the app. Native Android/iOS launcher widgets are not registered in this build, so this screen does not show switches that cannot take effect.", color = Color(theme.secondaryTextColor), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = { onDismissSheet(); onOpenHome() }) {
                Icon(Icons.Rounded.DashboardCustomize, null)
                Spacer(Modifier.width(8.dp))
                Text("Customize in-app home")
            }
}

/** Standalone host: Dart showModalBottomSheet(surfaceColor, top radius 24). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GbStandaloneSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val theme = ServiceLocator.themeController.globalTheme
    ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        containerColor = Color(theme.surfaceColor),
        shape = com.example.chat.ui.kit.ChatyModalShape,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) { content() }
    }
}
