package com.streamlivex.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun NativePlayerSurface(
    request: PlaybackRequest,
    player: ExoPlayer,
    onClose: (PlaybackProgress) -> Unit,
    onProgress: (PlaybackProgress) -> Unit,
    onFailure: (String) -> Unit,
    onPreferencesChanged: (PlaybackPreferences) -> Unit = {},
    externalKeyEvent: Triple<Int, Int, Long>? = null,
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
    var tracksPanelVisible by remember(request.sessionId) { mutableStateOf(false) }
    var subtitleOptions by remember(request.sessionId) { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var selectedSubtitleGroup by remember(request.sessionId) { mutableStateOf<Tracks.Group?>(null) }
    var audioOptions by remember(request.sessionId) { mutableStateOf<List<AudioOption>>(emptyList()) }
    var selectedAudioGroup by remember(request.sessionId) { mutableStateOf<Tracks.Group?>(null) }
    var subtitlePrefs by remember(request.sessionId) { mutableStateOf(request.preferences) }
    var onlineSubtitleResults by remember(request.sessionId) { mutableStateOf<List<OnlineSubtitleResult>>(emptyList()) }
    var onlineSubtitleBusy by remember(request.sessionId) { mutableStateOf(false) }
    var onlineSubtitleError by remember(request.sessionId) { mutableStateOf<String?>(null) }
    // Panel acikken kumandayla hangi satirin "isaretli" oldugunu Android/Compose'un ambient
    // odak sistemine hic guvenmeden kendimiz takip ediyoruz (0=ses sutunu, 1=altyazi sutunu).
    var panelColumn by remember(request.sessionId) { mutableIntStateOf(0) }
    var panelIndex by remember(request.sessionId) { mutableIntStateOf(0) }
    var playerViewRef by remember(request.sessionId) { mutableStateOf<PlayerView?>(null) }
    val coroutineScope = rememberCoroutineScope()
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

            override fun onTracksChanged(tracks: Tracks) {
                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                subtitleOptions = textGroups.mapIndexed { index, group ->
                    val format = group.mediaTrackGroup.getFormat(0)
                    val label = format.label
                        ?: format.language?.uppercase()
                        ?: "Altyazı ${index + 1}"
                    SubtitleOption(group, label)
                }
                selectedSubtitleGroup = textGroups.firstOrNull { it.isSelected }

                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                audioOptions = audioGroups.mapIndexed { index, group ->
                    val format = group.mediaTrackGroup.getFormat(0)
                    val label = format.label
                        ?: format.language?.uppercase()
                        ?: "Ses ${index + 1}"
                    AudioOption(group, label)
                }
                selectedAudioGroup = audioGroups.firstOrNull { it.isSelected }
            }
        }
        player.addListener(listener)
        // Paylasilan player, bu Composable'a baglanmadan ONCE zaten hazirlanmis olabilir
        // (on izlemeden tam ekrana gecis gibi) -- bu durumda onTracksChanged bir daha
        // tetiklenmez, cunku parcalar zaten belliydi. Mevcut durumu hemen elle besleyerek
        // "altyazi verisi olsa bile 'bulunamadi' gorunmesi" hatasini onluyoruz.
        listener.onTracksChanged(player.currentTracks)
        onDispose {
            onProgress(progress())
            player.removeListener(listener)
        }
    }

    LaunchedEffect(candidateIndex, candidateRetry, request.sessionId) {
        // On izlemeden tam ekrana gecerken ayni paylasilan player zaten bu adayla oynatiyor
        // olabilir -- boyle bir durumda sifirdan setMediaItem/prepare cagirip yayini yeniden
        // baslatmiyoruz, sadece mevcut oynatmayi devam ettiriyoruz. player.isPlaying gibi
        // zamanlamaya bagli bir kontrol yerine kesin bir kayit (PlaybackStartTracker) kullaniyoruz.
        val startKey = "${request.sessionId}:$candidateIndex:$candidateRetry"
        if (PlaybackStartTracker.hasStarted(startKey)) {
            ready = player.playbackState == Player.STATE_READY
            return@LaunchedEffect
        }
        PlaybackStartTracker.markStarted(startKey)
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

    fun selectSubtitle(option: SubtitleOption?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
            clearOverridesOfType(C.TRACK_TYPE_TEXT)
            if (option != null) {
                setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, 0))
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            } else {
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
        }.build()
        selectedSubtitleGroup = option?.group
        subtitlePrefs = subtitlePrefs.copy(
            subtitleMode = if (option != null) "on" else "off",
            subtitleLanguage = option?.group?.mediaTrackGroup?.getFormat(0)?.language ?: subtitlePrefs.subtitleLanguage,
        )
        onPreferencesChanged(subtitlePrefs)
    }

    fun selectAudio(option: AudioOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, 0))
            .build()
        selectedAudioGroup = option.group
        subtitlePrefs = subtitlePrefs.copy(audioLanguage = option.group.mediaTrackGroup.getFormat(0).language ?: subtitlePrefs.audioLanguage)
        onPreferencesChanged(subtitlePrefs)
    }

    fun updateSubtitleStyle(next: PlaybackPreferences) {
        subtitlePrefs = next
        onPreferencesChanged(next)
    }

    // Panel acilinca/kapaninca secili satiri sifirla ve PlayerView'in gercek Android View
    // odagini acikca birak/geri al -- Compose'un kendi FocusRequester/focusGroup sistemi
    // PlayerView'dan BAGIMSIZ calisir, bu yuzden bu olmadan panel "odaklanmis" gorunse bile
    // donanim tuslari hala PlayerView'a gidip hicbir sey olmuyordu.
    LaunchedEffect(tracksPanelVisible) {
        if (tracksPanelVisible) {
            panelColumn = 0
            panelIndex = 0
            playerViewRef?.clearFocus()
        } else {
            playerViewRef?.post { playerViewRef?.requestFocus() }
        }
    }

    // Kumanda tuslarini View/Compose odak sistemine hic guvenmeden burada isliyoruz (bkz.
    // MainActivity.dispatchKeyEvent + externalKeyEvent). Panel acikken yukari/asagi secili
    // satiri, sag/sol sutunu (ses/altyazi) degistirir; OK secili parcayi uygular. Panel
    // kapaliyken OK, oynatici kontrollerini (oynat/duraklat cubugu) ac/kapat yapar.
    LaunchedEffect(externalKeyEvent) {
        val event = externalKeyEvent ?: return@LaunchedEffect
        val (keyCode, action, _) = event
        if (action != android.view.KeyEvent.ACTION_DOWN) return@LaunchedEffect
        if (tracksPanelVisible) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { panelColumn = 0; panelIndex = 0 }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { panelColumn = 1; panelIndex = 0 }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> panelIndex = (panelIndex - 1).coerceAtLeast(0)
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val maxIndex = if (panelColumn == 0) (audioOptions.size - 1).coerceAtLeast(0) else subtitleOptions.size
                    panelIndex = (panelIndex + 1).coerceAtMost(maxIndex)
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                    if (panelColumn == 0) {
                        audioOptions.getOrNull(panelIndex)?.let { selectAudio(it) }
                    } else {
                        if (panelIndex == 0) selectSubtitle(null) else subtitleOptions.getOrNull(panelIndex - 1)?.let { selectSubtitle(it) }
                    }
                }
                android.view.KeyEvent.KEYCODE_BACK -> tracksPanelVisible = false
            }
        } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            playerViewRef?.let { view -> if (view.isControllerFullyVisible) view.hideController() else view.showController() }
        }
    }

    fun searchOnlineSubtitles() {
        if (onlineSubtitleBusy) return
        onlineSubtitleBusy = true
        onlineSubtitleError = null
        coroutineScope.launch {
            try {
                val results = SubtitleSearchClient.search(request.item.name)
                onlineSubtitleResults = results
                if (results.isEmpty()) onlineSubtitleError = "İnternette bu içerik için altyazı bulunamadı"
            } catch (_: Exception) {
                onlineSubtitleError = "Altyazı sitesine ulaşılamadı"
            } finally {
                onlineSubtitleBusy = false
            }
        }
    }

    // Secilen cevrimici altyaziyi indirip, oynatmayi bastan baslatmadan (ayni konumdan devam
    // ederek) mevcut MediaItem'a yeni bir altyazi parcasi olarak ekler -- VLC'de "harici altyazi
    // ekle" davranisina benzer sekilde.
    fun applyOnlineSubtitle(result: OnlineSubtitleResult) {
        if (onlineSubtitleBusy) return
        onlineSubtitleBusy = true
        onlineSubtitleError = null
        coroutineScope.launch {
            try {
                val url = SubtitleSearchClient.resolveDownloadUrl(result.fileId)
                if (url == null) {
                    onlineSubtitleError = "İndirme bağlantısı alınamadı"
                    return@launch
                }
                val currentItem = player.currentMediaItem
                if (currentItem == null) {
                    onlineSubtitleError = "Oynatıcı hazır değil"
                    return@launch
                }
                val position = player.currentPosition
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                    .setMimeType(subtitleMimeType(url))
                    .setLanguage(result.language)
                    .setLabel(result.release)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                val existing = currentItem.localConfiguration?.subtitleConfigurations.orEmpty()
                val newItem = currentItem.buildUpon()
                    .setSubtitleConfigurations(existing + subtitleConfig)
                    .build()
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                player.setMediaItem(newItem, position)
                player.prepare()
                player.play()
                subtitlePrefs = subtitlePrefs.copy(subtitleMode = "on", subtitleLanguage = result.language)
                onPreferencesChanged(subtitlePrefs)
                onlineSubtitleResults = emptyList()
            } catch (_: Exception) {
                onlineSubtitleError = "Altyazı eklenemedi"
            } finally {
                onlineSubtitleBusy = false
            }
        }
    }

    // Altyazi paneli acikken geri tusu tum oynaticiyi degil, sadece paneli kapatmali --
    // oncesinde ust duzeyde her zaman etkin olan tek bir BackHandler (MainActivity'de)
    // oldugu icin panel acikken bile geri basinca filmden direkt cikiliyordu.
    BackHandler(enabled = tracksPanelVisible) { tracksPanelVisible = false }

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
                    applySubtitleStyle(this, subtitlePrefs)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                        },
                    )
                    // NOT: setOnKeyListener burada cihazda dogrulandi ki hic tetiklenmiyor --
                    // PlayerView (bir AndroidView) tam ekran oynaticida gercek Android View
                    // odagini guvenilir sekilde alamiyor. Kumanda tuslarini artik View odak
                    // sistemine hic guvenmeden, MainActivity.dispatchKeyEvent'ten dogrudan
                    // Compose state'ine (externalKeyEvent) aktarip asagidaki LaunchedEffect'te
                    // isliyoruz.
                    showController()
                    post { requestFocus() }
                }
            },
            update = { view ->
                playerViewRef = view
                view.player = player
                view.resizeMode = resizeMode
                applySubtitleStyle(view, subtitlePrefs)
                // "Ses ve Altyazı" paneli acikken burasi her recompose'da tekrar calisip
                // PlayerView'a requestFocus() cagiriyordu -- kullanici paneldeki bir satira
                // kumandayla odaklaninca bu odak aninda video görünümüne geri cekiliyor,
                // panel kumandayla gezilemez hale geliyordu. Panel acikken odagi geri
                // calmiyoruz.
                if (!tracksPanelVisible && !view.hasFocus()) view.post { view.requestFocus() }
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
            if (!request.item.isLive) OutlinedButton(
                onClick = { tracksPanelVisible = !tracksPanelVisible },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text("🎧 Ses ve Altyazı", maxLines = 1, style = MaterialTheme.typography.labelSmall)
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

        if (tracksPanelVisible) TracksPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            audioOptions = audioOptions,
            selectedAudioGroup = selectedAudioGroup,
            onSelectAudio = { selectAudio(it) },
            subtitleOptions = subtitleOptions,
            selectedSubtitleGroup = selectedSubtitleGroup,
            highlightColumn = panelColumn,
            highlightIndex = panelIndex,
            prefs = subtitlePrefs,
            onSelectSubtitle = { selectSubtitle(it) },
            onPrefsChange = { updateSubtitleStyle(it) },
            onClose = { tracksPanelVisible = false },
            onlineResults = onlineSubtitleResults,
            onlineBusy = onlineSubtitleBusy,
            onlineError = onlineSubtitleError,
            onSearchOnline = { searchOnlineSubtitles() },
            onApplyOnline = { applyOnlineSubtitle(it) },
        )

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
    // Birden fazla altyazi varsa hepsini "varsayilan" olarak isaretlemek belirsiz davranisa
    // yol acar; sadece tercih edilen dile (ya da hicbiri eslesmezse ilkine) varsayilan
    // isaretini koyuyoruz, digerleri secilebilir kalmaya devam ediyor.
    val preferredIndex = request.item.subtitles.indexOfFirst {
        it.language.equals(request.preferences.subtitleLanguage, ignoreCase = true)
    }.let { if (it >= 0) it else 0 }
    val subtitleConfigurations = request.item.subtitles.mapIndexed { index, subtitle ->
        val isDefault = request.preferences.subtitleMode != "off" && index == preferredIndex
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.src))
            .setMimeType(subtitleMimeType(subtitle.src))
            .setLabel(subtitle.label)
            .setLanguage(subtitle.language)
            .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
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

