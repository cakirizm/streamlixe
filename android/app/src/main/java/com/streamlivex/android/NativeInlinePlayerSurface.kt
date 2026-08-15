package com.streamlivex.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
fun NativeInlinePlayerSurface(
    request: PlaybackRequest,
    modifier: Modifier,
    onFullScreen: () -> Unit,
    onFailure: (String) -> Unit,
) {
    val context = LocalContext.current
    val candidates = remember(request.item.url) { playbackCandidates(request.item) }
    var candidateIndex by remember(request.sessionId) { mutableIntStateOf(0) }
    var candidateRetry by remember(request.sessionId) { mutableIntStateOf(0) }
    var ready by remember(request.sessionId) { mutableStateOf(false) }
    var failed by remember(request.sessionId) { mutableStateOf(false) }
    var qualityLabel by remember(request.sessionId) { mutableStateOf("AUTO") }
    val failureMessage = stringResource(R.string.stream_failed)

    val player = remember(request.sessionId) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0 StreamLiveX-Android/${BuildConfig.VERSION_NAME}")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .build()
            .apply {
                playWhenReady = true
                playbackParameters = PlaybackParameters(request.preferences.playbackRate)
                trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
                    when (request.preferences.quality) {
                        "Yüksek" -> setForceHighestSupportedBitrate(true)
                        "Veri tasarrufu" -> setMaxVideoSizeSd()
                    }
                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                }.build()
            }
    }

    DisposableEffect(player, candidates) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                ready = state == Player.STATE_READY
                if (ready) PlaybackCandidateMemory.remember(request.sessionId, candidateIndex)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                qualityLabel = videoSize.height.takeIf { it > 0 }?.let { "${it}p" } ?: "AUTO"
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.isRetryableStreamError() && candidateRetry < 1) {
                    candidateRetry += 1
                } else if (candidateIndex + 1 < candidates.size) {
                    candidateRetry = 0
                    candidateIndex += 1
                } else {
                    ready = false
                    failed = true
                    onFailure(failureMessage)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(candidateIndex, candidateRetry, request.sessionId) {
        ready = false
        failed = false
        if (candidateRetry > 0) kotlinx.coroutines.delay(750)
        player.stop()
        player.setMediaItem(createMediaItem(request, candidates[candidateIndex]))
        player.prepare()
        player.play()
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { playerContext ->
                PlayerView(playerContext).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = false
                    controllerHideOnTouch = true
                    controllerShowTimeoutMs = 3_500
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
        )

        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    request.item.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "LIVE • $qualityLabel",
                    color = Color(0xFFB8B4C8),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Button(
                onClick = onFullScreen,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.full_screen), style = MaterialTheme.typography.labelSmall)
            }
        }

        if (!ready && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (failed) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.stream_failed),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(R.string.stream_failed_detail),
                    color = Color(0xFFCAC6D8),
                    maxLines = 2,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
