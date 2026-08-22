package com.streamlivex.android

import com.streamlivex.android.tv.TvRoot
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import java.util.Locale
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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

    private var playbackRequest by
        mutableStateOf<PlaybackRequest?>(null)

    private var inlinePlayback by
        mutableStateOf<InlinePlaybackSession?>(null)

    private var lastProgress =
        PlaybackProgress()

    private var fileCallback:
        ValueCallback<Array<Uri>>? = null

    /*
     * Tam ekran oynaticida PlayerView'in
     * gercek Android View odagini
     * guvenilir sekilde alamadigi durumlarda
     * kumanda tuslarini dogrudan Compose
     * tarafina aktariyoruz.
     */
    private var externalPlayerKeyEvent by
        mutableStateOf<Triple<Int, Int, Long>?>(null)

    /*
     * Native Android TV ekraninda tam ekran
     * player acikken MainActivity'nin
     * dispatchKeyEvent'i DPAD tuslarini
     * TvRoot / LiveTvScreen tarafina iletsin.
     */
    private var tvFullscreenActive by
        mutableStateOf(false)

    /*
     * On izlemeden tam ekrana gecerken ayni
     * ExoPlayer'i yeniden kullanmak icin.
     *
     * sessionId ayni oldugu surece preview
     * ve fullscreen ayni player instance'ini
     * kullanir.
     */
    private val sharedPlayers =
        mutableMapOf<String, ExoPlayer>()

    /*
     * Native oynatici metinlerini uygulama
     * diliyle eslestir.
     */
    override fun attachBaseContext(
        newBase: Context,
    ) {
        val lang =
            newBase
                .getSharedPreferences(
                    "streamlivex",
                    MODE_PRIVATE,
                )
                .getString(
                    "app-locale",
                    null,
                )

        if (lang.isNullOrBlank()) {
            super.attachBaseContext(
                newBase,
            )
            return
        }

        val locale =
            Locale.forLanguageTag(lang)

        Locale.setDefault(locale)

        val config =
            Configuration(
                newBase.resources.configuration,
            )

        config.setLocale(locale)

        super.attachBaseContext(
            newBase.createConfigurationContext(
                config,
            ),
        )
    }

    private fun syncAppLocaleFromCookie() {
        val cookies =
            CookieManager
                .getInstance()
                .getCookie(
                    BuildConfig.WEB_APP_URL,
                )
                ?: return

        val lang =
            Regex(
                "slx-language=([A-Za-z-]+)",
            )
                .find(cookies)
                ?.groupValues
                ?.getOrNull(1)
                ?: return

        val prefs =
            getSharedPreferences(
                "streamlivex",
                MODE_PRIVATE,
            )

        if (
            prefs.getString(
                "app-locale",
                null,
            ) != lang
        ) {
            prefs
                .edit()
                .putString(
                    "app-locale",
                    lang,
                )
                .apply()
        }
    }

    /*
     * Ayni sessionId icin ayni player.
     *
     * Native TV tarafinda:
     * preview -> fullscreen
     * gecisinde bu kullanilacak.
     */
    private fun playerFor(
        request: PlaybackRequest,
    ): ExoPlayer {

        sharedPlayers[
            request.sessionId
        ]?.let {
            return it
        }

        val httpFactory =
            DefaultHttpDataSource
                .Factory()
                .setAllowCrossProtocolRedirects(
                    true,
                )
                .setUserAgent(
                    "VLC/3.0 StreamLiveX-Android/${BuildConfig.VERSION_NAME}",
                )
                .setConnectTimeoutMs(
                    15_000,
                )
                .setReadTimeoutMs(
                    30_000,
                )

        val player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        this,
                    ).setDataSourceFactory(
                        httpFactory,
                    ),
                )
                .build()
                .apply {

                    playWhenReady = true

                    playbackParameters =
                        PlaybackParameters(
                            request
                                .preferences
                                .playbackRate,
                        )

                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .apply {

                                val audio =
                                    request
                                        .preferences
                                        .audioLanguage

                                if (
                                    audio !in
                                    setOf(
                                        "auto",
                                        "original",
                                    )
                                ) {
                                    setPreferredAudioLanguage(
                                        audio,
                                    )
                                }

                                val subtitle =
                                    request
                                        .preferences
                                        .subtitleLanguage

                                if (
                                    subtitle !=
                                    "auto"
                                ) {
                                    setPreferredTextLanguage(
                                        subtitle,
                                    )
                                }

                                setSelectUndeterminedTextLanguage(
                                    request
                                        .preferences
                                        .subtitleMode !=
                                        "off",
                                )

                                setTrackTypeDisabled(
                                    C.TRACK_TYPE_TEXT,
                                    request
                                        .preferences
                                        .subtitleMode ==
                                        "off",
                                )
                            }
                            .build()
                }

        sharedPlayers[
            request.sessionId
        ] = player

        return player
    }

    /*
     * Web tarafindaki player akisi icin
     * mevcut orphan kontrolunu koruyoruz.
     *
     * Native TV LiveTvScreen kendi
     * yasam dongusunde releasePlayer()
     * cagiracak.
     */
    private fun releaseOrphanedPlayer(
        sessionId: String?,
    ) {
        if (sessionId == null) {
            return
        }

        val stillNeeded =
            inlinePlayback
                ?.request
                ?.sessionId ==
                sessionId ||
                playbackRequest
                    ?.sessionId ==
                    sessionId

        if (!stillNeeded) {

            val player =
                sharedPlayers.remove(
                    sessionId,
                )

            PlaybackCandidateMemory
                .forget(
                    sessionId,
                )

            PlaybackStartTracker
                .clearSession(
                    sessionId,
                )

            /*
             * MediaCodec release zayif TV'lerde
             * gecisi dondurmasin.
             */
            player?.let {
                window.decorView.post {
                    it.release()
                }
            }
        }
    }

    private val filePicker =
        registerForActivityResult(
            ActivityResultContracts
                .GetContent(),
        ) { uri ->

            fileCallback
                ?.onReceiveValue(
                    uri?.let {
                        arrayOf(it)
                    },
                )

            fileCallback = null
        }

    private val fileChooser =
        object : WebChromeClient() {

            override fun onConsoleMessage(
                message:
                    android.webkit
                        .ConsoleMessage,
            ): Boolean {

                android.util.Log.d(
                    "StreamLiveXWeb",
                    "${message.message()} (${message.sourceId()}:${message.lineNumber()})",
                )

                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback:
                    ValueCallback<Array<Uri>>?,
                fileChooserParams:
                    FileChooserParams?,
            ): Boolean {

                fileCallback
                    ?.onReceiveValue(
                        null,
                    )

                fileCallback =
                    filePathCallback

                filePicker.launch(
                    "*/*",
                )

                return true
            }
        }

    override fun onPause() {
        super.onPause()

        syncAppLocaleFromCookie()
    }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )

        WebView
            .setWebContentsDebuggingEnabled(
                BuildConfig.DEBUG,
            )

        handleDeepLink(intent)

        val bridge =
            AndroidPlaybackBridge {
                    payload ->

                runCatching {
                    BridgeMessageParser
                        .parse(payload)
                }
                    .onSuccess {
                            command ->

                        runOnUiThread {
                            handleBridgeCommand(
                                command,
                            )
                        }
                    }
                    .onFailure {

                        sendWebEvent(
                            "streamlivex:native-player-error",
                            JSONObject.quote(
                                getString(
                                    R.string
                                        .stream_failed,
                                ),
                            ),
                        )
                    }
            }

        setContent {

            val isTvDevice =
                (
                    resources
                        .configuration
                        .uiMode and
                        Configuration
                            .UI_MODE_TYPE_MASK
                    ) ==
                    Configuration
                        .UI_MODE_TYPE_TELEVISION

            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary =
                            androidx
                                .compose
                                .ui
                                .graphics
                                .Color(
                                    0xFF765BFF,
                                ),
                        secondary =
                            androidx
                                .compose
                                .ui
                                .graphics
                                .Color(
                                    0xFFEC2F97,
                                ),
                        background =
                            androidx
                                .compose
                                .ui
                                .graphics
                                .Color(
                                    0xFF080910,
                                ),
                    ),
            ) {

                /*
                 * ==================================
                 * NATIVE ANDROID TV
                 * ==================================
                 */
                if (isTvDevice) {

                    TvRoot(
                        playerFor = {
                                request ->

                            playerFor(
                                request,
                            )
                        },
                        releasePlayer = {
                                sessionId ->

                            releaseOrphanedPlayer(
                                sessionId,
                            )
                        },
                        externalPlayerKeyEvent =
                            externalPlayerKeyEvent,
                        onFullscreenStateChanged = {
                                active ->

                            tvFullscreenActive =
                                active
                        },
                    )

                    return@MaterialTheme
                }

                /*
                 * ==================================
                 * TELEFON / WEBVIEW UYGULAMASI
                 * ==================================
                 */
                Box(
                    Modifier.fillMaxSize(),
                ) {

                    BrowserSurface(
                        url =
                            BuildConfig
                                .WEB_APP_URL,
                        bridge = bridge,
                        fileChooser =
                            fileChooser,
                        onWebViewReady = {
                            webView = it
                        },
                        onNavigationStart = {

                            releaseOrphanedPlayer(
                                inlinePlayback
                                    ?.request
                                    ?.sessionId,
                            )

                            inlinePlayback =
                                null
                        },
                        onHardwareBack = {
                            sendWebEvent(
                                "streamlivex:hardware-back",
                                "{}",
                            )
                        },
                    )

                    /*
                     * WEBVIEW INLINE PREVIEW
                     */
                    inlinePlayback
                        ?.takeIf {
                            it.bounds.visible &&
                                playbackRequest ==
                                null
                        }
                        ?.let { session ->

                            NativeInlinePlayerSurface(
                                request =
                                    session.request,
                                player =
                                    playerFor(
                                        session.request,
                                    ),
                                modifier =
                                    Modifier
                                        .offset(
                                            session
                                                .bounds
                                                .left
                                                .dp,
                                            session
                                                .bounds
                                                .top
                                                .dp,
                                        )
                                        .size(
                                            session
                                                .bounds
                                                .width
                                                .dp,
                                            session
                                                .bounds
                                                .height
                                                .dp,
                                        ),
                                onFullScreen = {

                                    lastProgress =
                                        PlaybackProgress()

                                    /*
                                     * inlinePlayback
                                     * temizlenmiyor.
                                     *
                                     * Tam ekrandan geri
                                     * gelince ayni player
                                     * preview olarak devam.
                                     */
                                    playbackRequest =
                                        session.request
                                },
                                onFailure = {
                                        message ->

                                    sendWebEvent(
                                        "streamlivex:native-preview-error",
                                        JSONObject.quote(
                                            message,
                                        ),
                                    )
                                },
                            )
                        }

                    /*
                     * WEBVIEW FULLSCREEN PLAYER
                     */
                    playbackRequest
                        ?.let { request ->

                            BackHandler {
                                closePlayer(
                                    notifyWeb =
                                        true,
                                )
                            }

                            NativePlayerSurface(
                                request =
                                    request,
                                player =
                                    playerFor(
                                        request,
                                    ),
                                externalKeyEvent =
                                    externalPlayerKeyEvent,
                                onClose = {
                                        progress ->

                                    lastProgress =
                                        progress

                                    closePlayer(
                                        notifyWeb =
                                            true,
                                    )
                                },
                                onProgress = {
                                        progress ->

                                    lastProgress =
                                        progress

                                    if (
                                        !request
                                            .item
                                            .isLive
                                    ) {
                                        sendProgress(
                                            progress,
                                        )
                                    }
                                },
                                onFailure = {
                                        message ->

                                    sendWebEvent(
                                        "streamlivex:native-player-error",
                                        JSONObject.quote(
                                            message,
                                        ),
                                    )
                                },
                                onPreferencesChanged = {
                                        prefs ->

                                    val detail =
                                        JSONObject()
                                            .put(
                                                "subtitleMode",
                                                prefs.subtitleMode,
                                            )
                                            .put(
                                                "subtitleLanguage",
                                                prefs.subtitleLanguage,
                                            )
                                            .put(
                                                "subtitleSize",
                                                prefs.subtitleSize,
                                            )
                                            .put(
                                                "subtitleColor",
                                                prefs.subtitleColor,
                                            )
                                            .put(
                                                "subtitleBackground",
                                                prefs.subtitleBackground,
                                            )

                                    sendWebEvent(
                                        "streamlivex:native-settings",
                                        detail.toString(),
                                    )
                                },
                                onNext = {

                                    sendWebEvent(
                                        "streamlivex:native-player-next",
                                        "{}",
                                    )
                                },
                            )
                        }
                }
            }
        }

        window.decorView.post {
            enterImmersiveMode()
        }
    }

    /*
     * Android TV / kumanda tuslari.
     */
    private val dpadKeys =
        setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
        )

    override fun dispatchKeyEvent(
        event: KeyEvent,
    ): Boolean {

        /*
         * Web player VEYA native TV fullscreen
         * player aciksa DPAD tuslarini dogrudan
         * player state'ine aktar.
         */
        if (
            tvFullscreenActive &&
            event.keyCode in
            dpadKeys
        ) {

            /*
             * Film/dizi native Compose oynaticisi kendi odak agacini
             * kullanir. Once olayi ekrana ver; bir dugme veya oynatici
             * kok katmani islediyse burada bitir. Eski kod olayi daha
             * Compose'a ulasmadan tukettigi icin kumanda calismiyordu.
             */
            if (super.dispatchKeyEvent(event)) {
                return true
            }

            /*
             * LiveTvScreen AndroidView tabanli oldugu icin islenmeyen
             * tuslari mevcut harici olay yoluna aktarmaya devam et.
             */
            externalPlayerKeyEvent =
                Triple(
                    event.keyCode,
                    event.action,
                    System.nanoTime(),
                )

            return true
        }

        if (
            playbackRequest != null &&
            event.keyCode in
            dpadKeys
        ) {

            externalPlayerKeyEvent =
                Triple(
                    event.keyCode,
                    event.action,
                    System.nanoTime(),
                )

            return true
        }

        /*
         * ESC:
         * emulator / fiziksel klavye testi.
         */
        if (
            playbackRequest ==
                null &&
            event.action ==
                KeyEvent.ACTION_DOWN &&
            event.keyCode ==
                KeyEvent.KEYCODE_ESCAPE
        ) {

            val imm =
                getSystemService(
                    Context
                        .INPUT_METHOD_SERVICE,
                ) as
                    InputMethodManager

            if (imm.isAcceptingText) {

                imm
                    .hideSoftInputFromWindow(
                        webView
                            ?.windowToken,
                        0,
                    )

                webView
                    ?.clearFocus()

                return true
            }
        }

        /*
         * WebView DPAD davranisi.
         *
         * Native Android TV TvRoot aktifken
         * webView zaten null olacagi icin
         * burasi devreye girmez.
         */
        if (
            playbackRequest ==
                null &&
            event.keyCode in
            dpadKeys
        ) {

            val view =
                webView

            if (
                view != null &&
                view.dispatchKeyEvent(
                    event,
                )
            ) {

                val imm =
                    getSystemService(
                        Context
                            .INPUT_METHOD_SERVICE,
                    ) as
                        InputMethodManager

                if (
                    event.keyCode ==
                    KeyEvent
                        .KEYCODE_DPAD_CENTER ||
                    event.keyCode ==
                    KeyEvent
                        .KEYCODE_ENTER
                ) {

                    view.post {

                        view
                            .evaluateJavascript(
                                "(function(){var e=document.activeElement;if(!e)return false;var t=e.tagName;return t==='INPUT'||t==='TEXTAREA'||e.isContentEditable===true})()",
                            ) {
                                    result ->

                                if (
                                    result ==
                                    "true"
                                ) {
                                    imm
                                        .showSoftInput(
                                            view,
                                            InputMethodManager
                                                .SHOW_IMPLICIT,
                                        )
                                }
                            }
                    }

                } else if (
                    event.keyCode ==
                    KeyEvent
                        .KEYCODE_DPAD_UP ||
                    event.keyCode ==
                    KeyEvent
                        .KEYCODE_DPAD_DOWN
                ) {

                    imm
                        .hideSoftInputFromWindow(
                            view.windowToken,
                            0,
                        )
                }

                return true
            }
        }

        return super
            .dispatchKeyEvent(
                event,
            )
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean,
    ) {
        super
            .onWindowFocusChanged(
                hasFocus,
            )

        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    /*
     * Android TV / telefon tam ekran.
     */
    private fun enterImmersiveMode() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {

            window.attributes =
                window.attributes
                    .apply {

                        layoutInDisplayCutoutMode =
                            WindowManager
                                .LayoutParams
                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {

            window
                .setDecorFitsSystemWindows(
                    false,
                )

            window
                .insetsController
                ?.apply {

                    hide(
                        WindowInsets
                            .Type
                            .systemBars(),
                    )

                    systemBarsBehavior =
                        WindowInsetsController
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }

        } else {

            @Suppress(
                "DEPRECATION",
            )
            window
                .decorView
                .systemUiVisibility =
                (
                    View
                        .SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View
                            .SYSTEM_UI_FLAG_FULLSCREEN or
                        View
                            .SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View
                            .SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View
                            .SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View
                            .SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }

    override fun onNewIntent(
        intent: Intent,
    ) {
        super.onNewIntent(
            intent,
        )

        setIntent(intent)

        handleDeepLink(
            intent,
        )
    }

    /*
     * WebView -> native player bridge.
     */
    private fun handleBridgeCommand(
        command: BridgeCommand?,
    ) {
        when (command) {

            is BridgeCommand.Play -> {

                val orphanedPlayback =
                    playbackRequest
                        ?.sessionId

                val orphanedPreview =
                    inlinePlayback
                        ?.request
                        ?.sessionId

                lastProgress =
                    PlaybackProgress()

                inlinePlayback =
                    null

                playbackRequest =
                    command.request

                releaseOrphanedPlayer(
                    orphanedPlayback,
                )

                releaseOrphanedPlayer(
                    orphanedPreview,
                )
            }

            is BridgeCommand.Close -> {

                val active =
                    playbackRequest

                val matchingSession =
                    !command
                        .sessionId
                        .isNullOrBlank() &&
                        command.sessionId ==
                        active?.sessionId

                val genericWebClose =
                    command
                        .sessionId
                        .isNullOrBlank() &&
                        active
                            ?.fromWebBridge ==
                        true

                if (
                    matchingSession ||
                    genericWebClose
                ) {
                    playbackRequest =
                        null

                    releaseOrphanedPlayer(
                        active
                            ?.sessionId,
                    )
                }
            }

            is BridgeCommand.Preview -> {

                val orphaned =
                    inlinePlayback
                        ?.request
                        ?.sessionId

                inlinePlayback =
                    command.session

                releaseOrphanedPlayer(
                    orphaned,
                )
            }

            is BridgeCommand.PreviewLayout -> {

                val active =
                    inlinePlayback

                if (
                    active
                        ?.request
                        ?.sessionId ==
                    command.sessionId
                ) {
                    inlinePlayback =
                        active.copy(
                            bounds =
                                command.bounds,
                        )
                }
            }

            is BridgeCommand.ClosePreview -> {

                val active =
                    inlinePlayback

                if (
                    command
                        .sessionId
                        .isNullOrBlank() ||
                    command.sessionId ==
                    active
                        ?.request
                        ?.sessionId
                ) {

                    inlinePlayback =
                        null

                    releaseOrphanedPlayer(
                        active
                            ?.request
                            ?.sessionId,
                    )
                }
            }

            is BridgeCommand.PromotePreview -> {

                val active =
                    inlinePlayback

                if (
                    active
                        ?.request
                        ?.sessionId ==
                    command.sessionId
                ) {

                    lastProgress =
                        PlaybackProgress()

                    playbackRequest =
                        active.request
                }
            }

            is BridgeCommand.ConfirmExit -> {
                finish()
            }

            null -> Unit
        }
    }

    private fun closePlayer(
        notifyWeb: Boolean,
    ) {
        val active =
            playbackRequest
                ?: return

        playbackRequest =
            null

        releaseOrphanedPlayer(
            active.sessionId,
        )

        if (notifyWeb) {

            val detail =
                JSONObject()
                    .put(
                        "current",
                        lastProgress
                            .currentSeconds,
                    )
                    .put(
                        "duration",
                        lastProgress
                            .durationSeconds,
                    )
                    .put(
                        "sessionId",
                        active.sessionId,
                    )

            sendWebEvent(
                "streamlivex:native-player-closed",
                detail.toString(),
            )
        }
    }

    private fun sendProgress(
        progress: PlaybackProgress,
    ) {

        val detail =
            JSONObject()
                .put(
                    "current",
                    progress
                        .currentSeconds,
                )
                .put(
                    "duration",
                    progress
                        .durationSeconds,
                )

        sendWebEvent(
            "streamlivex:native-player-progress",
            detail.toString(),
        )
    }

    private fun sendWebEvent(
        name: String,
        detailJson: String,
    ) {

        val script =
            "window.dispatchEvent(new CustomEvent(${JSONObject.quote(name)}, { detail: $detailJson }));"

        webView
            ?.post {

                webView
                    ?.evaluateJavascript(
                        script,
                        null,
                    )
            }
    }

    /*
     * streamlivex://play deep link.
     */
    private fun handleDeepLink(
        intent: Intent?,
    ) {

        val uri =
            intent
                ?.data
                ?: return

        if (
            uri.scheme !=
            "streamlivex" ||
            uri.host !=
            "play"
        ) {
            return
        }

        val source =
            uri
                .getQueryParameter(
                    "url",
                )
                ?.trim()
                .orEmpty()

        val sourceUri =
            Uri.parse(source)

        if (
            source.isBlank() ||
            sourceUri
                .scheme
                ?.lowercase() !in
            setOf(
                "http",
                "https",
            )
        ) {
            return
        }

        lastProgress =
            PlaybackProgress()

        playbackRequest =
            PlaybackRequest(
                sessionId =
                    UUID
                        .randomUUID()
                        .toString(),
                item =
                    PlaybackItem(
                        name =
                            uri
                                .getQueryParameter(
                                    "title",
                                )
                                ?: "StreamLiveX",
                        url =
                            source,
                        kind =
                            uri
                                .getQueryParameter(
                                    "kind",
                                )
                                ?: "movie",
                    ),
            )
    }

    override fun onDestroy() {

        fileCallback
            ?.onReceiveValue(
                null,
            )

        fileCallback =
            null

        inlinePlayback =
            null

        playbackRequest =
            null

        tvFullscreenActive =
            false

        sharedPlayers
            .values
            .forEach {
                it.release()
            }

        sharedPlayers
            .clear()

        webView =
            null

        super.onDestroy()
    }
}
