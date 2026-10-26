package com.example.chat.ui.navigation

import com.example.chat.core.templates.ChatyNavigationDestinationIds
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.core.theme.UILayoutMode
import com.example.chat.ui.core.theme.ChatyColors
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Parameters the Dart shell reads from the preference / appearance /
 *  template controllers. Defaults mirror the registered defaults so the
 *  shell is usable before those controllers land in Kotlin. */
data class NavigationShellPrefs(
    val separateChatsAndGroups: Boolean = false,
    val hasCustomNavigationDestinations: Boolean = false,
    val hideTasks: Boolean = false,
    val hideUpdates: Boolean = false,
    /** preferences.home.pagerTransition3d (default 'Cube 3D'). */
    val pagerTransition3d: String = "Cube 3D",
    /** appearance.bottomBarStyle (default 'Floating Pill'). */
    val bottomBarStyle: String = "Floating Pill",
    /** appearance.navigation style name; the rail index derives through
     *  the canonical AppearanceVariantController.navigationStyles
     *  indexOf clamp, exactly like the Dart controller. */
    val navigationStyleName: String = "Adaptive Rail",
    /** preferences.home.homeStyle; when set, HomePresetNormalizer resolves
     *  the shell mode and wins over [MainNavigationShell]'s navigationMode
     *  parameter (Dart: HomePresetNormalizer.navigationMode(homeStyle) ??
     *  themeController.navigationMode). */
    val homeStyle: String? = null,
    val animationsDisabled: Boolean = false,
)

/** Exact port of the _MainNavigationShellState build/selection/back
 *  pipeline from main_navigation_shell.dart. Destination resolution is the
 *  JVM-tested [NavigationCatalog]; this composable owns the pager, the page
 *  transitions and the shell dispatch.
 *
 *  Seams:
 *  - [screenContent]: builds the root screen for a destination id ('chats',
 *    'groups', 'updates', 'tasks', 'calls', 'settings', 'desktop'); unknown
 *    ids render an empty box (Dart _dummyBuilder).
 *  - [fab]: the contextual FAB host (NavigationFab.kt).
 *  - [onExitApp]: SystemNavigator.pop equivalent.
 *  - [onNotify]: SnackBar equivalent.
 *  - [homeHeaderTabsSlot]: topWhatsAppBar mode hands its tab bar to the
 *    Chats/Groups home header (Dart homeHeaderBottomWidget). */
