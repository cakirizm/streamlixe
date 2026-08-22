@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamlivex.android.tv.player

import android.net.Uri
import android.view.ViewGroup
import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.OnlineSubtitleResult
import com.streamlivex.android.SubtitleSearchClient
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvPlaybackSettingsStore
import com.streamlivex.android.tv.data.TvPlaybackSettings
import com.streamlivex.android.tv.data.TvSavedItem
import com.streamlivex.android.tv.i18n.TvLocale
import com.streamlivex.android.tv.i18n.TvStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

private enum class TrackPanel {
    None,
    Audio,
    Subtitle,
    SubtitleStyle,
    OnlineSubtitle,
}

private enum class LastPlayerControl { Play, Audio, Subtitle, SubtitleStyle, Fit }

private fun languageAliases(language: String): List<String> =
    when (language.lowercase(Locale.ROOT)) {
        "en", "eng" -> listOf("eng", "en")
        "tr", "tur" -> listOf("tur", "tr")
        "de", "deu", "ger" -> listOf("deu", "ger", "de")
        "fr", "fra", "fre" -> listOf("fra", "fre", "fr")
        "es", "spa" -> listOf("spa", "es")
        "ar", "ara" -> listOf("ara", "ar")
        "ru", "rus" -> listOf("rus", "ru")
        else -> listOf(language)
    }

private fun canonicalLanguage(language: String): String =
    when (language.lowercase(Locale.ROOT)) {
        "eng" -> "en"
        "tur" -> "tr"
        "deu", "ger" -> "de"
        "fra", "fre" -> "fr"
        "spa" -> "es"
        "ara" -> "ar"
        "rus" -> "ru"
        else -> language.lowercase(Locale.ROOT)
    }

private enum class PlayerControlIcon {
    Back, Replay10, Play, Pause, Forward10, Audio, Captions, SubtitleStyle, OnlineSubtitle, Fit, Fill,
}