internal data class SubtitleOption(val group: Tracks.Group, val label: String)
internal data class AudioOption(val group: Tracks.Group, val label: String)

private val subtitleColorPresets = listOf(
    "#ffffff" to "Beyaz",
    "#ffe066" to "Sarı",
    "#7ce8ff" to "Camgöbeği",
    "#8fffb0" to "Yeşil",
)

private val PanelAccent = ComposeColor(0xFFD84CFF)
private val PanelAccentDim = ComposeColor(0xFF7849DB)
private val PanelCardBg = ComposeColor(0x14FFFFFF)
private val PanelMuted = ComposeColor(0xFF9A94AC)
private val PanelFaint = ComposeColor(0xFF6F6982)

@Composable
private fun TracksPanel(
    modifier: Modifier = Modifier,
    audioOptions: List<AudioOption>,
    selectedAudioGroup: Tracks.Group?,
    onSelectAudio: (AudioOption) -> Unit,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleGroup: Tracks.Group?,
    prefs: PlaybackPreferences,
    onSelectSubtitle: (SubtitleOption?) -> Unit,
    onPrefsChange: (PlaybackPreferences) -> Unit,
    onClose: () -> Unit,
    highlightColumn: Int = -1,
    highlightIndex: Int = -1,
    onlineResults: List<OnlineSubtitleResult> = emptyList(),
    onlineBusy: Boolean = false,
    onlineError: String? = null,
    onSearchOnline: () -> Unit = {},
    onApplyOnline: (OnlineSubtitleResult) -> Unit = {},
) {
    val panelFocusRequester = remember { FocusRequester() }
    // Panel acilinca kumanda odagini icine tasiyoruz -- aksi halde D-pad, panel arkasindaki
    // (gorunmeyen) video/ust cubuk elemanlari arasinda dolanmaya devam ediyor, panel hic
    // erisilemez gibi duruyordu.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        runCatching { panelFocusRequester.requestFocus() }
    }
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth(0.62f)
            .focusRequester(panelFocusRequester)
            .focusGroup(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = ComposeColor(0xE0120E1C),
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(ComposeColor(0x33FFFFFF)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(PanelAccentDim, PanelAccent),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text("🎧", style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.weight(1f)) {
                    Text("Ses ve Altyazı", color = ComposeColor.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text("Parçayı seç, görünümü ayarla", color = PanelMuted, style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ComposeColor(0x1AFFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = ComposeColor.White, style = MaterialTheme.typography.labelMedium) }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(ComposeColor(0xFF07070C), ComposeColor(0xFF121017)),
                        ),
                    )
                    .border(1.dp, ComposeColor(0x1AFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
            ) { SubtitlePreview(prefs = prefs) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(PanelCardBg)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelSectionLabel("🔊", "Ses kaynağı", audioOptions.size.coerceAtLeast(1))
                    if (audioOptions.isEmpty()) {
                        Text("Tek ses parçası", color = PanelFaint, style = MaterialTheme.typography.labelSmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            audioOptions.forEachIndexed { index, option ->
                                TrackRow(
                                    label = option.label,
                                    active = option.group == selectedAudioGroup,
                                    highlighted = highlightColumn == 0 && highlightIndex == index,
                                ) { onSelectAudio(option) }
                            }
                        }
                    }
                }

                androidx.compose.material3.VerticalDivider(color = ComposeColor(0x1AFFFFFF))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelSectionLabel("💬", "Altyazı kaynağı", subtitleOptions.size)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TrackRow(
                            label = "Kapalı",
                            active = selectedSubtitleGroup == null,
                            highlighted = highlightColumn == 1 && highlightIndex == 0,
                        ) { onSelectSubtitle(null) }
                        subtitleOptions.forEachIndexed { index, option ->
                            TrackRow(
                                label = option.label,
                                active = option.group == selectedSubtitleGroup,
                                highlighted = highlightColumn == 1 && highlightIndex == index + 1,
                            ) { onSelectSubtitle(option) }
                        }
                        if (subtitleOptions.isEmpty()) Text("Yerleşik altyazı yok", color = PanelFaint, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(PanelCardBg)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PanelSectionLabel("🌐", "İnternetten altyazı bul", null)
                    Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(if (onlineBusy) ComposeColor(0x1AFFFFFF) else PanelAccentDim)
                            .clickable(enabled = !onlineBusy) { onSearchOnline() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (onlineBusy) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = PanelAccent,
                            )
                        } else {
                            Text("Ara", color = ComposeColor.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (onlineError != null) Text(onlineError, color = ComposeColor(0xFFFF8A8A), style = MaterialTheme.typography.labelSmall)
                if (onlineResults.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    onlineResults.forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .background(ComposeColor(0x1F000000))
                                .clickable { onApplyOnline(result) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                    .background(ComposeColor(0x33D84CFF))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(result.language.uppercase(), color = PanelAccent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                result.release,
                                color = ComposeColor(0xFFCFC9DC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text("↓", color = PanelMuted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(PanelCardBg)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PanelSectionLabel("🎨", "Görünüm", null)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Boyut", color = PanelMuted, style = MaterialTheme.typography.labelSmall)
                    Text("%${prefs.subtitleSize}", color = ComposeColor.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
                Slider(
                    value = prefs.subtitleSize.toFloat(),
                    onValueChange = { onPrefsChange(prefs.copy(subtitleSize = it.toInt())) },
                    valueRange = 70f..180f,
                    steps = 10,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = PanelAccent,
                        activeTrackColor = PanelAccent,
                        inactiveTrackColor = ComposeColor(0x26FFFFFF),
                    ),
                )

                Text("Renk", color = PanelMuted, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    subtitleColorPresets.forEach { (hex, _) ->
                        val active = prefs.subtitleColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(ComposeColor(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (active) 3.dp else 1.dp,
                                    color = if (active) PanelAccent else ComposeColor(0x33FFFFFF),
                                    shape = CircleShape,
                                )
                                .clickable { onPrefsChange(prefs.copy(subtitleColor = hex)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (active) Text("✓", color = ComposeColor.Black, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Arka plan", color = PanelMuted, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("shadow" to "Gölge", "box" to "Koyu kutu", "none" to "Yok").forEach { (value, label) ->
                        TrackRow(label = label, active = prefs.subtitleBackground == value, compact = true) {
                            onPrefsChange(prefs.copy(subtitleBackground = value))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelSectionLabel(icon: String, text: String, count: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(icon, style = MaterialTheme.typography.labelMedium)
        Text(text, color = PanelMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        if (count != null) Text("· $count", color = PanelFaint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SubtitlePreview(prefs: PlaybackPreferences) {
    val textColor = runCatching { ComposeColor(android.graphics.Color.parseColor(prefs.subtitleColor)) }
        .getOrDefault(ComposeColor.White)
    val fontSize = (16f * prefs.subtitleSize.coerceIn(70, 180) / 100f).sp
    val boxBackground = if (prefs.subtitleBackground == "box") ComposeColor(0xCC000000) else ComposeColor.Transparent
    val shadow = if (prefs.subtitleBackground == "shadow") {
        Shadow(color = ComposeColor.Black, offset = androidx.compose.ui.geometry.Offset(2f, 2f), blurRadius = 6f)
    } else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Altyazı önizleme metni",
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(shadow = shadow),
            modifier = Modifier
                .background(boxBackground, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .padding(horizontal = if (prefs.subtitleBackground == "box") 8.dp else 0.dp, vertical = if (prefs.subtitleBackground == "box") 2.dp else 0.dp),
        )
    }
}

@Composable
private fun TrackRow(label: String, active: Boolean, compact: Boolean = false, highlighted: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .let { if (compact) it else it.fillMaxWidth() }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(if (active) PanelAccentDim else ComposeColor(0x14FFFFFF))
            .let { if (highlighted) it.border(2.dp, PanelAccent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) else it }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            color = if (active) ComposeColor.White else ComposeColor(0xFFB8B4C8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            modifier = if (compact) Modifier else Modifier.weight(1f),
        )
        if (active) Text("✓", color = ComposeColor.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
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
