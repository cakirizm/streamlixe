@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamlivex.android.tv.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvPlaybackSettingsStore
import com.streamlivex.android.tv.data.TvSavedItem
import com.streamlivex.android.tv.i18n.TvLocale
import com.streamlivex.android.tv.i18n.TvStrings
import kotlinx.coroutines.delay
import java.util.Locale

private enum class TrackPanel {
    None,
    Audio,
    Subtitle,
}

private data class TrackChoice(
    val language: String,
    val label: String,
)

@Composable
fun TvNativePlayer(
    saved: TvSavedItem,
    request: PlaybackRequest,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    store: TvContentStore,
    locale: TvLocale,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    playlist: List<Pair<TvSavedItem, PlaybackItem>> = emptyList(),
    playlistStartIndex: Int = 0,
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val settingsStore =
        remember {
            TvPlaybackSettingsStore(context)
        }
    val initialSettings =
        remember {
            settingsStore.load()
        }

    val player =
        remember(request.sessionId) {
            playerFor(request)
        }
    val pauseFocusRequester =
        remember {
            FocusRequester()
        }
    val playerRootFocusRequester =
        remember {
            FocusRequester()
        }

    var controlsVisible by remember {
        mutableStateOf(true)
    }
    var controlsActivity by remember {
        mutableIntStateOf(0)
    }

    var playing by remember {
        mutableStateOf(false)
    }
    var currentMs by remember {
        mutableLongStateOf(0L)
    }
    var durationMs by remember {
        mutableLongStateOf(0L)
    }
    var panel by remember {
        mutableStateOf(TrackPanel.None)
    }
    var audioChoices by remember {
        mutableStateOf<List<TrackChoice>>(emptyList())
    }
    var subtitleChoices by remember {
        mutableStateOf<List<TrackChoice>>(emptyList())
    }
    var fitMode by remember {
        mutableStateOf(initialSettings.fitMode)
    }
    var currentIndex by remember {
        mutableIntStateOf(
            playlistStartIndex.coerceAtLeast(0),
        )
    }

    fun effectivePlaylist(): List<Pair<TvSavedItem, PlaybackItem>> =
        if (
            playlist.isNotEmpty() &&
            initialSettings.autoNextEpisode
        ) {
            playlist
        } else {
            emptyList()
        }

    fun currentSaved(): TvSavedItem {
        val rows = effectivePlaylist()
        if (rows.isEmpty()) return saved

        return rows
            .getOrNull(
                player.currentMediaItemIndex,
            )
            ?.first
            ?: saved
    }

    fun saveProgress() {
        val row = currentSaved()
        val duration =
            player.duration
                .takeIf {
                    it > 0L
                }
                ?: 0L
        val position =
            player.currentPosition
                .coerceAtLeast(0L)

        store.saveProgress(
            row.copy(
                positionMs = position,
                durationMs = duration,
            ),
        )

        if (
            duration > 0L &&
            position >=
            (duration * 0.95).toLong()
        ) {
            store.setWatched(
                row.id,
                true,
            )
        }
    }

    fun applyDefaultTracks() {
        val settings =
            settingsStore.load()
        val builder =
            player
                .trackSelectionParameters
                .buildUpon()

        if (
            settings.audioLanguage !=
            "auto"
        ) {
            builder.setPreferredAudioLanguage(
                settings.audioLanguage,
            )
        }

        if (settings.subtitlesEnabled) {
            builder
                .setTrackTypeDisabled(
                    C.TRACK_TYPE_TEXT,
                    false,
                )
                .setPreferredTextLanguage(
                    settings.subtitleLanguage,
                )
        } else {
            builder.setTrackTypeDisabled(
                C.TRACK_TYPE_TEXT,
                true,
            )
        }

        player.trackSelectionParameters =
            builder.build()
    }

    fun refreshTracks() {
        val audio =
            linkedMapOf<String, TrackChoice>()
        val text =
            linkedMapOf<String, TrackChoice>()

        player.currentTracks.groups
            .forEach { group ->
                for (
                    i in
                    0 until group.length
                ) {
                    val format =
                        group.getTrackFormat(i)
                    val language =
                        format.language
                            ?.trim()
                            .orEmpty()
                            .ifBlank {
                                "und"
                            }
                    val label =
                        format.label
                            ?.trim()
                            .orEmpty()
                            .ifBlank {
                                if (
                                    language ==
                                    "und"
                                ) {
                                    "Track ${i + 1}"
                                } else {
                                    language
                                        .uppercase(
                                            Locale.ROOT,
                                        )
                                }
                            }

                    val key =
                        "$language|$label|$i"

                    when (group.type) {
                        C.TRACK_TYPE_AUDIO ->
                            audio[key] =
                                TrackChoice(
                                    language,
                                    label,
                                )

                        C.TRACK_TYPE_TEXT ->
                            text[key] =
                                TrackChoice(
                                    language,
                                    label,
                                )
                    }
                }
            }

        audioChoices =
            audio.values.toList()
        subtitleChoices =
            text.values.toList()
    }

    DisposableEffect(
        request.sessionId,
    ) {
        onFullscreenStateChanged(
            true,
        )

        var previousIndex =
            player.currentMediaItemIndex

        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(
                    isPlaying: Boolean,
                ) {
                    playing = isPlaying
                }

                override fun onTracksChanged(
                    tracks:
                        androidx.media3
                            .common
                            .Tracks,
                ) {
                    refreshTracks()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    val rows =
                        effectivePlaylist()

                    if (
                        reason ==
                        Player
                            .MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                        previousIndex >= 0
                    ) {
                        rows
                            .getOrNull(
                                previousIndex,
                            )
                            ?.first
                            ?.let {
                                store.setWatched(
                                    it.id,
                                    true,
                                )
                            }
                    }

                    previousIndex =
                        player.currentMediaItemIndex
                    currentIndex =
                        previousIndex
                            .coerceAtLeast(
                                0,
                            )
                }

                override fun onPlaybackStateChanged(
                    playbackState: Int,
                ) {
                    if (
                        playbackState ==
                        Player.STATE_ENDED
                    ) {
                        store.setWatched(
                            currentSaved().id,
                            true,
                        )
                    }
                }
            }

        player.addListener(listener)
        playing = player.isPlaying
        refreshTracks()
        applyDefaultTracks()

        onDispose {
            saveProgress()
            player.removeListener(
                listener,
            )
            onFullscreenStateChanged(
                false,
            )
            releasePlayer(
                request.sessionId,
            )
        }
    }

    LaunchedEffect(
        request.sessionId,
        request.item.url,
        playlistStartIndex,
        initialSettings.autoNextEpisode,
    ) {
        val rows =
            effectivePlaylist()

        if (rows.isNotEmpty()) {
            val mediaItems =
                rows.map {
                    (_, item) ->

                    MediaItem.Builder()
                        .setUri(item.url)
                        .setMediaId(item.name)
                        .build()
                }

            val safeIndex =
                playlistStartIndex
                    .coerceIn(
                        0,
                        mediaItems.lastIndex,
                    )

            player.setMediaItems(
                mediaItems,
                safeIndex,
                request.resumeTimeMs,
            )
        } else {
            player.setMediaItem(
                MediaItem.fromUri(
                    request.item.url,
                ),
                request.resumeTimeMs,
            )
        }

        player.prepare()
        player.playWhenReady = true

        controlsVisible = true
        controlsActivity += 1

        delay(250)
        runCatching {
            pauseFocusRequester
                .requestFocus()
        }
    }

    LaunchedEffect(
        controlsVisible,
        controlsActivity,
    ) {
        if (!controlsVisible) {
            return@LaunchedEffect
        }

        delay(3_500)

        if (
            panel ==
            TrackPanel.None
        ) {
            controlsVisible = false
            runCatching {
                playerRootFocusRequester
                    .requestFocus()
            }
        }
    }

    LaunchedEffect(
        player,
        request.sessionId,
    ) {
        while (true) {
            currentMs =
                player.currentPosition
                    .coerceAtLeast(0L)
            durationMs =
                player.duration
                    .takeIf {
                        it > 0L
                    }
                    ?: 0L

            if (currentMs > 0L) {
                saveProgress()
            }

            delay(1_000)
        }
    }

    BackHandler {
        if (
            panel !=
            TrackPanel.None
        ) {
            panel =
                TrackPanel.None
            controlsVisible =
                true
            controlsActivity +=
                1

            runCatching {
                pauseFocusRequester
                    .requestFocus()
            }
        } else {
            onClose()
        }
    }

    fun revealControls() {
        controlsVisible =
            true
        controlsActivity +=
            1
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black,
                )
                .focusRequester(
                    playerRootFocusRequester,
                )
                .onKeyEvent {
                    event ->

                    if (
                        !controlsVisible &&
                        event.type ==
                        KeyEventType.KeyUp &&
                        (
                            event.key ==
                            Key.DirectionCenter ||
                            event.key ==
                            Key.Enter ||
                            event.key ==
                            Key.NumPadEnter
                        )
                    ) {
                        revealControls()

                        runCatching {
                            pauseFocusRequester
                                .requestFocus()
                        }

                        true
                    } else {
                        false
                    }
                }
                .focusable(),
    ) {
        AndroidView(
            modifier =
                Modifier.fillMaxSize(),
            factory = {
                playerContext ->

                PlayerView(
                    playerContext,
                ).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup
                                .LayoutParams
                                .MATCH_PARENT,
                            ViewGroup
                                .LayoutParams
                                .MATCH_PARENT,
                        )

                    this.player =
                        player
                    useController =
                        false
                    resizeMode =
                        if (
                            fitMode ==
                            "fill"
                        ) {
                            AspectRatioFrameLayout
                                .RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout
                                .RESIZE_MODE_FIT
                        }
                    keepScreenOn =
                        true

                    // Keep subtitles above the TV safe-area and
                    // away from the temporary control strip.
                    subtitleView
                        ?.setBottomPaddingFraction(
                            0.12f,
                        )
                }
            },
            update = {
                view ->

                view.player =
                    player
                view.useController =
                    false
                view.resizeMode =
                    if (
                        fitMode ==
                        "fill"
                    ) {
                        AspectRatioFrameLayout
                            .RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout
                            .RESIZE_MODE_FIT
                    }
                view.keepScreenOn =
                    true
                view.subtitleView
                    ?.setBottomPaddingFraction(
                        0.12f,
                    )
            },
        )

        if (
            controlsVisible
        ) {
            // Small, TV-safe top row. It disappears with
            // the rest of the controls after inactivity.
            Row(
                modifier =
                    Modifier
                        .align(
                            Alignment.TopStart,
                        )
                        .padding(
                            24.dp,
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                TvPlayerIconButton(
                    icon =
                        "←",
                    contentDescription =
                        strings["back"],
                    onActivity = {
                        revealControls()
                    },
                    onClick =
                        onClose,
                )
            }

            Column(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .fillMaxWidth()
                        .background(
                            Color(
                                0xB30B0F17,
                            ),
                        )
                        .padding(
                            horizontal =
                                34.dp,
                            vertical =
                                16.dp,
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                Text(
                    currentSaved().name,
                    color =
                        Color.White,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                )

                LinearProgressIndicator(
                    progress = {
                        if (
                            durationMs >
                            0L
                        ) {
                            (
                                currentMs
                                    .toFloat() /
                                    durationMs
                                        .toFloat()
                            ).coerceIn(
                                0f,
                                1f,
                            )
                        } else {
                            0f
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement
                            .SpaceBetween,
                ) {
                    Text(
                        formatTime(
                            currentMs,
                        ),
                        color =
                            Color(
                                0xFFCBD5E1,
                            ),
                    )

                    Text(
                        formatTime(
                            durationMs,
                        ),
                        color =
                            Color(
                                0xFFCBD5E1,
                            ),
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement
                            .SpaceBetween,
                    verticalAlignment =
                        Alignment
                            .CenterVertically,
                ) {
                    // Playback group: left side.
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                            ),
                        verticalAlignment =
                            Alignment
                                .CenterVertically,
                    ) {
                        TvPlayerIconButton(
                            icon =
                                "↶10",
                            contentDescription =
                                "10 saniye geri",
                            onActivity = {
                                revealControls()
                            },
                        ) {
                            player.seekTo(
                                (
                                    player
                                        .currentPosition -
                                        10_000L
                                ).coerceAtLeast(
                                    0L,
                                ),
                            )
                        }

                        TvPlayerIconButton(
                            icon =
                                if (
                                    playing
                                ) {
                                    "Ⅱ"
                                } else {
                                    "▶"
                                },
                            contentDescription =
                                if (
                                    playing
                                ) {
                                    strings[
                                        "pause"
                                    ]
                                } else {
                                    strings[
                                        "resume"
                                    ]
                                },
                            modifier =
                                Modifier
                                    .focusRequester(
                                        pauseFocusRequester,
                                    ),
                            prominent =
                                true,
                            onActivity = {
                                revealControls()
                            },
                        ) {
                            if (
                                player.isPlaying
                            ) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        }

                        TvPlayerIconButton(
                            icon =
                                "10↷",
                            contentDescription =
                                "10 saniye ileri",
                            onActivity = {
                                revealControls()
                            },
                        ) {
                            val target =
                                player
                                    .currentPosition +
                                    10_000L
                            val duration =
                                player.duration

                            player.seekTo(
                                if (
                                    duration >
                                    0L
                                ) {
                                    target
                                        .coerceAtMost(
                                            duration,
                                        )
                                } else {
                                    target
                                },
                            )
                        }

                        val rows =
                            effectivePlaylist()

                        if (
                            rows.isNotEmpty() &&
                            player
                                .hasNextMediaItem()
                        ) {
                            TvPlayerIconButton(
                                icon =
                                    "⏭",
                                contentDescription =
                                    "Sonraki bölüm",
                                onActivity = {
                                    revealControls()
                                },
                            ) {
                                saveProgress()
                                player
                                    .seekToNextMediaItem()
                                player.play()
                            }
                        }
                    }

                    // Utility group: right side.
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                            ),
                        verticalAlignment =
                            Alignment
                                .CenterVertically,
                    ) {
                        TvPlayerIconButton(
                            icon =
                                "♪",
                            contentDescription =
                                strings[
                                    "audio"
                                ],
                            onActivity = {
                                revealControls()
                            },
                        ) {
                            refreshTracks()
                            panel =
                                TrackPanel.Audio
                            controlsActivity +=
                                1
                        }

                        TvPlayerIconButton(
                            icon =
                                "CC",
                            contentDescription =
                                strings[
                                    "subtitles"
                                ],
                            onActivity = {
                                revealControls()
                            },
                        ) {
                            refreshTracks()
                            panel =
                                TrackPanel.Subtitle
                            controlsActivity +=
                                1
                        }

                        TvPlayerIconButton(
                            icon =
                                if (
                                    fitMode ==
                                    "fill"
                                ) {
                                    "⛶"
                                } else {
                                    "▣"
                                },
                            contentDescription =
                                if (
                                    fitMode ==
                                    "fill"
                                ) {
                                    "Ekranı sığdır"
                                } else {
                                    "Ekranı doldur"
                                },
                            onActivity = {
                                revealControls()
                            },
                        ) {
                            fitMode =
                                if (
                                    fitMode ==
                                    "fill"
                                ) {
                                    "fit"
                                } else {
                                    "fill"
                                }

                            settingsStore.save(
                                settingsStore
                                    .load()
                                    .copy(
                                        fitMode =
                                            fitMode,
                                    ),
                            )
                        }
                    }
                }
            }
        }

        if (
            panel !=
            TrackPanel.None
        ) {
            TrackPanelOverlay(
                title =
                    if (
                        panel ==
                        TrackPanel.Audio
                    ) {
                        strings[
                            "audio"
                        ]
                    } else {
                        strings[
                            "subtitles"
                        ]
                    },
                choices =
                    if (
                        panel ==
                        TrackPanel.Audio
                    ) {
                        audioChoices
                    } else {
                        subtitleChoices
                    },
                showOff =
                    panel ==
                    TrackPanel.Subtitle,
                offLabel =
                    strings[
                        "off"
                    ],
                onOff = {
                    player
                        .trackSelectionParameters =
                        player
                            .trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(
                                C.TRACK_TYPE_TEXT,
                                true,
                            )
                            .build()

                    settingsStore.save(
                        settingsStore
                            .load()
                            .copy(
                                subtitlesEnabled =
                                    false,
                            ),
                    )

                    panel =
                        TrackPanel.None
                    revealControls()
                },
                onChoice = {
                    choice ->

                    val builder =
                        player
                            .trackSelectionParameters
                            .buildUpon()

                    if (
                        panel ==
                        TrackPanel.Audio
                    ) {
                        player
                            .trackSelectionParameters =
                            builder
                                .setPreferredAudioLanguage(
                                    choice.language,
                                )
                                .build()

                        settingsStore.save(
                            settingsStore
                                .load()
                                .copy(
                                    audioLanguage =
                                        choice.language,
                                ),
                        )
                    } else {
                        player
                            .trackSelectionParameters =
                            builder
                                .setTrackTypeDisabled(
                                    C.TRACK_TYPE_TEXT,
                                    false,
                                )
                                .setPreferredTextLanguage(
                                    choice.language,
                                )
                                .build()

                        settingsStore.save(
                            settingsStore
                                .load()
                                .copy(
                                    subtitlesEnabled =
                                        true,
                                    subtitleLanguage =
                                        choice.language,
                                ),
                        )
                    }

                    panel =
                        TrackPanel.None
                    revealControls()
                },
                onClose = {
                    panel =
                        TrackPanel.None
                    revealControls()
                },
            )
        }
    }
}