private data class TrackChoice(
    val language: String,
    val label: String,
    val selected: Boolean,
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
    val coroutineScope = rememberCoroutineScope()
    val strings = remember(locale) { TvStrings(locale) }
    val settingsStore =
        remember {
            TvPlaybackSettingsStore(context)
        }
    val initialSettings =
        remember {
            settingsStore.load()
        }
    var subtitleSettings by remember {
        mutableStateOf(initialSettings)
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
    val audioFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val subtitleStyleFocusRequester = remember { FocusRequester() }
    val fitFocusRequester = remember { FocusRequester() }
    var lastControl by remember { mutableStateOf(LastPlayerControl.Play) }

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
    var lastProgressWriteAtMs by remember(request.sessionId) {
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
    var onlineSubtitleBusy by remember { mutableStateOf(false) }
    var onlineSubtitleResults by remember { mutableStateOf<List<OnlineSubtitleResult>>(emptyList()) }
    var onlineSubtitleError by remember { mutableStateOf<String?>(null) }
    var onlineSubtitleQuery by remember { mutableStateOf(saved.name.substringBefore(" • ")) }
    var onlineSubtitleLanguage by remember { mutableStateOf("tr") }
    var playerViewRef by remember(request.sessionId) { mutableStateOf<PlayerView?>(null) }
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
            builder.setPreferredAudioLanguages(
                *languageAliases(settings.audioLanguage).toTypedArray(),
            )
        }

        if (settings.subtitlesEnabled) {
            builder
                .setTrackTypeDisabled(
                    C.TRACK_TYPE_TEXT,
                    false,
                )
                .setPreferredTextLanguages(
                    *languageAliases(settings.subtitleLanguage).toTypedArray(),
                )
                .setSelectUndeterminedTextLanguage(true)
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
                                    group.isTrackSelected(i),
                                )

                        C.TRACK_TYPE_TEXT ->
                            text[key] =
                                TrackChoice(
                                    language,
                                    label,
                                    group.isTrackSelected(i),
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
        applyDefaultTracks()
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

    fun searchOnlineSubtitles() {
        if (onlineSubtitleBusy) return
        onlineSubtitleBusy = true
        onlineSubtitleError = null
        coroutineScope.launch {
            val results = runCatching {
                SubtitleSearchClient.search(
                    onlineSubtitleQuery.ifBlank { currentSaved().name.substringBefore(" • ") },
                    onlineSubtitleLanguage,
                )
            }.getOrElse {
                onlineSubtitleError = "Altyazı servisine ulaşılamadı"
                emptyList()
            }
            onlineSubtitleResults = results
            if (results.isEmpty() && onlineSubtitleError == null) {
                onlineSubtitleError = "Bu içerik için altyazı bulunamadı"
            }
            onlineSubtitleBusy = false
        }
    }

    fun applyOnlineSubtitle(result: OnlineSubtitleResult) {
        if (onlineSubtitleBusy) return
        onlineSubtitleBusy = true
        onlineSubtitleError = null
        coroutineScope.launch {
            val url = runCatching { SubtitleSearchClient.resolveDownloadUrl(result.fileId) }.getOrNull()
            val currentItem = player.currentMediaItem
            if (url == null || currentItem == null) {
                onlineSubtitleError = "Altyazı indirilemedi"
                onlineSubtitleBusy = false
                return@launch
            }
            val position = player.currentPosition
            val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                .setMimeType(subtitleMimeType(url))
                .setLanguage(result.language)
                .setLabel(result.release)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val existing = currentItem.localConfiguration?.subtitleConfigurations.orEmpty()
                .filterNot { it.language.equals(result.language, ignoreCase = true) }
            val updatedItem = currentItem.buildUpon()
                .setSubtitleConfigurations(existing + subtitle)
                .build()
            val wasPlaying = player.playWhenReady
            player.setMediaItem(updatedItem, position)
            player.prepare()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguages(*languageAliases(result.language).toTypedArray())
                .setSelectUndeterminedTextLanguage(true)
                .build()
            player.playWhenReady = wasPlaying
            subtitleSettings = subtitleSettings.copy(
                subtitlesEnabled = true,
                subtitleLanguage = canonicalLanguage(result.language),
            )
            settingsStore.save(subtitleSettings)
            onlineSubtitleBusy = false
            panel = TrackPanel.None
            controlsVisible = true
            controlsActivity += 1
        }
    }

    LaunchedEffect(
        controlsVisible,
        panel,
    ) {
        if (controlsVisible && panel == TrackPanel.None) {
            delay(90)
            runCatching {
                when (lastControl) {
                    LastPlayerControl.Play -> pauseFocusRequester
                    LastPlayerControl.Audio -> audioFocusRequester
                    LastPlayerControl.Subtitle -> subtitleFocusRequester
                    LastPlayerControl.SubtitleStyle -> subtitleStyleFocusRequester
                    LastPlayerControl.Fit -> fitFocusRequester
                }.requestFocus()
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

            val nowElapsed = android.os.SystemClock.elapsedRealtime()
            if (
                currentMs > 0L &&
                nowElapsed - lastProgressWriteAtMs >= 10_000L
            ) {
                saveProgress()
                lastProgressWriteAtMs = nowElapsed
            }

            delay(1_000)
        }
    }

    DisposableEffect(player, playerViewRef, subtitleSettings.subtitleDelayMs) {
        if (subtitleSettings.subtitleDelayMs <= 0 || playerViewRef == null) {
            onDispose { }
        } else {
            var pendingCue: Job? = null
            val cueListener = object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    pendingCue?.cancel()
                    playerViewRef?.post { playerViewRef?.subtitleView?.setCues(emptyList()) }
                    pendingCue = coroutineScope.launch {
                        delay(subtitleSettings.subtitleDelayMs.toLong())
                        playerViewRef?.subtitleView?.setCues(cueGroup.cues)
                    }
                }
            }
            player.addListener(cueListener)
            onDispose {
                pendingCue?.cancel()
                player.removeListener(cueListener)
            }
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
                when (lastControl) {
                    LastPlayerControl.Play -> pauseFocusRequester
                    LastPlayerControl.Audio -> audioFocusRequester
                    LastPlayerControl.Subtitle -> subtitleFocusRequester
                    LastPlayerControl.SubtitleStyle -> subtitleStyleFocusRequester
                    LastPlayerControl.Fit -> fitFocusRequester
                }.requestFocus()
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
                            Key.NumPadEnter ||
                            event.key ==
                            Key.DirectionLeft ||
                            event.key ==
                            Key.DirectionRight ||
                            event.key ==
                            Key.DirectionUp ||
                            event.key ==
                            Key.DirectionDown
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
                    playerViewRef = this
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

                    // The native PlayerView must never own TV focus.
                    // Compose controls are the only DPAD focus graph.
                    isFocusable = false
                    isFocusableInTouchMode = false

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
                            0.065f,
                        )
                    applySubtitleStyle(
                        this,
                        subtitleSettings,
                    )
                }
            },
            update = {
                view ->

                view.player =
                    player
                view.isFocusable =
                    false
                view.isFocusableInTouchMode =
                    false
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
                        0.065f,
                    )
                applySubtitleStyle(
                    view,
                    subtitleSettings,
                )
            },
        )

        // Touch/mouse path is intentionally separate from DPAD:
        // a tap anywhere on the video reveals the controls and puts
        // remote focus on Play/Pause. This layer only exists while
        // controls are hidden, so it never blocks the actual buttons.
        if (
            !controlsVisible &&
            panel ==
            TrackPanel.None
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(
                            request.sessionId,
                        ) {
                            detectTapGestures(
                                onTap = {
                                    revealControls()

                                    runCatching {
                                        pauseFocusRequester
                                            .requestFocus()
                                    }
                                },
                            )
                        },
            )
        }

        if (
            controlsVisible
        ) {
            val displayName = currentSaved().name
            val mainTitle = displayName.substringBefore(" • ")
            val episodeLabel = displayName.substringAfter(" • ", "")
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
                        PlayerControlIcon.Back,
                    contentDescription =
                        strings["back"],
                    onActivity = {
                        revealControls()
                    },
                    onClick =
                        onClose,
                )
            }

            if (request.item.kind != "live") {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvPlayerIconButton(
                        icon = PlayerControlIcon.Replay10,
                        contentDescription = "10 saniye geri",
                        onActivity = { revealControls() },
                    ) {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                    }
                    TvPlayerIconButton(
                        icon = if (playing) PlayerControlIcon.Pause else PlayerControlIcon.Play,
                        contentDescription = if (playing) strings["pause"] else strings["resume"],
                        modifier = Modifier.focusRequester(pauseFocusRequester),
                        prominent = true,
                        onActivity = {
                            lastControl = LastPlayerControl.Play
                            revealControls()
                        },
                    ) {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    TvPlayerIconButton(
                        icon = PlayerControlIcon.Forward10,
                        contentDescription = "10 saniye ileri",
                        onActivity = { revealControls() },
                    ) {
                        val end = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(end))
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xB8000508),
                                    Color(0xF500070B),
                                ),
                            ),
                        )
                        .padding(
                            start = 42.dp,
                            end = 42.dp,
                            top = 42.dp,
                            bottom = 18.dp,
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        6.dp,
                    ),
            ) {
                Text(
                    mainTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (episodeLabel.isNotBlank()) {
                    Text(
                        episodeLabel,
                        color = Color(0xB3CBD5E1),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatTime(currentMs), color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelMedium)
                    TvSeekBar(
                        currentMs = currentMs,
                        durationMs = durationMs,
                        modifier = Modifier.weight(1f),
                        onSeekBy = { delta ->
                            val maximum = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                            player.seekTo((player.currentPosition + delta).coerceIn(0L, maximum))
                            revealControls()
                        },
                    )
                    Text(formatTime(durationMs), color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelMedium)
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement
                            .End,
                    verticalAlignment =
                        Alignment
                            .CenterVertically,
                ) {
                    // Main playback controls are centered above this utility row.
                    if (false) Row(
                        horizontalArrangement =
                    Arrangement.spacedBy(
                                5.dp,
                            ),
                        verticalAlignment =
                            Alignment
                                .CenterVertically,
                    ) {
                        TvPlayerIconButton(
                            icon =
                                "−10 sn",
                            contentDescription =
                                "10 saniye geri",
                            onActivity = {
                                revealControls()
                            },
                            wide = true,
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
                                "+10 sn",
                            contentDescription =
                                "10 saniye ileri",
                            onActivity = {
                                revealControls()
                            },
                            wide = true,
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
                                5.dp,
                            ),
                        verticalAlignment =
                            Alignment
                                .CenterVertically,
                    ) {
                        TvPlayerIconButton(
                            icon =
                                PlayerControlIcon.Audio,
                            contentDescription =
                                strings[
                                    "audio"
                                ],
                            modifier = Modifier.focusRequester(audioFocusRequester),
                            onActivity = {
                                lastControl = LastPlayerControl.Audio
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
                                PlayerControlIcon.Captions,
                            contentDescription =
                                strings[
                                    "subtitles"
                                ],
                            modifier = Modifier.focusRequester(subtitleFocusRequester),
                            onActivity = {
                                lastControl = LastPlayerControl.Subtitle
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
                            icon = PlayerControlIcon.SubtitleStyle,
                            contentDescription = "Altyazı görünümü",
                            modifier = Modifier.focusRequester(subtitleStyleFocusRequester),
                            onActivity = {
                                lastControl = LastPlayerControl.SubtitleStyle
                                revealControls()
                            },
                        ) {
                            panel =
                                TrackPanel.SubtitleStyle
                            controlsActivity +=
                                1
                        }

                        TvPlayerIconButton(
                            icon =
                                if (
                                    fitMode ==
                                    "fill"
                                ) {
                                    PlayerControlIcon.Fit
                                } else {
                                    PlayerControlIcon.Fill
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
                            modifier = Modifier.focusRequester(fitFocusRequester),
                            onActivity = {
                                lastControl = LastPlayerControl.Fit
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
            if (panel == TrackPanel.SubtitleStyle) {
                SubtitleStylePanel(
                    settings = subtitleSettings,
                    onSettingsChanged = { next ->
                        subtitleSettings = next
                        settingsStore.save(next)
                    },
                    onClose = {
                        panel = TrackPanel.None
                        revealControls()
                    },
                )
            } else if (panel == TrackPanel.OnlineSubtitle) {
                OnlineSubtitlePanel(
                    busy = onlineSubtitleBusy,
                    results = onlineSubtitleResults,
                    error = onlineSubtitleError,
                    query = onlineSubtitleQuery,
                    language = onlineSubtitleLanguage,
                    onQueryChanged = { onlineSubtitleQuery = it },
                    onLanguageChanged = { onlineSubtitleLanguage = it },
                    onRetry = { searchOnlineSubtitles() },
                    onChoice = { applyOnlineSubtitle(it) },
                    onClose = {
                        panel = TrackPanel.None
                        revealControls()
                    },
                )
            } else {
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
                extraLabel = if (panel == TrackPanel.Subtitle) "OpenSubtitles • Altyazı bul" else null,
                onExtra = {
                    panel = TrackPanel.OnlineSubtitle
                    onlineSubtitleResults = emptyList()
                    searchOnlineSubtitles()
                },
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
                                .setPreferredAudioLanguages(
                                    *languageAliases(choice.language).toTypedArray(),
                                )
                                .build()

                        settingsStore.save(
                            settingsStore
                                .load()
                                .copy(
                                    audioLanguage =
                                        canonicalLanguage(choice.language),
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
                                .setPreferredTextLanguages(
                                    *languageAliases(choice.language).toTypedArray(),
                                )
                                .setSelectUndeterminedTextLanguage(true)
                                .build()

                        settingsStore.save(
                            settingsStore
                                .load()
                                .copy(
                                    subtitlesEnabled =
                                        true,
                                    subtitleLanguage =
                                        canonicalLanguage(choice.language),
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
}

@Composable
private fun TvSeekBar(
    currentMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    onSeekBy: (Long) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress =
        if (durationMs > 0L) {
            (currentMs.toFloat() / durationMs.toFloat())
                .coerceIn(0f, 1f)
        } else {
            0f
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (focused) Color(0x3322D3EE) else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .onFocusChanged {
                    focused = it.isFocused
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onSeekBy(-10_000L)
                                true
                            }
                            Key.DirectionRight -> {
                                onSeekBy(10_000L)
                                true
                            }
                            else -> false
                        }
                    }
                }
                .focusable()
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = if (focused) Color(0xFF22D3EE) else Color(0xFF3B82F6),
            trackColor = Color(0xFF334155),
        )
        if (focused) {
            Text(
                text = "◀ 10 sn geri   •   basılı tutarak hızlı sar   •   10 sn ileri ▶",
                color = Color(0xFFBAE6FD),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun applySubtitleStyle(
    view: PlayerView,
    settings: TvPlaybackSettings,
) {
    val foreground =
        when (settings.subtitleColor) {
            "yellow" -> android.graphics.Color.YELLOW
            "cyan" -> android.graphics.Color.CYAN
            else -> android.graphics.Color.WHITE
        }
    val background =
        if (settings.subtitleBackground == "black") {
            0xCC000000.toInt()
        } else {
            android.graphics.Color.TRANSPARENT
        }
    val edgeType =
        when (settings.subtitleBackground) {
            "none" -> CaptionStyleCompat.EDGE_TYPE_NONE
            "outline" -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            else -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        }

    view.subtitleView?.apply {
        setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            settings.subtitleSizeSp.toFloat(),
        )
        setStyle(
            CaptionStyleCompat(
                foreground,
                background,
                android.graphics.Color.TRANSPARENT,
                edgeType,
                android.graphics.Color.BLACK,
                null,
            ),
        )
    }
}

@Composable
private fun SubtitleStylePanel(
    settings: TvPlaybackSettings,
    onSettingsChanged: (TvPlaybackSettings) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x73000000)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .width(410.dp)
                    .heightIn(max = 620.dp)
                    .padding(end = 26.dp)
                    .background(Color(0xF2111827), RoundedCornerShape(18.dp))
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "Altyazı görünümü",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("Boyut", color = Color(0xFF94A3B8))
            listOf(20 to "Küçük", 26 to "Orta", 32 to "Büyük", 38 to "Çok büyük")
                .forEachIndexed { index, choice ->
                    TvPlayerTextButton(
                        text = if (settings.subtitleSizeSp == choice.first) "✓ ${choice.second}" else choice.second,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier),
                    ) {
                        onSettingsChanged(settings.copy(subtitleSizeSp = choice.first))
                    }
                }

            Text("Yazı rengi", color = Color(0xFF94A3B8))
            listOf("white" to "Beyaz", "yellow" to "Sarı", "cyan" to "Camgöbeği")
                .forEach { choice ->
                    TvPlayerTextButton(
                        text = if (settings.subtitleColor == choice.first) "✓ ${choice.second}" else choice.second,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        onSettingsChanged(settings.copy(subtitleColor = choice.first))
                    }
                }

            Text("Arka plan", color = Color(0xFF94A3B8))
            listOf(
                "shadow" to "Yumuşak gölge",
                "outline" to "Siyah çerçeve",
                "black" to "Siyah kutu",
                "none" to "Arka plansız",
            ).forEach { choice ->
                TvPlayerTextButton(
                    text = if (settings.subtitleBackground == choice.first) "✓ ${choice.second}" else choice.second,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    onSettingsChanged(settings.copy(subtitleBackground = choice.first))
                }
            }

            Text("Altyazı gecikmesi", color = Color(0xFF94A3B8))
            listOf(0 to "Kapalı", 500 to "+0,5 sn", 1_000 to "+1 sn", 2_000 to "+2 sn", 3_000 to "+3 sn")
                .forEach { choice ->
                    TvPlayerTextButton(
                        text = if (settings.subtitleDelayMs == choice.first) "✓ ${choice.second}" else choice.second,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        onSettingsChanged(settings.copy(subtitleDelayMs = choice.first))
                    }
                }

            TvPlayerTextButton(
                text = "Kapat",
                modifier = Modifier.fillMaxWidth(),
                onClick = onClose,
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
    extraLabel: String? = null,
    onExtra: () -> Unit = {},
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
                    .heightIn(max = 620.dp)
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
                    )
                    .verticalScroll(
                        rememberScrollState(),
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
                            (if (choice.selected) "✓ " else "") + if (
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
                extraLabel?.let { label ->
                    TvPlayerTextButton(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onExtra,
                    )
                }
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
private fun OnlineSubtitlePanel(
    busy: Boolean,
    results: List<OnlineSubtitleResult>,
    error: String?,
    query: String,
    language: String,
    onQueryChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onChoice: (OnlineSubtitleResult) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(busy, results.size, error) {
        delay(100)
        runCatching { firstFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x73000000)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier.width(430.dp).heightIn(max = 620.dp).padding(end = 26.dp)
                .background(Color(0xF2111827), RoundedCornerShape(22.dp)).padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("OpenSubtitles", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Film / dizi adı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("tr" to "TR", "en" to "EN", "el" to "GR", "fr" to "FR", "ru" to "RU").forEach { option ->
                    TvPlayerTextButton(
                        text = if (language == option.first) "✓ ${option.second}" else option.second,
                        modifier = Modifier.weight(1f),
                    ) { onLanguageChanged(option.first) }
                }
            }
            TvPlayerTextButton("Ara", Modifier.fillMaxWidth(), onRetry)
            when {
                busy -> Text("Altyazılar aranıyor…", color = Color(0xFFBAE6FD))
                error != null -> {
                    Text(error, color = Color(0xFFFCA5A5))
                    TvPlayerTextButton("Tekrar ara", Modifier.fillMaxWidth().focusRequester(firstFocusRequester), onRetry)
                }
                else -> results.forEachIndexed { index, result ->
                    TvPlayerTextButton(
                        text = "${result.release} · ${result.language.uppercase(Locale.ROOT)}",
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier),
                    ) { onChoice(result) }
                }
            }
            TvPlayerTextButton("Kapat", Modifier.fillMaxWidth().then(if (busy) Modifier.focusRequester(firstFocusRequester) else Modifier), onClose)
        }
    }
}

private fun subtitleMimeType(url: String): String =
    when (url.substringBefore('?').lowercase(Locale.ROOT).substringAfterLast('.', "")) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

@Composable
private fun TvPlayerIconButton(
    icon: Any,
    contentDescription: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    wide: Boolean = false,
    selected: Boolean = false,
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
                .then(
                    if (wide) {
                        Modifier.width(78.dp).height(42.dp)
                    } else {
                        Modifier.size(if (prominent) 48.dp else 42.dp)
                    },
                )
                .background(
                    if (focused) {
                        Color(0xE6142630)
                    } else if (selected) {
                        Color(0xD9112029)
                    } else {
                        Color(0xC90B1117)
                    },
                    RoundedCornerShape(
                        50.dp,
                    ),
                )
                .border(
                    width = if (focused) 1.5.dp else 1.dp,
                    color =
                        if (focused) {
                            Color(0xFF38C9F2)
                        } else {
                            Color(0x40DCE8EF)
                        },
                    shape = RoundedCornerShape(50.dp),
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
                .pointerInput(
                    onClick,
                ) {
                    detectTapGestures(
                        onTap = {
                            onActivity()
                            onClick()
                        },
                    )
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
        if (icon is PlayerControlIcon) {
            PlayerControlGlyph(icon, Modifier.size(if (prominent) 30.dp else 23.dp), focused)
        } else {
            Text(
                text = icon.toString(),
                color = Color.White,
                style = if (prominent) MaterialTheme.typography.titleLarge else if (wide) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PlayerControlGlyph(icon: PlayerControlIcon, modifier: Modifier, focused: Boolean) {
    val color = if (focused) Color(0xFF64D8F7) else Color(0xFFF2F7FA)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = (w * 0.075f).coerceAtLeast(2f))
        when (icon) {
            PlayerControlIcon.Back -> {
                drawLine(color, Offset(w * .72f, h * .20f), Offset(w * .30f, h * .50f), stroke.width)
                drawLine(color, Offset(w * .30f, h * .50f), Offset(w * .72f, h * .80f), stroke.width)
                drawLine(color, Offset(w * .31f, h * .50f), Offset(w * .86f, h * .50f), stroke.width)
            }
            PlayerControlIcon.Play -> {
                val p = Path().apply { moveTo(w * .32f, h * .18f); lineTo(w * .78f, h * .5f); lineTo(w * .32f, h * .82f); close() }
                drawPath(p, color)
            }
            PlayerControlIcon.Pause -> {
                drawRoundRect(color, Offset(w * .27f, h * .18f), Size(w * .15f, h * .64f))
                drawRoundRect(color, Offset(w * .58f, h * .18f), Size(w * .15f, h * .64f))
            }
            PlayerControlIcon.Replay10, PlayerControlIcon.Forward10 -> {
                drawArc(color, if (icon == PlayerControlIcon.Replay10) 35f else 145f, 285f, false, Offset(w * .12f, h * .12f), Size(w * .76f, h * .76f), style = stroke)
                val x = if (icon == PlayerControlIcon.Replay10) w * .18f else w * .82f
                drawLine(color, Offset(x, h * .18f), Offset(x, h * .42f), stroke.width)
                drawLine(color, Offset(w * .42f, h * .38f), Offset(w * .42f, h * .66f), stroke.width)
                drawOval(color, Offset(w * .53f, h * .38f), Size(w * .18f, h * .28f), style = stroke)
            }
            PlayerControlIcon.Audio -> {
                val speaker = Path().apply {
                    moveTo(w * .16f, h * .40f)
                    lineTo(w * .34f, h * .40f)
                    lineTo(w * .55f, h * .23f)
                    lineTo(w * .55f, h * .77f)
                    lineTo(w * .34f, h * .60f)
                    lineTo(w * .16f, h * .60f)
                    close()
                }
                drawPath(speaker, color)
                drawArc(color, -48f, 96f, false, Offset(w * .48f, h * .28f), Size(w * .28f, h * .44f), style = stroke)
                drawArc(color, -48f, 96f, false, Offset(w * .48f, h * .16f), Size(w * .44f, h * .68f), style = stroke)
            }
            PlayerControlIcon.Captions, PlayerControlIcon.OnlineSubtitle -> {
                drawRoundRect(color, Offset(w * .08f, h * .18f), Size(w * .84f, h * .64f), style = stroke)
                drawLine(color, Offset(w * .24f, h * .46f), Offset(w * .45f, h * .46f), stroke.width)
                drawLine(color, Offset(w * .55f, h * .46f), Offset(w * .76f, h * .46f), stroke.width)
            }
            PlayerControlIcon.SubtitleStyle -> {
                drawLine(color, Offset(w * .20f, h * .72f), Offset(w * .42f, h * .22f), stroke.width)
                drawLine(color, Offset(w * .42f, h * .22f), Offset(w * .64f, h * .72f), stroke.width)
                drawLine(color, Offset(w * .29f, h * .52f), Offset(w * .55f, h * .52f), stroke.width)
            }
            PlayerControlIcon.Fit, PlayerControlIcon.Fill -> {
                drawRect(color, Offset(w * .14f, h * .20f), Size(w * .72f, h * .60f), style = stroke)
                if (icon == PlayerControlIcon.Fill) drawRect(color, Offset(w * .30f, h * .34f), Size(w * .40f, h * .32f), style = stroke)
            }
        }
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
                        Color(0xFF123647)
                    } else {
                        Color(0xFF111A22)
                    },
                    RoundedCornerShape(
                        10.dp,
                    ),
                )
                .border(
                    1.dp,
                    if (focused) Color(0xFF38C9F2) else Color(0x334F6675),
                    RoundedCornerShape(10.dp),
                )
                .onFocusChanged {
                    focused =
                        it.isFocused
                }
                .pointerInput(
                    onClick,
                ) {
                    detectTapGestures(
                        onTap = {
                            onClick()
                        },
                    )
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
