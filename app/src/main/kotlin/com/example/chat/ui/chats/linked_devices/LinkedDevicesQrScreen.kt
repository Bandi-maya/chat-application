package com.example.chat.ui.chats.linked_devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.services.LinkedDevice
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.contact_info.ConfirmDialog
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Port of linked_devices_qr_screen.dart (api branch anchor 3bee780):
 *  devices list (register current device, revoke others), My QR
 *  (chaty://contact/<username>, EC level H, center brand badge), and the ML
 *  Kit scanner that resolves a scanned contact into a direct conversation.
 *
 *  Tab shapes follow the Dart constructor flags: devicesOnly -> single
 *  "Linked Devices" page, qrOnly -> My QR + Scan, otherwise all three.
 *  Pull-to-refresh matches the Dart RefreshIndicator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesQrScreen(
    currentUserDisplayName: String,
    currentUserUsername: String,
    devices: List<LinkedDevice>,
    loadingDevices: Boolean,
    devicesError: String?,
    initialIndex: Int = 0,
    qrOnly: Boolean = false,
    devicesOnly: Boolean = false,
    onRefreshDevices: suspend () -> Unit,
    onRevokeDevice: suspend (LinkedDevice) -> Unit,
    searchUsersRemote: suspend (String) -> List<UserProfile>,
    openDirectConversation: suspend (UserProfile) -> Conversation,
    onOpenConversation: (Conversation) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val tabCount = if (devicesOnly) 0 else if (qrOnly) 2 else 3
    var selectedTab by remember {
        mutableIntStateOf(
            when {
                devicesOnly -> 0
                qrOnly -> initialIndex.coerceIn(0, 1)
                else -> initialIndex.coerceIn(0, 2)
            },
        )
      }
    var scanBusy by remember { mutableStateOf(false) }
    var revokeCandidate by remember { mutableStateOf<LinkedDevice?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // _handleBarcode: validate the Chaty URI, reject self, resolve the user
    // remotely, then open the direct conversation.
    fun handleBarcode(raw: String?) {
        if (scanBusy) return
        when (val parsed = QrPayloads.parseScanned(raw)) {
            QrPayloads.ScanResult.EmptyUsername -> Unit // Dart returns silently.
            QrPayloads.ScanResult.NotChatyQr -> {
                scope.launch { snackbar.showSnackbar("This is not a Chaty contact QR code.") }
            }
            is QrPayloads.ScanResult.Contact -> {
                val username = parsed.username
                if (QrPayloads.isSelfUsername(username, currentUserUsername)) {
                    scope.launch { snackbar.showSnackbar("This QR belongs to your current account.") }
                    return
                }
                scanBusy = true
                scope.launch {
                    try {
                        val results = searchUsersRemote(username)
                        val user = results.firstOrNull { it.username.lowercase() == username.lowercase() }
                            ?: throw Exception("Chaty user @$username could not be found.")
                        val conversation = openDirectConversation(user)
                        onOpenConversation(conversation)
                    } catch (error: Exception) {
                        snackbar.showSnackbar((error.message ?: error.toString()).removePrefix("Exception: "))
                    } finally {
                        scanBusy = false
                    }
                }
            }
        }
    }

    val title = when {
        devicesOnly -> "Linked Devices"
        qrOnly -> "QR Code & Scanner"
        else -> "Linked Devices & QR"
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.foreground)
                    }
                    Text(title, color = colors.foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                if (tabCount > 0) {
                    // Dart TabBar(indicatorColor: primary, labelColor: primary,
                    // unselectedLabelColor: onSurface 0.55). PrimaryTabRow's
                    // DEFAULT indicator is the supported way to land that exact
                    // visual: it positions the bar under the selected tab
                    // (TabRowScope.tabIndicatorOffset internally) and colors it
                    // with MaterialTheme.colorScheme.primary, which ChatyTheme
                    // maps to the theme accent. A custom indicator slot broke
                    // the build 22 compile (tabIndicatorOffset is
                    // TabRowScope-scoped, not a top-level Modifier import).
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = colors.background,
                        contentColor = colors.primary,
                    ) {
                        if (!qrOnly) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Rounded.Devices, contentDescription = null) },
                                text = { Text("Devices") },
                                selectedContentColor = colors.primary,
                                unselectedContentColor = colors.foreground.copy(alpha = 0.55f),
                            )
                        }
                        val myQrIndex = if (qrOnly) 0 else 1
                        val scanIndex = if (qrOnly) 1 else 2
                        Tab(
                            selected = selectedTab == myQrIndex,
                            onClick = { selectedTab = myQrIndex },
                            icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
                            text = { Text("My QR") },
                            selectedContentColor = colors.primary,
                            unselectedContentColor = colors.foreground.copy(alpha = 0.55f),
                        )
                        Tab(
                            selected = selectedTab == scanIndex,
                            onClick = { selectedTab = scanIndex },
                            icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                            text = { Text("Scan") },
                            selectedContentColor = colors.primary,
                            unselectedContentColor = colors.foreground.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                devicesOnly -> DevicesView(
                    devices, loadingDevices, devicesError, refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            onRefreshDevices()
                            refreshing = false
                        }
                    },
                    onRevoke = { revokeCandidate = it },
                    linkTabAvailable = false,
                    onGoToLinkTab = null,
                )
                qrOnly && selectedTab == 0 -> MyQrView(currentUserDisplayName, currentUserUsername)
                qrOnly -> ScannerView(scanBusy, onDetected = { handleBarcode(it) })
                selectedTab == 0 -> DevicesView(
                    devices, loadingDevices, devicesError, refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            onRefreshDevices()
                            refreshing = false
                        }
                    },
                    onRevoke = { revokeCandidate = it },
                    linkTabAvailable = true,
                    onGoToLinkTab = { selectedTab = 1 },
                )
                selectedTab == 1 -> MyQrView(currentUserDisplayName, currentUserUsername)
                else -> ScannerView(scanBusy, onDetected = { handleBarcode(it) })
            }
        }
    }

    // _revoke confirm dialog (skipped for the current device).
    val revokeTarget = revokeCandidate
    if (revokeTarget != null) {
        ConfirmDialog(
            title = "Log out ${revokeTarget.deviceName}?",
            message = "This device will be marked revoked and removed from your active device list.",
            confirmLabel = "Log out",
            onConfirm = {
                revokeCandidate = null
                scope.launch {
                    try {
                        onRevokeDevice(revokeTarget)
                    } catch (error: Exception) {
                        snackbar.showSnackbar("$error")
                    }
                }
            },
            onDismiss = { revokeCandidate = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesView(
    devices: List<LinkedDevice>,
    loading: Boolean,
    error: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRevoke: (LinkedDevice) -> Unit,
    linkTabAvailable: Boolean,
    onGoToLinkTab: (() -> Unit)?,
) {
    val colors = LocalAppColors.current
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.2.dp, color = colors.primary)
        }
        error != null -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.foregroundSecondary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text("Could not load devices", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(error, color = colors.foregroundSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRefresh) {
                    Text("Try again", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        devices.isEmpty() -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Devices, contentDescription = null, tint = colors.foregroundSecondary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text("No linked devices", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Link your computer or tablet by scanning the QR code in the Link Device tab.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                if (linkTabAvailable && onGoToLinkTab != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onGoToLinkTab) {
                        Text("Link new device", color = colors.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                item {
                    Text(
                        "Active Devices",
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                        devices.forEach { device ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.background(colors.primary.copy(alpha = 0.12f), CircleShape).padding(8.dp)) {
                                    Icon(
                                        Icons.Rounded.DevicesOther,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        device.deviceName,
                                        color = colors.foreground,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                    Text(
                                        "${device.platform}${if (device.location.isEmpty()) "" else " • ${device.location}"}\n${QrPayloads.lastActiveLabel(device.lastActiveAt)}",
                                        color = colors.foregroundSecondary.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                    )
                                }
                                if (device.isCurrentDevice) {
                                    Box(
                                        Modifier
                                            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            "This Device",
                                            color = colors.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { onRevoke(device) }) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.Logout,
                                            contentDescription = "Log out device",
                                            tint = colors.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyQrView(displayName: String, username: String) {
    val colors = LocalAppColors.current
    val theme = LocalThemeConfig.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .background(colors.surface, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                val qrBitmap = remember(username, theme) {
                    QrCodeRenderer.render(
                        QrPayloads.myQrPayload(username),
                        sizePx = 230 * 3,
                        foregroundArgb = theme.primaryTextColor.toInt(),
                    )
                }
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "My Chaty contact QR",
                    modifier = Modifier.size(230.dp),
                )
                // Center quiet-zone badge with the Chaty brand mark.
                Box(
                    Modifier
                        .size(44.dp)
                        .background(colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.QrCode2,
                        contentDescription = "Chaty",
                        tint = colors.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(displayName, color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text("@$username", color = colors.foregroundSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Scan this code to start an instant encrypted chat without phone numbers.",
            color = colors.foregroundSecondary,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp),
        )
    }
}

@Composable
private fun ScannerView(scanBusy: Boolean, onDetected: (String) -> Unit) {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxSize()) {
        QrScannerView(modifier = Modifier.fillMaxSize(), onDetected = onDetected)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp)
                .background(colors.surfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "Point camera at a Chaty contact QR code",
                color = colors.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Dart: Border.all(color: primary, width: 2.5) over 240x240, radius xl.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .border(2.5.dp, colors.primary, RoundedCornerShape(20.dp)),
        )
        if (scanBusy) {
            Box(
                Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.2.dp, color = colors.primary)
            }
        }
    }
}
