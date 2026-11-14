package com.example.chat.island.ui.screens

import com.example.chat.island.ui.components.IslandIcon as Icon
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chat.R
import com.example.chat.island.core.IslandSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val hasLauncher: Boolean,
)

private enum class AppFilter { All, User, System }

/** Bounded icon cache; loads happen off the main thread. */
private object AppIconCache {
    private const val MAX_ENTRIES = 120
    private val lock = Any()
    private val cache = android.util.LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(packageName: String): ImageBitmap? = synchronized(lock) { cache.get(packageName) }

    fun put(packageName: String, icon: ImageBitmap) {
        synchronized(lock) { cache.put(packageName, icon) }
    }
}

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val ownPackage = context.packageName

    var refreshKey by remember { mutableIntStateOf(0) }

    // Refresh on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Refresh on install / uninstall / update.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshKey++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val apps by produceState<List<AppEntry>?>(initialValue = null, refreshKey) {
        value = withContext(Dispatchers.IO) { loadApps(context, ownPackage) }
    }

    val blocked by IslandSettings.blockedApps.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(AppFilter.All.name) }
    val filter = AppFilter.valueOf(filterName)

    val installed = apps
    val baseList = remember(installed, filter) {
        installed.orEmpty().filter { entry ->
            when (filter) {
                // Default policy: launcher and user apps only.
                AppFilter.All -> !entry.isSystem || entry.hasLauncher
                AppFilter.User -> !entry.isSystem
                // System filter additionally exposes non-launcher system apps.
                AppFilter.System -> true
            }
        }
    }
    val visible = remember(baseList, query) {
        if (query.isBlank()) {
            baseList
        } else {
            baseList.filter { it.label.contains(query, ignoreCase = true) }
        }
    }
    val installedPackages = remember(installed) { installed.orEmpty().map { it.packageName }.toSet() }
    val orphanedBlocked = remember(blocked, installedPackages, query) {
        (blocked - installedPackages)
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .sorted()
    }

    val listState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.apps_search_hint)) },
        )
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFilter.entries.forEach { f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { filterName = f.name },
                    label = {
                        Text(
                            stringResource(
                                when (f) {
                                    AppFilter.All -> R.string.apps_filter_all
                                    AppFilter.User -> R.string.apps_filter_user
                                    AppFilter.System -> R.string.apps_filter_system
                                },
                            ),
                        )
                    },
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.apps_blocked_count, blocked.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    IslandSettings.blockedApps.value = emptySet()
                    IslandSettings.save()
                }) {
                    Text(stringResource(R.string.apps_allow_all))
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = {
                    // Block the whole system-policy base list for the current
                    // filter, not just the text-search results.
                    IslandSettings.blockedApps.value = blocked + baseList.map { it.packageName }
                    IslandSettings.save()
                }) {
                    Text(stringResource(R.string.apps_block_all))
                }
            }
        }

        when {
            installed == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            visible.isEmpty() && orphanedBlocked.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.apps_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (orphanedBlocked.isNotEmpty()) {
                        item(key = "orphan_header") {
                            Text(
                                text = stringResource(R.string.apps_not_installed_section),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(orphanedBlocked, key = { "orphan_$it" }) { packageName ->
                            OrphanAppRow(packageName = packageName)
                        }
                    }
                    items(visible, key = { it.packageName }) { entry ->
                        AppRow(
                            entry = entry,
                            allowed = entry.packageName !in blocked,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(entry: AppEntry, allowed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = entry.packageName)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = allowed,
            onCheckedChange = { isAllowed ->
                IslandSettings.setBlocked(entry.packageName, !isAllowed)
                IslandSettings.save()
            },
        )
    }
}

/** Row for a blocked package that is no longer installed, so it can be unblocked. */
@Composable
private fun OrphanAppRow(packageName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Android,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(packageName, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.apps_not_installed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = false,
            onCheckedChange = {
                IslandSettings.setBlocked(packageName, false)
                IslandSettings.save()
            },
        )
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = AppIconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppIconCache.get(packageName) ?: runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(width = 96, height = 96)
                        .asImageBitmap()
                }.getOrNull()?.also { AppIconCache.put(packageName, it) }
            }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
    } else {
        Icon(
            Icons.Outlined.Android,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun loadApps(context: Context, ownPackage: String): List<AppEntry> {
    val pm = context.packageManager
    val launcherPackages = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        PackageManager.MATCH_ALL,
    ).mapTo(HashSet()) { it.activityInfo.packageName }

    return launcherPackages.asSequence()
        .mapNotNull { packageName -> runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() }
        .filter { it.packageName != ownPackage }
        .map { info ->
            AppEntry(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(info.packageName),
                isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                hasLauncher = info.packageName in launcherPackages,
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}