@Composable
fun MainNavigationShell(
    colors: ChatyColors,
    modifier: Modifier = Modifier,
    template: com.example.chat.core.templates.NavigationTemplate = com.example.chat.core.templates.ChatyTemplateRegistry.messageFirst.navigation,
    navigationMode: AppNavigationMode = AppNavigationMode.bottomNav,
    layoutMode: UILayoutMode = UILayoutMode.classic,
    prefs: NavigationShellPrefs = NavigationShellPrefs(),
    screenContent: @Composable (destinationId: String) -> Unit = {},
    fab: @Composable (activeDestinationId: String) -> Unit = {},
    homeHeaderTabsSlot: ((@Composable () -> Unit)?) -> Unit = {},
    /** Current user for the drawer/rail identity rows (Dart
     *  locator<ChatyDataStore>().currentUser). */
    currentUser: com.example.chat.domain.models.UserProfile? = null,
    /** preferences.home.myNameOverride (modern side menu display name). */
    myNameOverride: () -> String = { "" },
    /** Dart _handleNavigationCenterAction: 'camera' -> story capture +
     *  publish + 'Status posted.'; otherwise the effect picker sheet. The
     *  host wires the camera seam (same one as UpdatesScreen). */
    onCenterAction: () -> Unit = {},
    onExitApp: () -> Unit = {},
    onNotify: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val catalogIds = NavigationCatalog.catalogIds(
        separateChatsAndGroups = prefs.separateChatsAndGroups,
        hasCustomNavigationDestinations = prefs.hasCustomNavigationDestinations,
        hideTasks = prefs.hideTasks,
        hideUpdates = prefs.hideUpdates,
    )
    val allDestinationIds = NavigationCatalog.resolveOrdered(catalogIds, template)
    // Every enabled destination is visible. Templates still choose ordering/style,
    // but must not put destinations behind a More item.
    val primaryIds = allDestinationIds
    val overflowIds = emptyList<String>()
    val navItemIds = allDestinationIds

    var currentIndex by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var currentDestinationId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ChatyNavigationDestinationIds.chats) }
    var lastExitAttempt by remember { mutableStateOf(0L) }
    var rootHistory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(listOf<String>()) }

    val pagerState = rememberPagerState(initialPage = currentIndex, pageCount = { allDestinationIds.size })

    fun selectRootDestination(next: Int, destinationId: String? = null) {
        if (next == currentIndex && (destinationId == null || destinationId == currentDestinationId)) return
        val nextId = destinationId ?: allDestinationIds.getOrNull(next) ?: return
        rootHistory = rootHistory + currentDestinationId
        lastExitAttempt = 0L
        currentIndex = next
        currentDestinationId = nextId
        if (pagerState.currentPage != next) {
            scope.launch {
                if (prefs.animationsDisabled) pagerState.scrollToPage(next)
                else pagerState.animateScrollToPage(next)
            }
        }
    }

    fun handleRootBack() {
        if (currentDestinationId == ChatyNavigationDestinationIds.chats) rootHistory = emptyList()
        if (currentDestinationId != ChatyNavigationDestinationIds.chats && rootHistory.isNotEmpty()) {
            val previous = rootHistory.last()
            rootHistory = rootHistory.dropLast(1)
            val index = allDestinationIds.indexOf(previous)
            if (index >= 0) {
                currentIndex = index
                currentDestinationId = previous
                lastExitAttempt = 0L
                scope.launch { pagerState.scrollToPage(index) }
                return
            }
        }
        if (currentDestinationId != ChatyNavigationDestinationIds.chats) {
            currentIndex = allDestinationIds.indexOf(ChatyNavigationDestinationIds.chats).coerceAtLeast(0)
            currentDestinationId = allDestinationIds[currentIndex]
            lastExitAttempt = 0L
            scope.launch { pagerState.scrollToPage(currentIndex) }
            return
        }
        val now = System.currentTimeMillis()
        if (lastExitAttempt == 0L || now - lastExitAttempt > 2000) {
            lastExitAttempt = now
            onNotify("Press back again to exit Chaty")
            return
        }
        onExitApp()
    }

    BackHandler(onBack = ::handleRootBack)

    var moreMenuOpen by remember { mutableStateOf(false) }

    val (effectiveIndex, wasInvalid) = NavigationCatalog.effectiveIndex(currentDestinationId, allDestinationIds)
    if (effectiveIndex != currentIndex || wasInvalid) {
        androidx.compose.runtime.LaunchedEffect(effectiveIndex, wasInvalid) {
            currentIndex = effectiveIndex
            currentDestinationId = allDestinationIds.getOrElse(effectiveIndex) { ChatyNavigationDestinationIds.chats }
            if (pagerState.currentPage != effectiveIndex) pagerState.scrollToPage(effectiveIndex)
        }
    }
    val activeDestinationId = allDestinationIds.getOrElse(effectiveIndex) { ChatyNavigationDestinationIds.chats }

    @Composable
    fun pageContent() {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val destinationId = allDestinationIds.getOrElse(index) { "destination-$index" }
            val rawOffset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
            val delta = (-rawOffset).coerceIn(-1f, 1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .pagerTransition(prefs.pagerTransition3d, delta),
            ) {
                screenContent(destinationId)
            }
        }
    }

    // Keep the pager and selection in sync after swipes (Dart onPageChanged).
    androidx.compose.runtime.LaunchedEffect(pagerState.settledPage) {
        val id = allDestinationIds.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (currentIndex != pagerState.settledPage || currentDestinationId != id) {
            rootHistory = rootHistory + currentDestinationId
            lastExitAttempt = 0L
            currentIndex = pagerState.settledPage
            currentDestinationId = id
        }
    }

    val selectedPrimaryIndex = primaryIds.indexOf(activeDestinationId)
    val bottomNavSelectedIndex = if (selectedPrimaryIndex >= 0) selectedPrimaryIndex else primaryIds.size

    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize().navigationBarsPadding()) {
        val appearanceStyles = com.example.chat.core.templates.AppearanceVariantController.navigationStyles
        val appearanceNavigationIndex = appearanceStyles.indexOf(prefs.navigationStyleName).coerceIn(0, appearanceStyles.size - 1)
        val forceRail = appearanceNavigationIndex in setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18)
        val autoRail = maxWidth >= (if (forceRail) 720.dp else 900.dp)
        val navMode = prefs.homeStyle?.let(HomePresetNormalizer::navigationMode) ?: navigationMode

        when {
            navMode == AppNavigationMode.topWhatsAppBar -> TopWhatsAppShell(
                colors = colors,
                template = template,
                navItemIds = allDestinationIds,
                selectedIndex = effectiveIndex,
                activeDestinationId = activeDestinationId,
                onSelect = { idx -> selectRootDestination(idx, allDestinationIds.getOrNull(idx)) },
                homeHeaderTabsSlot = homeHeaderTabsSlot,
                fab = { fab(activeDestinationId) },
                content = { pageContent() },
            )
            navMode == AppNavigationMode.floatingIslandRail -> FloatingIslandRailShell(
                colors = colors,
                navItemIds = allDestinationIds,
                selectedIndex = effectiveIndex,
                userInitials = currentUser?.avatarInitials?.ifEmpty { "CU" } ?: "CU",
                onSelect = { idx -> selectRootDestination(idx, allDestinationIds.getOrNull(idx)) },
                fab = { fab(activeDestinationId) },
                content = { pageContent() },
            )
            navMode == AppNavigationMode.perspective3DDrawer -> PerspectiveDrawerShell(
                colors = colors,
                navItemIds = allDestinationIds,
                selectedIndex = effectiveIndex,
                animationsDisabled = prefs.animationsDisabled,
                onSelect = { idx -> selectRootDestination(idx, allDestinationIds.getOrNull(idx)) },
                fab = { fab(activeDestinationId) },
                content = { pageContent() },
            )
            navMode == AppNavigationMode.modernSideMenu -> ModernSideMenuShell(
                colors = colors,
                navItemIds = allDestinationIds,
                selectedIndex = effectiveIndex,
                currentUser = currentUser,
                myNameOverride = myNameOverride,
                onSelect = { idx -> selectRootDestination(idx, allDestinationIds.getOrNull(idx)) },
                fab = { fab(activeDestinationId) },
                content = { pageContent() },
            )
            navMode == AppNavigationMode.curvedRadialDrawer -> CurvedRadialDrawerShell(
                colors = colors,
                navItemIds = allDestinationIds,
                selectedIndex = effectiveIndex,
                animationsDisabled = prefs.animationsDisabled,
                onSelect = { idx -> selectRootDestination(idx, allDestinationIds.getOrNull(idx)) },
                fab = { fab(activeDestinationId) },
                content = { pageContent() },
            )
            navMode in setOf(
                AppNavigationMode.oneUi,
                AppNavigationMode.iosStyle,
                AppNavigationMode.instagramStyle,
                AppNavigationMode.telegramStyle,
                AppNavigationMode.basicTabStyle,
                AppNavigationMode.gestureTabs,
            ) -> PresetNavigationShell(
                mode = navMode,
                colors = colors,
                navItemIds = allDestinationIds,
                selectedIndex = effectiveIndex,
                onSelect = { idx -> selectRootDestination(idx, allDestinationIds.getOrNull(idx)) },
                fab = { fab(activeDestinationId) },
                content = { pageContent() },
            )
            else -> {
                // Dart compactRail explicitly selects a rail on tablet widths.
                val useRail = maxWidth >= 600.dp &&
                    (navMode == AppNavigationMode.compactRail ||
                        (navMode != AppNavigationMode.gestureTabs &&
                            (layoutMode == UILayoutMode.tabletDesktop || autoRail)))
                if (useRail) {
                    RailShell(
                        colors = colors,
                        navItemIds = allDestinationIds,
                        selectedIndex = effectiveIndex,
                        appearanceNavigationIndex = appearanceNavigationIndex,
                        maxWidth = maxWidth,
                        onSelect = { idx -> selectRootDestination(idx, allDestinationIds.getOrNull(idx)) },
                        fab = { fab(activeDestinationId) },
                        content = { pageContent() },
                    )
                } else {
                    BottomShell(
                        colors = colors,
                        template = template,
                        styleName = prefs.bottomBarStyle,
                        navItemIds = navItemIds,
                        selectedIndex = bottomNavSelectedIndex,
                        primaryCount = primaryIds.size,
                        animationsDisabled = prefs.animationsDisabled,
                        onDestinationTap = { idx ->
                            when {
                                overflowIds.isNotEmpty() && idx == primaryIds.size -> {
                                    // More menu handled by the caller sheet state.
                                    moreMenuOpen = true
                                }
                                idx in primaryIds.indices -> {
                                    val destination = primaryIds[idx]
                                    val realIndex = allDestinationIds.indexOf(destination)
                                    if (realIndex >= 0) selectRootDestination(realIndex, destination)
                                }
                                idx in allDestinationIds.indices -> selectRootDestination(idx)
                            }
                        },
                        onCenterAction = onCenterAction,
                        fab = { fab(activeDestinationId) },
                        content = { pageContent() },
                    )
                }
            }
        }
    }

    if (moreMenuOpen) {
        MoreDestinationsSheet(
            colors = colors,
            overflowIds = overflowIds,
            onSelect = { overflowIdx ->
                overflowIds.getOrNull(overflowIdx)?.let { destination ->
                    val realIndex = allDestinationIds.indexOf(destination)
                    if (realIndex >= 0) selectRootDestination(realIndex, destination)
                }
                moreMenuOpen = false
            },
            onDismiss = { moreMenuOpen = false },
        )
    }
}

