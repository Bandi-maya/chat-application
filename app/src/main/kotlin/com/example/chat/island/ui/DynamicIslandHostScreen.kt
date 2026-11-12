package com.example.chat.island.ui

import com.example.chat.island.ui.components.IslandIcon as Icon
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chat.R
import com.example.chat.island.core.IslandControl
import com.example.chat.island.engine.IslandService
import com.example.chat.island.core.IslandSettings
import com.example.chat.island.core.Perms
import com.example.chat.island.ui.onboarding.OnboardingScreen
import com.example.chat.island.ui.screens.AboutScreen
import com.example.chat.island.ui.screens.ActivitiesScreen
import com.example.chat.island.ui.screens.AppsScreen
import com.example.chat.island.ui.screens.CustomizeScreen
import com.example.chat.island.ui.screens.HomeScreen

private enum class IslandTab(val labelRes: Int, val icon: ImageVector) {
    Home(R.string.island_tab_home, Icons.Outlined.Home),
    Activities(R.string.island_tab_activities, Icons.Outlined.Bolt),
    Customize(R.string.island_tab_customize, Icons.Outlined.Tune),
    Apps(R.string.island_tab_apps, Icons.Outlined.Apps),
    About(R.string.island_tab_about, Icons.Outlined.Info),
}

@Composable
fun DynamicIslandHostScreen(onBack: () -> Unit) {
    com.example.chat.island.ui.theme.IslandLocale {
        com.example.chat.island.ui.theme.AppTheme { IslandSettingsContent(onBack) }
    }
}

@Composable
private fun IslandSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { IslandSettings.init(context.applicationContext) }

    // Bumped on every ON_RESUME so permission checks re-run after the user
    // returns from a system settings screen.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onboarded by IslandSettings.onboarded.collectAsState()
    val enabled by IslandSettings.enabled.collectAsState()
    val running by IslandService.running.collectAsState()

    val coreOk = remember(tick) { Perms.coreOk(context) }
    val ready = onboarded && coreOk

    LaunchedEffect(ready, enabled, running) {
        if (ready && enabled && !running) {
            IslandControl.start(context)
        }
    }

    if (!ready) {
        BackHandler { onBack() }
        OnboardingScreen(tick = tick)
        return
    }

    var selectedTabName by rememberSaveable { mutableStateOf(IslandTab.Home.name) }
    val selectedTab = IslandTab.valueOf(selectedTabName)
    val haptics = LocalHapticFeedback.current

    // Back from any secondary tab returns to Home first; Home backs out.
    BackHandler {
        if (selectedTab != IslandTab.Home) {
            selectedTabName = IslandTab.Home.name
        } else {
            onBack()
        }
    }

    val selectTab: (IslandTab) -> Unit = { tab ->
        if (tab.name != selectedTabName) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            selectedTabName = tab.name
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail {
                    IslandTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = tab == selectedTab,
                            onClick = { selectTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                bottomBar = {
                    if (!useRail) {
                        NavigationBar {
                            IslandTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tab == selectedTab,
                                    onClick = { selectTab(tab) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(stringResource(tab.labelRes)) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it / 3 } + fadeOut())
                        }
                    },
                    label = "islandTab",
                ) { tab ->
                    when (tab) {
                        IslandTab.Home -> HomeScreen(tick = tick)
                        IslandTab.Activities -> ActivitiesScreen()
                        IslandTab.Customize -> CustomizeScreen()
                        IslandTab.Apps -> AppsScreen()
                        IslandTab.About -> AboutScreen(tick = tick)
                    }
                }
            }
        }
    }
}
