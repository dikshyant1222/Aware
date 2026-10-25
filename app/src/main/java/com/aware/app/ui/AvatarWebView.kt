package com.aware.app.ui

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AvatarWebView(
    onWebViewCreated: (WebView) -> Unit,
    onReady: () -> Unit,
    onSigningStarted: (String, String, Int, Int) -> Unit,
    onSigningComplete: (String) -> Unit,
    onError: () -> Unit,
) {
    val readyCallback by rememberUpdatedState(onReady)
    val startCallback by rememberUpdatedState(onSigningStarted)
    val completeCallback by rememberUpdatedState(onSigningComplete)
    val errorCallback by rememberUpdatedState(onError)
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var released by remember { mutableStateOf(false) }

    DisposableEffect(webView, lifecycleOwner) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    view?.onResume()
                    view?.evaluateJavascript("window.setPlaybackPaused?.(false)", null)
                }
                Lifecycle.Event.ON_STOP -> {
                    view?.evaluateJavascript("window.setPlaybackPaused?.(true)", null)
                    view?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { context ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onAvatarReady() {
                        post {
                            if (!released) {
                                val paused = !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                                evaluateJavascript("window.setPlaybackPaused?.($paused)", null)
                                readyCallback()
                            }
                        }
                    }

                    @JavascriptInterface
                    fun onSigningStarted(id: String, lineId: String, start: Int, end: Int) {
                        post { if (!released) startCallback(id, lineId, start, end) }
                    }

                    @JavascriptInterface
                    fun onSigningComplete(id: String) { post { if (!released) completeCallback(id) } }

                    @JavascriptInterface
                    fun onAvatarError(message: String) {
                        Log.e("AwareAvatar", message)
                        post { if (!released) errorCallback() }
                    }
                }, "AwareBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                        loader.shouldInterceptRequest(request.url) ?: WebResourceResponse(
                            "text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)),
                        )

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        Log.e("AwareAvatar", "Asset error: ${error.description}")
                        if (request.isForMainFrame && !released) errorCallback()
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            Log.e("AwareAvatar", "${message.message()} (${message.lineNumber()})")
                        }
                        return true
                    }
                }
                webView = this
                onWebViewCreated(this)
                // Loading before Compose lays out the view can freeze CSS viewport
                // units at zero on Android, leaving a ready but invisible canvas.
                var pageLoaded = false
                addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                    if (right > left && bottom > top && !released) {
                        if (!pageLoaded) {
                            pageLoaded = true
                            loadUrl("https://appassets.androidplatform.net/assets/avatar/embed.html")
                        } else {
                            evaluateJavascript("window.resizeAvatar?.()", null)
                        }
                    }
                }
            }
        },
        onRelease = {
            released = true
            it.stopLoading()
            it.removeJavascriptInterface("AwareBridge")
            it.destroy()
        },
        modifier = Modifier.fillMaxSize(),
    )
}