/** Dart _buildTransitionPage: the five named 3D pager transitions. Delta is
 *  page index minus live pager position, clamped to [-1, 1]. */
fun Modifier.pagerTransition(transition: String, delta: Float): Modifier = when (transition) {
    "Cube 3D" -> graphicsLayer {
        transformOrigin = TransformOrigin(if (delta >= 0f) 0f else 1f, 0.5f)
        cameraDistance = 16f
        rotationY = delta * 0.62f * 180f / Math.PI.toFloat()
    }
    "Accordion Fold" -> graphicsLayer {
        transformOrigin = TransformOrigin(if (delta >= 0f) 0f else 1f, 0.5f)
        cameraDistance = 16f
        rotationY = delta * 0.28f * 180f / Math.PI.toFloat()
        scaleX = 1f - abs(delta) * 0.025f
    }
    "Card Flip" -> graphicsLayer {
        transformOrigin = TransformOrigin(if (delta >= 0f) 1f else 0f, 0.5f)
        cameraDistance = 14f
        rotationY = delta * 90f
        alpha = (1f - abs(delta) * 0.18f).coerceIn(0f, 1f)
    }
    "Depth Zoom" -> graphicsLayer {
        translationX = delta * 18f
        val scale = (1f - abs(delta) * 0.09f).coerceIn(0.88f, 1f)
        scaleX = scale
        scaleY = scale
        alpha = (1f - abs(delta) * 0.12f).coerceIn(0f, 1f)
    }
    "Stack Rotate" -> graphicsLayer {
        rotationZ = delta * 0.045f * 180f / Math.PI.toFloat()
        val scale = (1f - abs(delta) * 0.045f).coerceIn(0.92f, 1f)
        scaleX = scale
        scaleY = scale
    }
    else -> this
}

/** Port of _navDestinationIcon: the themed registry ChatyIcon whenever the
 *  destination maps to a ChatyIconId (drop9 chatyIconIdForNavDestination);
 *  the Material template glyph only when the mapping is null (Dart keeps
 *  ChatyGlyphIcon there - the synthetic 'more' item and unknown ids). */
@Composable
fun NavDestinationIcon(
    id: String,
    modifier: Modifier = Modifier,
    size: Int = 22,
    tint: Color = Color.Unspecified,
) {
    val registryId = com.example.chat.ui.icons.chatyIconIdForNavDestination(id)
    if (registryId != null) {
        com.example.chat.ui.icons.ChatyIcon(
            registryId,
            semanticLabel = NavigationCatalog.labelFor(id),
            modifier = modifier,
            size = size.dp,
            color = if (tint != Color.Unspecified) tint else null,
        )
        return
    }
    // Null-mapping destinations: only the synthetic 'more' item and unknown
    // ids reach this glyph fallback.
    androidx.compose.material3.Icon(
        Icons.Rounded.MoreHoriz,
        contentDescription = NavigationCatalog.labelFor(id),
        tint = tint,
        modifier = modifier.size(size.dp),
    )
}
