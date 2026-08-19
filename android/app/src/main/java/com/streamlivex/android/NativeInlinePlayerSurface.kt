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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
fun NativeInlinePlayerSurface(
    request: PlaybackRequest,
    player: ExoPlayer,
    modifier: Modifier,
    onFullScreen: () -> Unit,
    onFailure: (String) -> Unit,
) {
    /*
     * Kanal URL'si degisince candidate listesi
     * yeniden hesaplanir.
     */
    val candidates =
        remember(request.item.url) {
            playbackCandidates(
                request.item,
            )
        }

    /*
     * Ayni sessionId ile kanal degistiriyoruz.
     * Bu nedenle state sadece sessionId'ye
     * bagli olamaz; URL de anahtarin parcasi.
     */
    var candidateIndex by remember(
        request.sessionId,
        request.item.url,
    ) {
        mutableIntStateOf(0)
    }

    var candidateRetry by remember(
        request.sessionId,
        request.item.url,
    ) {
        mutableIntStateOf(0)
    }

    var ready by remember(
        request.sessionId,
        request.item.url,
    ) {
        mutableStateOf(
            player.playbackState ==
                Player.STATE_READY,
        )
    }

    var failed by remember(
        request.sessionId,
        request.item.url,
    ) {
        mutableStateOf(false)
    }

    var qualityLabel by remember(
        request.sessionId,
        request.item.url,
    ) {
        mutableStateOf("AUTO")
    }

    val failureMessage =
        stringResource(
            R.string.stream_failed,
        )

    /*
     * Player MainActivity tarafindan
     * olusturuluyor.
     *
     * Preview ve fullscreen ayni player'i
     * paylasabilir.
     *
     * Burada release() YOK.
     */
    DisposableEffect(
        player,
        candidates,
        request.item.url,
    ) {
        val listener =
            object : Player.Listener {

                override fun onPlaybackStateChanged(
                    state: Int,
                ) {
                    ready =
                        state ==
                        Player.STATE_READY

                    if (ready) {
                        PlaybackCandidateMemory
                            .remember(
                                request.sessionId,
                                candidateIndex,
                            )
                    }
                }

                override fun onVideoSizeChanged(
                    videoSize: VideoSize,
                ) {
                    qualityLabel =
                        videoSize
                            .height
                            .takeIf {
                                it > 0
                            }
                            ?.let {
                                "${it}p"
                            }
                            ?: "AUTO"
                }

                override fun onPlayerError(
                    error: PlaybackException,
                ) {
                    if (
                        error
                            .isRetryableStreamError() &&
                        candidateRetry < 1
                    ) {
                        candidateRetry += 1

                    } else if (
                        candidateIndex + 1 <
                        candidates.size
                    ) {
                        candidateRetry = 0
                        candidateIndex += 1

                    } else {
                        ready = false
                        failed = true

                        onFailure(
                            failureMessage,
                        )
                    }
                }
            }

        player.addListener(
            listener,
        )

        onDispose {
            player.removeListener(
                listener,
            )
        }
    }

    /*
     * URL hash'i startKey'e dahil.
     *
     * Ayni sessionId ile baska kanala
     * gecince yeni yayinin gercekten
     * hazırlanmasını saglar.
     */
    LaunchedEffect(
        candidateIndex,
        candidateRetry,
        request.sessionId,
        request.item.url,
    ) {
        val startKey =
            "${request.sessionId}:${request.item.url.hashCode()}:$candidateIndex:$candidateRetry"

        if (
            PlaybackStartTracker
                .hasStarted(
                    startKey,
                )
        ) {
            ready =
                player.playbackState ==
                Player.STATE_READY

            return@LaunchedEffect
        }

        PlaybackStartTracker
            .markStarted(
                startKey,
            )

        ready = false
        failed = false

        if (candidateRetry > 0) {
            kotlinx.coroutines.delay(
                750,
            )
        }

        player.stop()

        player.setMediaItem(
            createMediaItem(
                request,
                candidates[
                    candidateIndex
                ],
            ),
        )

        player.prepare()

        player.play()
    }

    Box(
        modifier.background(
            Color.Black,
        ),
    ) {
        AndroidView(
            modifier =
                Modifier.fillMaxSize(),
            factory = {
                    playerContext ->

                PlayerView(
                    playerContext,
                ).apply {

                    this.player =
                        player

                    useController =
                        true

                    controllerAutoShow =
                        false

                    controllerHideOnTouch =
                        true

                    controllerShowTimeoutMs =
                        3_500

                    resizeMode =
                        AspectRatioFrameLayout
                            .RESIZE_MODE_FIT

                    keepScreenOn =
                        true
                }
            },
            update = {
                it.player =
                    player
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(
                        0x99000000,
                    ),
                )
                .padding(
                    horizontal =
                        10.dp,
                    vertical =
                        7.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Column(
                Modifier.weight(1f),
            ) {
                Text(
                    text =
                        request.item.name,
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                )

                Text(
                    text =
                        "LIVE • $qualityLabel",
                    color =
                        Color(
                            0xFFB8B4C8,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                )
            }

            Button(
                onClick =
                    onFullScreen,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                Color.White,
                            contentColor =
                                Color.Black,
                        ),
                contentPadding =
                    PaddingValues(
                        horizontal =
                            12.dp,
                        vertical =
                            0.dp,
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .full_screen,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                )
            }
        }

        if (
            !ready &&
            !failed
        ) {
            CircularProgressIndicator(
                modifier =
                    Modifier.align(
                        Alignment.Center,
                    ),
                color =
                    MaterialTheme
                        .colorScheme
                        .primary,
            )
        }

        if (failed) {
            Column(
                modifier =
                    Modifier
                        .align(
                            Alignment.Center,
                        )
                        .padding(
                            20.dp,
                        ),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(
                        6.dp,
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .stream_failed,
                        ),
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                )

                Text(
                    text =
                        stringResource(
                            R.string
                                .stream_failed_detail,
                        ),
                    color =
                        Color(
                            0xFFCAC6D8,
                        ),
                    maxLines = 2,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                )
            }
        }
    }
}