@Composable
private fun TrackPanelOverlay(
    title: String,
    choices: List<TrackChoice>,
    showOff: Boolean,
    offLabel: String,
    onOff: () -> Unit,
    onChoice: (TrackChoice) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocusRequester =
        remember {
            FocusRequester()
        }

    LaunchedEffect(
        title,
        choices.size,
        showOff,
    ) {
        delay(120)

        runCatching {
            firstFocusRequester
                .requestFocus()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(
                        0x73000000,
                    ),
                ),
        contentAlignment =
            Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .width(
                        390.dp,
                    )
                    .padding(
                        end = 26.dp,
                    )
                    .background(
                        Color(
                            0xF2111827,
                        ),
                        RoundedCornerShape(
                            18.dp,
                        ),
                    )
                    .padding(
                        18.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    9.dp,
                ),
        ) {
            Text(
                title,
                color =
                    Color.White,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
            )

            var firstAssigned =
                false

            if (showOff) {
                TvPlayerTextButton(
                    text =
                        offLabel,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(
                                firstFocusRequester,
                            ),
                    onClick =
                        onOff,
                )
                firstAssigned =
                    true
            }

            if (
                choices.isEmpty()
            ) {
                Text(
                    "Track bulunamadı",
                    color =
                        Color(
                            0xFF94A3B8,
                        ),
                )

                if (
                    !firstAssigned
                ) {
                    TvPlayerTextButton(
                        text =
                            "✕",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(
                                    firstFocusRequester,
                                ),
                        onClick =
                            onClose,
                    )
                    firstAssigned =
                        true
                }
            } else {
                choices.forEachIndexed {
                    index,
                    choice ->

                    TvPlayerTextButton(
                        text =
                            if (
                                choice.language ==
                                "und"
                            ) {
                                choice.label
                            } else {
                                "${choice.label} · ${choice.language.uppercase(Locale.ROOT)}"
                            },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (
                                        !firstAssigned &&
                                        index ==
                                        0
                                    ) {
                                        Modifier
                                            .focusRequester(
                                                firstFocusRequester,
                                            )
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        onChoice(
                            choice,
                        )
                    }
                }

                firstAssigned =
                    true
            }

            if (
                firstAssigned
            ) {
                TvPlayerTextButton(
                    text =
                        "✕",
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    onClick =
                        onClose,
                )
            }
        }
    }
}

