package com.streamlivex.android

import android.annotation.SuppressLint
import android.app.AlertDialog
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

// Geçici teşhis: WebView'de sessizce yutulan JS hatalarını (kategori listesi vb. bomboş
// kalmasına sebep olabilecek render hatalarını) doğrudan ekranda kırmızı bir banner olarak
// gösterir, uzaktan devtools bağlantısı gerektirmez. onPageStarted'da enjekte edilir ki
// sayfanın kendi script'leri çalışmaya başlamadan önce hata yakalayıcılar hazır olsun.
private const val EARLY_DIAGNOSTIC_SCRIPT = """
    (function() {
      if (window.__slxDiag) return;
      window.__slxDiag = true;
      var box = null;
      function show(text) {
        try {
          if (!box) {
            box = document.createElement('div');
            box.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:2147483647;background:#c0142f;color:#fff;font:11px monospace;padding:8px;white-space:pre-wrap;max-height:40vh;overflow:auto;';
            (document.body || document.documentElement).appendChild(box);
          }
          box.textContent += text + '\n\n';
        } catch (e) {}
      }
      window.addEventListener('error', function(e) {
        show('JS HATA: ' + (e.message || e.error) + ' @ ' + (e.filename || '') + ':' + (e.lineno || '') + '\n' + (e.error && e.error.stack || ''));
      });
      window.addEventListener('unhandledrejection', function(e) {
        show('PROMISE HATA: ' + (e.reason && (e.reason.stack || e.reason.message || e.reason)));
      });
      var origError = console.error, origWarn = console.warn;
      console.error = function() { try { show('CONSOLE.ERROR: ' + Array.prototype.slice.call(arguments).join(' ')); } catch(e){}; origError.apply(console, arguments); };
      console.warn = function() { try { show('CONSOLE.WARN: ' + Array.prototype.slice.call(arguments).join(' ')); } catch(e){}; origWarn.apply(console, arguments); };
      function addDiagButton() {
        if (!document.body || document.getElementById('__slxDiagBtn')) return;
        var btn = document.createElement('button');
        btn.id = '__slxDiagBtn';
        btn.textContent = 'TEŞHİS';
        btn.style.cssText = 'position:fixed;bottom:8px;right:8px;z-index:2147483647;background:#111;color:#0f0;border:1px solid #0f0;padding:8px 10px;font:11px monospace;';
        btn.onclick = function() {
          var asideCat = document.querySelector('.catalog aside');
          var catalog = document.querySelector('.catalog');
          var csAside = asideCat ? getComputedStyle(asideCat) : null;
          var csCatalog = catalog ? getComputedStyle(catalog) : null;
          var rectAside = asideCat ? asideCat.getBoundingClientRect() : null;
          var rectCatalog = catalog ? catalog.getBoundingClientRect() : null;
          var report = [
            'UA: ' + navigator.userAgent,
            'gridSupport: ' + (window.CSS && CSS.supports ? CSS.supports('display','grid') : 'CSS.supports yok'),
            'window: ' + innerWidth + 'x' + innerHeight,
            'catalog display: ' + (csCatalog ? csCatalog.display : '-') + ' gridTemplateColumns: ' + (csCatalog ? csCatalog.gridTemplateColumns : '-'),
            'catalog rect: ' + JSON.stringify(rectCatalog),
            'aside display: ' + (csAside ? csAside.display : '-') + ' width: ' + (csAside ? csAside.width : '-') + ' visibility: ' + (csAside ? csAside.visibility : '-') + ' opacity: ' + (csAside ? csAside.opacity : '-') + ' position: ' + (csAside ? csAside.position : '-') + ' zIndex: ' + (csAside ? csAside.zIndex : '-'),
            'aside rect: ' + JSON.stringify(rectAside),
            'aside children: ' + (asideCat ? asideCat.children.length : '-'),
          ].join('\n\n');
          if (window.StreamLiveXDiag && window.StreamLiveXDiag.report) window.StreamLiveXDiag.report(report);
          else show('TEŞHİS RAPORU:\n' + report);
        };
        document.body.appendChild(btn);
      }
      var t = setInterval(addDiagButton, 500);
      setTimeout(function(){ clearInterval(t); }, 30000);
    })();
"""

class DiagBridge(private val context: android.content.Context) {
    @JavascriptInterface
    fun report(text: String) {
        val activity = context.findActivity() ?: return
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("Teşhis Raporu")
                .setMessage(text)
                .setPositiveButton("Kapat", null)
                .show()
        }
    }
}

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
                    addJavascriptInterface(DiagBridge(context), "StreamLiveXDiag")
                    webChromeClient = fileChooser
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                            onNavigationStart()
                            loading = true
                            failed = false
                            view.evaluateJavascript(EARLY_DIAGNOSTIC_SCRIPT, null)
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
