package com.streamlivex.android

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.json.JSONObject
import java.util.UUID

@UnstableApi
class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var playbackRequest by mutableStateOf<PlaybackRequest?>(null)
    private var inlinePlayback by mutableStateOf<InlinePlaybackSession?>(null)
    private var lastProgress = PlaybackProgress()
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    // On izlemeden (inline) tam ekrana geçerken aynı ExoPlayer'ı yeniden kullanmak için --
    // böylece yayın sıfırdan başlamak yerine zaten çalan haliyle tam ekrana taşınır.
    // Player, sessionId'ye hem inlinePlayback hem playbackRequest tarafından referans
    // verilmediği anda ("orphan" olduğunda) serbest bırakılır.
    private val sharedPlayers = mutableMapOf<String, ExoPlayer>()

    private fun playerFor(request: PlaybackRequest): ExoPlayer {
        sharedPlayers[request.sessionId]?.let { return it }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0 StreamLiveX-Android/${BuildConfig.VERSION_NAME}")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory))
            .build()
            .apply {
                playWhenReady = true
                playbackParameters = PlaybackParameters(request.preferences.playbackRate)
                trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
                    val audio = request.preferences.audioLanguage
                    if (audio !in setOf("auto", "original")) setPreferredAudioLanguage(audio)
                    val subtitle = request.preferences.subtitleLanguage
                    if (subtitle != "auto") setPreferredTextLanguage(subtitle)
                    setSelectUndeterminedTextLanguage(request.preferences.subtitleMode != "off")
                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, request.preferences.subtitleMode == "off")
                }.build()
            }
        sharedPlayers[request.sessionId] = player
        return player
    }

    private fun releaseOrphanedPlayer(sessionId: String?) {
        if (sessionId == null) return
        val stillNeeded = inlinePlayback?.request?.sessionId == sessionId || playbackRequest?.sessionId == sessionId
        if (!stillNeeded) {
            sharedPlayers.remove(sessionId)?.release()
            PlaybackCandidateMemory.forget(sessionId)
        }
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCallback = null
    }

    private val fileChooser = object : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            filePicker.launch("*/*")
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        handleDeepLink(intent)

        val bridge = AndroidPlaybackBridge { payload ->
            runCatching { BridgeMessageParser.parse(payload) }
                .onSuccess { command -> runOnUiThread { handleBridgeCommand(command) } }
                .onFailure { sendWebEvent("streamlivex:native-player-error", JSONObject.quote(getString(R.string.stream_failed))) }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF765BFF),
                    secondary = androidx.compose.ui.graphics.Color(0xFFEC2F97),
                    background = androidx.compose.ui.graphics.Color(0xFF080910),
                ),
            ) {
                Box(Modifier.fillMaxSize()) {
                    BrowserSurface(
                        url = BuildConfig.WEB_APP_URL,
                        bridge = bridge,
                        fileChooser = fileChooser,
                        onWebViewReady = { webView = it },
                        onNavigationStart = {
                            releaseOrphanedPlayer(inlinePlayback?.request?.sessionId)
                            inlinePlayback = null
                        },
                    )
                    inlinePlayback?.takeIf { it.bounds.visible && playbackRequest == null }?.let { session ->
                        NativeInlinePlayerSurface(
                            request = session.request,
                            player = playerFor(session.request),
                            modifier = Modifier
                                .offset(session.bounds.left.dp, session.bounds.top.dp)
                                .size(session.bounds.width.dp, session.bounds.height.dp),
                            onFullScreen = {
                                lastProgress = PlaybackProgress()
                                // inlinePlayback bilerek temizlenmiyor: tam ekrandan geri
                                // dönüldüğünde ayni oturum otomatik olarak on izlemeye geri
                                // döner (bkz. yukarıdaki takeIf { playbackRequest == null }).
                                playbackRequest = session.request
                            },
                            onFailure = { message ->
                                sendWebEvent("streamlivex:native-preview-error", JSONObject.quote(message))
                            },
                        )
                    }
                    playbackRequest?.let { request ->
                        BackHandler { closePlayer(notifyWeb = true) }
                        NativePlayerSurface(
                            request = request,
                            player = playerFor(request),
                            onClose = { progress ->
                                lastProgress = progress
                                closePlayer(notifyWeb = true)
                            },
                            onProgress = { progress ->
                                lastProgress = progress
                                if (!request.item.isLive) sendProgress(progress)
                            },
                            onFailure = { message -> sendWebEvent("streamlivex:native-player-error", JSONObject.quote(message)) },
                        )
                    }
                }
            }
        }
        window.decorView.post { enterImmersiveMode() }
    }

    private val dpadKeys = setOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
    )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (playbackRequest == null && event.keyCode in dpadKeys) {
            val view = webView
            if (view != null && view.dispatchKeyEvent(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleBridgeCommand(command: BridgeCommand?) {
        when (command) {
            is BridgeCommand.Play -> {
                val orphanedPlayback = playbackRequest?.sessionId
                val orphanedPreview = inlinePlayback?.request?.sessionId
                lastProgress = PlaybackProgress()
                inlinePlayback = null
                playbackRequest = command.request
                releaseOrphanedPlayer(orphanedPlayback)
                releaseOrphanedPlayer(orphanedPreview)
            }
            is BridgeCommand.Close -> {
                val active = playbackRequest
                val matchingSession = !command.sessionId.isNullOrBlank() && command.sessionId == active?.sessionId
                val genericWebClose = command.sessionId.isNullOrBlank() && active?.fromWebBridge == true
                if (matchingSession || genericWebClose) {
                    playbackRequest = null
                    releaseOrphanedPlayer(active?.sessionId)
                }
            }
            is BridgeCommand.Preview -> {
                val orphaned = inlinePlayback?.request?.sessionId
                inlinePlayback = command.session
                releaseOrphanedPlayer(orphaned)
            }
            is BridgeCommand.PreviewLayout -> {
                val active = inlinePlayback
                if (active?.request?.sessionId == command.sessionId) {
                    inlinePlayback = active.copy(bounds = command.bounds)
                }
            }
            is BridgeCommand.ClosePreview -> {
                val active = inlinePlayback
                if (command.sessionId.isNullOrBlank() || command.sessionId == active?.request?.sessionId) {
                    inlinePlayback = null
                    releaseOrphanedPlayer(active?.request?.sessionId)
                }
            }
            is BridgeCommand.PromotePreview -> {
                val active = inlinePlayback
                if (active?.request?.sessionId == command.sessionId) {
                    lastProgress = PlaybackProgress()
                    // inlinePlayback bilerek temizlenmiyor, bkz. onFullScreen yorumu.
                    playbackRequest = active.request
                }
            }
            null -> Unit
        }
    }

    private fun closePlayer(notifyWeb: Boolean) {
        val active = playbackRequest ?: return
        playbackRequest = null
        releaseOrphanedPlayer(active.sessionId)
        if (notifyWeb) {
            val detail = JSONObject()
                .put("current", lastProgress.currentSeconds)
                .put("duration", lastProgress.durationSeconds)
                .put("sessionId", active.sessionId)
            sendWebEvent("streamlivex:native-player-closed", detail.toString())
        }
    }

    private fun sendProgress(progress: PlaybackProgress) {
        val detail = JSONObject()
            .put("current", progress.currentSeconds)
            .put("duration", progress.durationSeconds)
        sendWebEvent("streamlivex:native-player-progress", detail.toString())
    }

    private fun sendWebEvent(name: String, detailJson: String) {
        val script = "window.dispatchEvent(new CustomEvent(${JSONObject.quote(name)}, { detail: $detailJson }));"
        webView?.post { webView?.evaluateJavascript(script, null) }
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "streamlivex" || uri.host != "play") return
        val source = uri.getQueryParameter("url")?.trim().orEmpty()
        val sourceUri = Uri.parse(source)
        if (source.isBlank() || sourceUri.scheme?.lowercase() !in setOf("http", "https")) return
        lastProgress = PlaybackProgress()
        playbackRequest = PlaybackRequest(
            sessionId = UUID.randomUUID().toString(),
            item = PlaybackItem(
                name = uri.getQueryParameter("title") ?: "StreamLiveX",
                url = source,
                kind = uri.getQueryParameter("kind") ?: "movie",
            ),
        )
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        inlinePlayback = null
        playbackRequest = null
        sharedPlayers.values.forEach { it.release() }
        sharedPlayers.clear()
        webView = null
        super.onDestroy()
    }
}
