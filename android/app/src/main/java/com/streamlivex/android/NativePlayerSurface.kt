package com.streamlivex.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun NativePlayerSurface(
    request: PlaybackRequest,
    player: ExoPlayer,
    onClose: (PlaybackProgress) -> Unit,
    onProgress: (PlaybackProgress) -> Unit,
    onFailure: (String) -> Unit,
) {
    val context = LocalContext.current
    val candidates = remember(request.item.url) { playbackCandidates(request.item) }
    var candidateIndex by remember(request.sessionId) {
        mutableIntStateOf(PlaybackCandidateMemory.recall(request.sessionId).coerceIn(0, (candidates.size - 1).coerceAtLeast(0)))
    }
    var candidateRetry by remember(request.sessionId) { mutableIntStateOf(0) }
    var failed by remember(request.sessionId) { mutableStateOf(false) }
    var ready by remember(request.sessionId) { mutableStateOf(player.playbackState == Player.STATE_READY) }
    var resizeMode by remember(request.sessionId) { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var qualityLabel by remember(request.sessionId) { mutableStateOf("AUTO") }
    val streamFailedMessage = stringResource(R.string.stream_failed)
    val fitLabel = stringResource(R.string.fit_screen)
    val fillLabel = stringResource(R.string.fill_screen)
    var controlsVisible by remember(request.sessionId) { mutableStateOf(true) }
    val isTelevision = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    DisposableEffect(request.sessionId, isTelevision) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
        if (!isTelevision) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (!isTelevision && previousOrientation != null) {
                activity?.requestedOrientation = previousOrientation
            }
        }
    }

    fun progress() = PlaybackProgress(
        currentSeconds = player.currentPosition.coerceAtLeast(0) / 1000.0,
        durationSeconds = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }?.div(1000.0) ?: 0.0,
    )

    DisposableEffect(player, candidates) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                ready = state == Player.STATE_READY
                if (state == Player.STATE_ENDED) onClose(progress())
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.isRetryableStreamError() && candidateRetry < 1) {
                    candidateRetry += 1
                } else if (candidateIndex + 1 < candidates.size) {
                    candidateRetry = 0
                    candidateIndex += 1
                } else {
                    failed = true
                    ready = false
                    onFailure(streamFailedMessage)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                qualityLabel = videoSize.height.takeIf { it > 0 }?.let { "${it}p" } ?: "AUTO"
            }
        }
        player.addListener(listener)
        onDispose {
            onProgress(progress())
            player.removeListener(listener)
        }
    }

    LaunchedEffect(candidateIndex, candidateRetry, request.sessionId) {
        // On izlemeden tam ekrana gecerken ayni paylasilan player zaten bu adayla oynatiyor
        // olabilir -- boyle bir durumda sifirdan setMediaItem/prepare cagirip yayini yeniden
        // baslatmiyoruz, sadece mevcut oynatmayi devam ettiriyoruz.
        if (player.playbackState == Player.STATE_READY && player.isPlaying) return@LaunchedEffect
        failed = false
        ready = false
        if (candidateRetry > 0) delay(750)
        player.stop()
        val mediaItem = createMediaItem(request, candidates[candidateIndex])
        player.setMediaItem(mediaItem, request.resumeTimeMs)
        player.prepare()
        player.play()
    }

    LaunchedEffect(player, request.sessionId) {
        while (true) {
            delay(5_000)
            if (!request.item.isLive) onProgress(progress())
        }
    }

    Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { playerContext ->
                PlayerView(playerContext).apply {
                    this.player = player
                    useController = true
                    // controllerAutoShow=true, ExoPlayer'in her oynatma durumu degisiminde
                    // (arabellek doldurma, hazir olma vb.) kontrolcuyu zorla tekrar gostermesine
                    // sebep oluyordu -- canli/surekli tamponlanan yayinlarda bu, ust bilgi
                    // cubugunun hicbir zaman kaybolamamasina yol aciyordu. false yapip girince
                    // bir kere manuel gosteriyoruz, sonrasında sadece dokunma/zaman aşımı
                    // kontrolu (controllerShowTimeoutMs) devrede kalıyor.
                    controllerAutoShow = false
                    controllerHideOnTouch = true
                    controllerShowTimeoutMs = 1_800
                    this.resizeMode = resizeMode
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    applySubtitleStyle(this, request.preferences)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                        },
                    )
                    showController()
                    post { requestFocus() }
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
                applySubtitleStyle(view, request.preferences)
                if (!view.hasFocus()) view.post { view.requestFocus() }
            },
        )

        if (controlsVisible) Row(
            modifier = Modifier.fillMaxWidth()
                .background(ComposeColor(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = { onClose(progress()) },
                modifier = Modifier.size(42.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("‹", color = ComposeColor.White, style = MaterialTheme.typography.headlineMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    request.item.name,
                    color = ComposeColor.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(if (request.item.isLive) "LIVE" else "StreamLiveX")
                        if (request.preferences.showInfo) append(" • $qualityLabel")
                    },
                    color = ComposeColor(0xFFB8B4C8),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(
                onClick = {
                    resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(
                    if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) fitLabel else fillLabel,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (!ready && !failed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.preparing_stream), color = ComposeColor.White)
            }
        }

        if (failed) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.stream_failed), color = ComposeColor.White, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.stream_failed_detail), color = ComposeColor(0xFFCAC6D8))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { failed = false; candidateIndex = 0 }) { Text(stringResource(R.string.retry)) }
                    Button(onClick = { onClose(progress()) }) { Text(stringResource(R.string.back)) }
                }
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun playbackCandidates(item: PlaybackItem): List<String> {
    val source = item.url.trim()
    val candidates = mutableListOf(source)
    if (item.isLive && Uri.parse(source).path?.endsWith(".ts", ignoreCase = true) == true) {
        candidates += source.replace(Regex("\\.ts(?=($|\\?))", RegexOption.IGNORE_CASE), ".m3u8")
    }
    return candidates.distinct()
}

