package com.example.chat.ui.core.customization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.chat.core.customization.CustomizationController
import com.example.chat.core.customization.CustomizationSnapshot
import com.example.chat.core.customization.CustomizationStateStore
import com.example.chat.ui.core.components.overlays.ChatyOverlayController
import com.example.chat.ui.core.components.overlays.ChatyOverlayHost
import com.example.chat.ui.core.components.overlays.rememberChatyOverlayController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SharedPreferences backing for CustomizationController (Dart: SharedPreferences key
 *  chaty.customization.snapshot_v1, device-global, not per-account). */
class SharedPrefsCustomizationStateStore(context: Context) : CustomizationStateStore {
    private val prefs = context.applicationContext.getSharedPreferences("chaty_customization", Context.MODE_PRIVATE)
    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) { prefs.getString(key, null) }
    override suspend fun setString(key: String, value: String) { withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() } }
}

/** Process-wide singleton equivalent of Dart locator.registerLazySingleton<CustomizationController>.
 *  Non-Compose callers (ServiceLocator) can use [get] too; no edit of shared files is required. */
object CustomizationRuntime {
    @Volatile private var instance: CustomizationController? = null
    fun get(context: Context): CustomizationController =
        instance ?: synchronized(this) {
            instance ?: CustomizationController(SharedPrefsCustomizationStateStore(context)).also { instance = it }
        }
}

/** Dart: locator<CustomizationController>() inside screens. Provided by [ChatyCustomizationRoot]. */
val LocalCustomizationController = staticCompositionLocalOf<CustomizationController> {
    error("LocalCustomizationController not provided: wrap the app in ChatyCustomizationRoot")
}

/** Dart: ChatyToast / ChatyOverlayRenderer calls. Provided by [ChatyCustomizationRoot]. */
val LocalChatyOverlayController = staticCompositionLocalOf<ChatyOverlayController> {
    error("LocalChatyOverlayController not provided: wrap the app in ChatyCustomizationRoot")
}

/** For the ComponentStylesScreen call site: returns the loaded singleton (loads on first use, Dart ctor _load). */
@Composable
fun rememberLoadedCustomizationController(): CustomizationController {
    val ctx = LocalContext.current
    val controller = remember { CustomizationRuntime.get(ctx) }
    LaunchedEffect(controller) { if (!controller.isLoaded) controller.load() }
    return controller
}

/**
 * Optional root integration: wrap the existing content INSIDE ChatyTheme { ... } with
 *   ChatyCustomizationRoot { <existing AppRoot content> }
 * It (1) creates/loads the singleton controller once (Dart ctor _load), (2) provides both locals,
 * (3) only when hostOverlay = true, renders one ChatyOverlayHost for app-wide toasts/dialogs.
 */
@Composable
fun ChatyCustomizationRoot(hostOverlay: Boolean = false, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val controller = remember { CustomizationRuntime.get(ctx) }
    val overlay = rememberChatyOverlayController()
    LaunchedEffect(controller) { if (!controller.isLoaded) controller.load() }
    CompositionLocalProvider(LocalCustomizationController provides controller, LocalChatyOverlayController provides overlay) {
        androidx.compose.foundation.layout.Box {
            content()
            // ComponentStylesScreen (head 45e94b8) hosts its own ChatyOverlayHost for the controller it is given;
            // a second host on the same controller would show every dialog twice, so the root host is opt-in.
            if (hostOverlay) ChatyOverlayHost(overlay)
        }
    }
}

/** ListenableBuilder equivalent: recomposes when the controller notifies (snapshot/preview/undo state changes).
 *  Read controller.effectiveSnapshot / isDirty / canUndo / previewSnapshot via the returned tick-driven state. */
@Composable
fun CustomizationController.observeAsState(): State<CustomizationController> {
    val state = remember(this) { mutableStateOf(this, androidx.compose.runtime.neverEqualPolicy()) }
    DisposableEffect(this) {
        val l: () -> Unit = { state.value = this@observeAsState }
        addListener(l)
        onDispose { removeListener(l) }
    }
    return state
}

/** Convenience: the effective (preview-aware) snapshot as Compose state. */
@Composable
fun CustomizationController.effectiveSnapshotState(): State<CustomizationSnapshot> {
    val s = observeAsState()
    return remember(s) { object : State<CustomizationSnapshot> { override val value get() = s.value.effectiveSnapshot } }
}