@Composable
private fun TvPlayerIconButton(
    icon: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    onActivity: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by
        remember {
            mutableStateOf(
                false,
            )
        }

    Box(
        modifier =
            modifier
                .size(
                    if (
                        prominent
                    ) {
                        58.dp
                    } else {
                        50.dp
                    },
                )
                .background(
                    if (focused) {
                        Color(
                            0x663B82F6,
                        )
                    } else {
                        Color.Transparent
                    },
                    RoundedCornerShape(
                        50.dp,
                    ),
                )
                .onFocusChanged {
                    state ->

                    focused =
                        state.isFocused

                    if (
                        state.isFocused
                    ) {
                        onActivity()
                    }
                }
                .onKeyEvent {
                    event ->

                    if (
                        event.type ==
                        KeyEventType.KeyUp &&
                        (
                            event.key ==
                            Key.DirectionCenter ||
                            event.key ==
                            Key.Enter ||
                            event.key ==
                            Key.NumPadEnter
                        )
                    ) {
                        onActivity()
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                .focusable(),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(
            text =
                icon,
            color =
                Color.White,
            style =
                if (
                    prominent
                ) {
                    MaterialTheme
                        .typography
                        .headlineMedium
                } else {
                    MaterialTheme
                        .typography
                        .titleLarge
                },
        )
    }
}

@Composable
private fun TvPlayerTextButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by
        remember {
            mutableStateOf(
                false,
            )
        }

    Box(
        modifier =
            modifier
                .background(
                    if (focused) {
                        Color(
                            0xFF2563EB,
                        )
                    } else {
                        Color(
                            0xFF202A3A,
                        )
                    },
                    RoundedCornerShape(
                        10.dp,
                    ),
                )
                .onFocusChanged {
                    focused =
                        it.isFocused
                }
                .onKeyEvent {
                    event ->

                    if (
                        event.type ==
                        KeyEventType.KeyUp &&
                        (
                            event.key ==
                            Key.DirectionCenter ||
                            event.key ==
                            Key.Enter ||
                            event.key ==
                            Key.NumPadEnter
                        )
                    ) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                .focusable()
                .padding(
                    horizontal =
                        15.dp,
                    vertical =
                        12.dp,
                ),
        contentAlignment =
            Alignment.CenterStart,
    ) {
        Text(
            text =
                text,
            color =
                Color.White,
        )
    }
}

private fun formatTime(
    ms: Long,
): String {
    if (ms <= 0L) return "00:00"

    val totalSeconds =
        ms / 1000L
    val hours =
        totalSeconds / 3600L
    val minutes =
        (
            totalSeconds %
                3600L
        ) / 60L
    val seconds =
        totalSeconds % 60L

    return if (
        hours > 0L
    ) {
        "%02d:%02d:%02d"
            .format(
                hours,
                minutes,
                seconds,
            )
    } else {
        "%02d:%02d"
            .format(
                minutes,
                seconds,
            )
    }
}