internal fun PlaybackException.isRetryableStreamError(): Boolean {
    var current: Throwable? = cause
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) {
            return current.responseCode in setOf(403, 404, 429, 500, 502, 503, 504)
        }
        current = current.cause
    }
    return errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
        errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
}

internal fun createMediaItem(request: PlaybackRequest, source: String): MediaItem {
    val subtitleConfigurations = request.item.subtitles.map { subtitle ->
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.src))
            .setMimeType(subtitleMimeType(subtitle.src))
            .setLabel(subtitle.label)
            .setLanguage(subtitle.language)
            .setSelectionFlags(if (request.preferences.subtitleMode == "off") 0 else C.SELECTION_FLAG_DEFAULT)
            .build()
    }
    return MediaItem.Builder()
        .setUri(source)
        .setMediaId(request.sessionId)
        .setSubtitleConfigurations(subtitleConfigurations)
        .build()
}

private fun subtitleMimeType(url: String): String = when (Uri.parse(url).path?.substringAfterLast('.')?.lowercase()) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "ttml", "xml" -> MimeTypes.APPLICATION_TTML
    else -> MimeTypes.TEXT_VTT
}

@UnstableApi
private fun applySubtitleStyle(playerView: PlayerView, preferences: PlaybackPreferences) {
    val foreground = runCatching { Color.parseColor(preferences.subtitleColor) }.getOrDefault(Color.WHITE)
    val background = if (preferences.subtitleBackground == "box") Color.argb(190, 0, 0, 0) else Color.TRANSPARENT
    val edgeType = if (preferences.subtitleBackground == "shadow") CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW else CaptionStyleCompat.EDGE_TYPE_NONE
    playerView.subtitleView?.apply {
        setFractionalTextSize(0.0533f * preferences.subtitleSize.coerceIn(70, 180) / 100f)
        setStyle(CaptionStyleCompat(foreground, background, Color.TRANSPARENT, edgeType, Color.BLACK, null))
    }
}
