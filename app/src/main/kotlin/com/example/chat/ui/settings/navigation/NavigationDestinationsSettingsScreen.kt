package com.example.chat.ui.settings.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.chat.core.templates.ChatyNavigationDestinationIds
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Port of navigation_destinations_settings_screen.dart (api branch anchor
 *  3bee780): choose up to 4 primary navigation destinations; the rest stay
 *  under More. No route is deleted. Save/reset go through TemplateController
 *  (owner wires [onSave]/[onReset] to setNavigationDestinations /
 *  resetNavigationDestinations with the shared TemplateRuntime); errors
 *  surface with Dart's 'Bad state: ' prefix stripped.
 *
 *  [onSaved] mirrors the Dart Navigator.pop after the saved snackbar; the
 *  caller shows 'Navigation destinations saved.' at app level and pops. */
@Composable
fun NavigationDestinationsSettingsScreen(
    initialPrimary: List<String>,
    initialOverflow: List<String>,
    onSave: suspend (primary: List<String>, overflow: List<String>) -> Unit,
    onReset: suspend () -> Pair<List<String>, List<String>>,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var primary by remember {
        mutableStateOf(NavigationDestinationsLogic.reconcile(initialPrimary, initialOverflow).first)
    }
    var overflow by remember {
        mutableStateOf(NavigationDestinationsLogic.reconcile(initialPrimary, initialOverflow).second)
    }
    var saving by remember { mutableStateOf(false) }

    fun errorText(error: Throwable): String =
        (error.message ?: error.toString()).removePrefix("Bad state: ")

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                onSave(primary.toList(), overflow.toList())
                saving = false
                onSaved()
            } catch (error: Exception) {
                saving = false
                snackbar.showSnackbar(errorText(error))
            }
        }
    }

    fun reset() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val (p, o) = onReset()
                val reconciled = NavigationDestinationsLogic.reconcile(p, o)
                primary = reconciled.first
                overflow = reconciled.second
                saving = false
                snackbar.showSnackbar("Destination order reset to the active template.")
            } catch (error: Exception) {
                saving = false
                snackbar.showSnackbar(errorText(error))
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.foreground)
                }
                Text(
                    "Navigation destinations",
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { reset() }, enabled = !saving) {
                    Text("Reset", color = colors.primary)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { save() }, enabled = !saving) {
                    if (saving) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = colors.primary, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = "Save navigation destinations", tint = colors.foreground)
                    }
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 760.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, top = 18.dp, end = 10.dp, bottom = 28.dp),
                    ) {
                        DestinationSection(
                            title = "Primary navigation",
                            subtitle = "Up to 4 frequently used destinations",
                            ids = primary,
                            isPrimary = true,
                            primaryCount = primary.size,
                            onMove = { id ->
                                val p = primary.toMutableList()
                                val o = overflow.toMutableList()
                                NavigationDestinationsLogic.moveToOverflow(p, o, id)
                                primary = p; overflow = o
                            },
                            onReorder = { index, delta ->
                                val p = primary.toMutableList()
                                NavigationDestinationsLogic.reorder(p, index, delta)
                                primary = p
                            },
                        )
                    }
                    Box(Modifier.fillMaxHeight().width(1.dp).background(colors.border))
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 10.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
                    ) {
                        DestinationSection(
                            title = "More menu",
                            subtitle = "All remaining screens stay available here",
                            ids = overflow,
                            isPrimary = false,
                            primaryCount = primary.size,
                            onMove = { id ->
                                val p = primary.toMutableList()
                                val o = overflow.toMutableList()
                                NavigationDestinationsLogic.moveToPrimary(p, o, id)
                                primary = p; overflow = o
                            },
                            onReorder = { index, delta ->
                                val o = overflow.toMutableList()
                                NavigationDestinationsLogic.reorder(o, index, delta)
                                overflow = o
                            },
                        )
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
                ) {
                    DestinationSection(
                        title = "Primary navigation",
                        subtitle = "Up to 4 frequently used destinations",
                        ids = primary,
                        isPrimary = true,
                        primaryCount = primary.size,
                        onMove = { id ->
                            val p = primary.toMutableList()
                            val o = overflow.toMutableList()
                            NavigationDestinationsLogic.moveToOverflow(p, o, id)
                            primary = p; overflow = o
                        },
                        onReorder = { index, delta ->
                            val p = primary.toMutableList()
                            NavigationDestinationsLogic.reorder(p, index, delta)
                            primary = p
                        },
                    )
                    Spacer(Modifier.height(18.dp))
                    DestinationSection(
                        title = "More menu",
                        subtitle = "All remaining screens stay available here",
                        ids = overflow,
                        isPrimary = false,
                        primaryCount = primary.size,
                        onMove = { id ->
                            val p = primary.toMutableList()
                            val o = overflow.toMutableList()
                            NavigationDestinationsLogic.moveToPrimary(p, o, id)
                            primary = p; overflow = o
                        },
                        onReorder = { index, delta ->
                            val o = overflow.toMutableList()
                            NavigationDestinationsLogic.reorder(o, index, delta)
                            overflow = o
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tip: keep your most-used screens in Primary. Every destination remains available either here or under More.",
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationSection(
    title: String,
    subtitle: String,
    ids: List<String>,
    isPrimary: Boolean,
    primaryCount: Int,
    onMove: (String) -> Unit,
    onReorder: (index: Int, delta: Int) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
            .padding(12.dp),
    ) {
        Row(Modifier.padding(start = 4.dp, top = 2.dp, end = 4.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.foreground, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
            }
            if (isPrimary) {
                Text(
                    "${ids.size}/4",
                    color = colors.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            }
        }
        if (ids.isEmpty()) {
            Text(
                "No destinations in this group.",
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        ids.forEachIndexed { index, id ->
            if (NavigationDestinationsLogic.isRowVisible(id)) {
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
                }
                DestinationRow(
                    id = id,
                    index = index,
                    count = ids.size,
                    isPrimary = isPrimary,
                    primaryCount = primaryCount,
                    onMove = { onMove(id) },
                    onReorder = onReorder,
                )
            }
        }
    }
}

@Composable
private fun DestinationRow(
    id: String,
    index: Int,
    count: Int,
    isPrimary: Boolean,
    primaryCount: Int,
    onMove: () -> Unit,
    onReorder: (index: Int, delta: Int) -> Unit,
) {
    val colors = LocalAppColors.current
    val title = NavigationDestinationsLogic.labels[id] ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(colors.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(destinationIcon(id), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = colors.foreground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) {
            IconButton(onClick = { onReorder(index, -1) }, enabled = index != 0, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", tint = colors.foreground)
            }
            IconButton(
                onClick = { onReorder(index, 1) },
                enabled = index != count - 1,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", tint = colors.foreground)
            }
            TextButton(
                onClick = onMove,
                enabled = if (isPrimary) primaryCount > 1 else primaryCount < 4,
            ) {
                Icon(
                    if (isPrimary) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (if (isPrimary) primaryCount > 1 else primaryCount < 4) colors.primary else colors.foregroundTertiary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isPrimary) "Move to More" else "Make primary",
                    color = if (if (isPrimary) primaryCount > 1 else primaryCount < 4) colors.primary else colors.foregroundTertiary,
                )
            }
        }
    }
}

private fun destinationIcon(id: String): ImageVector = when (id) {
    ChatyNavigationDestinationIds.chats -> Icons.Outlined.ChatBubbleOutline
    ChatyNavigationDestinationIds.groups -> Icons.Outlined.Groups
    ChatyNavigationDestinationIds.updates -> Icons.Outlined.AutoAwesome
    ChatyNavigationDestinationIds.tasks -> Icons.Rounded.Checklist
    ChatyNavigationDestinationIds.calls -> Icons.Outlined.Call
    ChatyNavigationDestinationIds.settings -> Icons.Outlined.Settings
    else -> Icons.Outlined.Devices
}
