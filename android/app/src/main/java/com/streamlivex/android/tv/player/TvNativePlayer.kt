@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamlivex.android.tv.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvSavedItem
import kotlinx.coroutines.delay

/*
 * TV için ayrı native player.
 * Web/Compose kontrol paneli YOK.
 * Media3 PlayerView'ın kendi stabil TV kontrollerini kullanır.
 */
@Composable
fun TvNativePlayer(
    saved: TvSavedItem,
    request: PlaybackRequest,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    store: TvContentStore,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    playlist: List<Pair<TvSavedItem, PlaybackItem>> = emptyList(),
    playlistStartIndex: Int = 0,
) {
    val player = remember(request.sessionId) {
        playerFor(request)
    }

    DisposableEffect(request.sessionId) {
        onFullscreenStateChanged(true)

        onDispose {
            val duration = player.duration.takeIf { it > 0 } ?: 0L
            store.saveProgress(
                saved.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = duration,
                ),
            )
            onFullscreenStateChanged(false)
            releasePlayer(request.sessionId)
        }
    }

    LaunchedEffect(request.sessionId) {
        if (playlist.isNotEmpty()) {
            val mediaItems = playlist.map { (_, item) ->
                MediaItem.Builder()
                    .setUri(item.url)
                    .setMediaId(item.name)
                    .build()
            }
            player.setMediaItems(
                mediaItems,
                playlistStartIndex.coerceIn(0, mediaItems.lastIndex),
                request.resumeTimeMs,
            )
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(player, request.sessionId) {
        while (true) {
            delay(5_000)
            val duration = player.duration.takeIf { it > 0 } ?: 0L
            store.saveProgress(
                saved.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = duration,
                ),
            )
        }
    }

    BackHandler {
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 4_000
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            },
            update = { view ->
                view.player = player
                view.useController = true
                view.controllerAutoShow = true
                view.keepScreenOn = true
                if (!view.hasFocus()) view.requestFocus()
            },
        )
    }
}
