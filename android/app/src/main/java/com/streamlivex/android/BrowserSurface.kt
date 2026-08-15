package com.streamlivex.android

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val BRIDGE_SCRIPT = """
    (function() {
      window.chrome = window.chrome || {};
      window.chrome.webview = {
        postMessage: function(message) {
          window.StreamLiveXAndroid.postMessage(JSON.stringify(message));
        }
      };
      window.dispatchEvent(new Event('streamlivex:native-bridge-ready'));
    })();
"""

class AndroidPlaybackBridge(private val onMessage: (String) -> Unit) {
    @JavascriptInterface
    fun postMessage(payload: String) = onMessage(payload)
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun BrowserSurface(
    url: String,
    bridge: AndroidPlaybackBridge,
    fileChooser: WebChromeClient,
    onWebViewReady: (WebView) -> Unit,
    onNavigationStart: () -> Unit = {},
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val configuredHost = remember(url) { Uri.parse(url).host }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Box(Modifier.fillMaxSize().background(ComposeColor(0xFF080910))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    setBackgroundColor(Color.rgb(8, 9, 16))
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.textZoom = 100
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    if (BuildConfig.DEBUG) settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.allowFileAccess = false
                    settings.allowContentAccess = true
                    settings.userAgentString = "${settings.userAgentString} StreamLiveXAndroid/${BuildConfig.VERSION_NAME}"
                    addJavascriptInterface(bridge, "StreamLiveXAndroid")
                    webChromeClient = fileChooser
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                            onNavigationStart()
                            loading = true
                            failed = false
                        }

                        override fun onPageFinished(view: WebView, pageUrl: String?) {
                            view.evaluateJavascript(BRIDGE_SCRIPT, null)
                            loading = false
                            view.requestFocus()
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                failed = true
                                loading = false
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val destination = request.url
                            val sameApp = destination.scheme in setOf("http", "https") &&
                                (destination.host == configuredHost || destination.host in setOf("localhost", "10.0.2.2"))
                            if (sameApp) return false
                            if (destination.scheme in setOf("http", "https")) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, destination)) }
                            }
                            return true
                        }
                    }
                    loadUrl(url)
                    webView = this
                    onWebViewReady(this)
                    requestFocus()
                }
            },
        )

        if (loading && !failed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.loading_web), color = ComposeColor.White)
            }
        }

        if (failed) {
            Box(
                modifier = Modifier.fillMaxSize().background(ComposeColor(0xFF080910)),
                contentAlignment = Alignment.Center,
            ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.web_error), color = ComposeColor.White)
                Button(onClick = { failed = false; loading = true; webView?.reload() }) {
                    Text(stringResource(R.string.retry))
                }
            }
            }
        }
    }

    LaunchedEffect(webView) {
        webView?.let(onWebViewReady)
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.removeJavascriptInterface("StreamLiveXAndroid")
            webView?.stopLoading()
            webView?.destroy()
        }
    }
}
