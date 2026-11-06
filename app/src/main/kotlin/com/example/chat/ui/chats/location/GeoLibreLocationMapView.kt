package com.example.chat.ui.chats.location

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import org.json.JSONObject

/** Local GeoLibre map only. Host owns foreground permissions, draft preview and encrypted send. */
@SuppressLint("SetJavaScriptEnabled")
class GeoLibreLocationMapView(
    context: Context,
    private val onPinSelected: (LocationCoordinate) -> Unit,
    private val onUnavailable: (String) -> Unit,
    private val onAttributionLink: (Uri) -> Unit,
    private val onViewChanged: (Boolean) -> Unit = {},
    private val onStyleChanged: (String) -> Unit = {},
    private val nativeLayers: Boolean = false,
    systemDark: Boolean = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES,
    private val onMapReady: () -> Unit = {},
    private val tileRequestsAllowed: () -> Boolean = { true },
    private val onTileRequestDecision: (Boolean) -> Unit = {},
) : WebView(context) {
    private val bridge = LocationMapBridgePolicy()
    private val assets = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
    private var darkTheme = systemDark
    private var ready = false
    private var pendingPin: LocationCoordinate? = null
    @Volatile private var disposed = false
    private var nativeDestroyed = false
    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val forceCleanup = Runnable { finishNativeCleanup() }

    init {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString ChatyLocation/1.0"
        }
        webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }
            override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                if (disposed) return blocked()
                val uri = request.url
                if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return blocked()
                if (uri.host == "appassets.androidplatform.net" && uri.path?.startsWith("/assets/location-map/") == true) {
                    return assets.shouldInterceptRequest(uri) ?: blocked()
                }
                if (uri.host == "tiles.openfreemap.org" && request.method == "GET") {
                    val allowed = tileRequestsAllowed()
                    onTileRequestDecision(allowed)
                    return if (allowed) null else blocked()
                }
                return blocked()
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (request.isForMainFrame && request.hasGesture() && uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
                    uri.host in setOf("openfreemap.org", "www.openmaptiles.org", "openmaptiles.org", "www.openstreetmap.org", "openstreetmap.org", "maplibre.org")) {
                    onAttributionLink(uri)
                }
                return true // No page navigation, redirects, intent: or arbitrary URL execution.
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!disposed && url == PAGE_URL) evaluateJavascript("window.ChatyGeoLibre.initialize('${bridge.session}',$nativeLayers,$darkTheme)", null)
            }
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            onUnavailable("Update Android System WebView to use the map.")
        } else {
            WebViewCompat.addWebMessageListener(this, "ChatyMap", setOf(LocationMapBridgePolicy.LOCAL_ORIGIN)) { _, message, origin, mainFrame, _ ->
                if (message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
                if (disposed) return@addWebMessageListener
                val text = message.data ?: return@addWebMessageListener
                if (text.length > LocationMapBridgePolicy.MAX_MESSAGE_LENGTH) return@addWebMessageListener
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return@addWebMessageListener
                val fields = json.keys().asSequence().associateWith { json.opt(it) }
                when (val event = bridge.validate(origin.toString(), mainFrame, text.length, fields)) {
                    LocationMapEvent.Ready -> { if (ready) return@addWebMessageListener; ready = true; pendingPin?.let { showPin(it) }; onMapReady() }
                    is LocationMapEvent.ViewChanged -> onViewChanged(event.buildings)
                    is LocationMapEvent.StyleChanged -> onStyleChanged(event.style)
                    is LocationMapEvent.PinSelected -> onPinSelected(event.coordinate)
                    null -> Unit
                }
            }
            setBackgroundColor(if (darkTheme) android.graphics.Color.rgb(16,28,34) else android.graphics.Color.rgb(248,250,251))
            loadUrl(PAGE_URL)
        }
    }

    /** Called only with a validated draft or a user-requested one-time foreground fix. */
    fun showPin(coordinate: LocationCoordinate) {
        if (disposed) return
        pendingPin = coordinate
        if (ready && !disposed) evaluateJavascript(
            "window.ChatyGeoLibre.setPin(${coordinate.latitude},${coordinate.longitude},'${bridge.session}')", null,
        )
    }

    fun setTheme(dark: Boolean) {
        if (disposed) return
        darkTheme = dark
        setBackgroundColor(if (dark) android.graphics.Color.rgb(16,28,34) else android.graphics.Color.rgb(248,250,251))
        evaluateJavascript("window.ChatyGeoLibre?.setTheme($dark,'${bridge.session}')", null)
    }

    fun control(action: String) {
        if (disposed || !ready || action !in setOf("zoomIn", "zoomOut", "toggleBuildings")) return
        evaluateJavascript("window.ChatyGeoLibre.control('${action}','${bridge.session}')", null)
    }

    fun selectStyle(style: String) {
        if (disposed || !ready || style !in setOf("liberty", "positron", "dark", "bright", "fiord")) return
        evaluateJavascript("window.ChatyGeoLibre.selectStyle('${style}','${bridge.session}')", null)
    }

    /** Host must call from AndroidView disposal. No permission or sharing continues afterward. */
    fun disposeMap() {
        if (disposed) return
        disposed = true
        // JS destroys GeoLibre layers, GL context and workers before the native WebView goes away.
        cleanupHandler.postDelayed(forceCleanup, 500L)
        try {
            evaluateJavascript("window.ChatyGeoLibre?.dispose()") { finishNativeCleanup() }
        } catch (_: RuntimeException) {
            finishNativeCleanup()
        }
        ready = false
        pendingPin = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(this, "ChatyMap")
    }


    private fun finishNativeCleanup() {
        if (nativeDestroyed) return
        nativeDestroyed = true
        cleanupHandler.removeCallbacks(forceCleanup)
        stopLoading()
        removeAllViews()
        destroy()
    }

    companion object {
        private const val PAGE_URL = "https://appassets.androidplatform.net/assets/location-map/index.html"
        private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}
