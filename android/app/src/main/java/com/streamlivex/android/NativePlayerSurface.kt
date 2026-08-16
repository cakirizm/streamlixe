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
    var subtitlePanelVisible by remember(request.sessionId) { mutableStateOf(false) }
    var subtitleOptions by remember(request.sessionId) { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var selectedSubtitleGroup by remember(request.sessionId) { mutableStateOf<Tracks.Group?>(null) }
    var subtitlePrefs by remember(request.sessionId) { mutableStateOf(request.preferences) }
    var onlineSubtitleResults by remember(request.sessionId) { mutableStateOf<List<OnlineSubtitleResult>>(emptyList()) }
    var onlineSubtitleBusy by remember(request.sessionId) { mutableStateOf(false) }
    var onlineSubtitleError by remember(request.sessionId) { mutableStateOf<String?>(null) }
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
                val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                subtitleOptions = groups.mapIndexed { index, group ->
                    val format = group.mediaTrackGroup.getFormat(0)
                    val label = format.label
                        ?: format.language?.uppercase()
                        ?: "Altyazı ${index + 1}"
                    SubtitleOption(group, label)
                }
                selectedSubtitleGroup = groups.firstOrNull { it.isSelected }
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

    fun updateSubtitleStyle(next: PlaybackPreferences) {
        subtitlePrefs = next
        onPreferencesChanged(next)
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
                    showController()
                    post { requestFocus() }
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
                applySubtitleStyle(view, subtitlePrefs)
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
            if (!request.item.isLive) OutlinedButton(
                onClick = { subtitlePanelVisible = !subtitlePanelVisible },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text("Altyazı", maxLines = 1, style = MaterialTheme.typography.labelSmall)
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

        if (subtitlePanelVisible) SubtitlePanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            options = subtitleOptions,
            selectedGroup = selectedSubtitleGroup,
            prefs = subtitlePrefs,
            onSelect = { selectSubtitle(it) },
            onPrefsChange = { updateSubtitleStyle(it) },
            onClose = { subtitlePanelVisible = false },
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

private val subtitleColorPresets = listOf(
    "#ffffff" to "Beyaz",
    "#ffe066" to "Sarı",
    "#7ce8ff" to "Camgöbeği",
    "#8fffb0" to "Yeşil",
)

@Composable
private fun SubtitlePanel(
    modifier: Modifier = Modifier,
    options: List<SubtitleOption>,
    selectedGroup: Tracks.Group?,
    prefs: PlaybackPreferences,
    onSelect: (SubtitleOption?) -> Unit,
    onPrefsChange: (PlaybackPreferences) -> Unit,
    onClose: () -> Unit,
    onlineResults: List<OnlineSubtitleResult> = emptyList(),
    onlineBusy: Boolean = false,
    onlineError: String? = null,
    onSearchOnline: () -> Unit = {},
    onApplyOnline: (OnlineSubtitleResult) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .background(ComposeColor(0xFF15121D))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Altyazı", color = ComposeColor.White, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onClose) { Text("Kapat", color = ComposeColor(0xFFB8B4C8)) }
        }

        SubtitlePreview(prefs = prefs)

        Text("Parça", color = ComposeColor(0xFF9A94AC), style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubtitleChip(label = "Kapalı", active = selectedGroup == null) { onSelect(null) }
            options.forEach { option ->
                SubtitleChip(label = option.label, active = option.group == selectedGroup) { onSelect(option) }
            }
            if (options.isEmpty()) Text(
                "Bu içerik için altyazı bulunamadı",
                color = ComposeColor(0xFF7C7690),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("İnternetten altyazı bul", color = ComposeColor(0xFF9A94AC), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onSearchOnline, enabled = !onlineBusy) {
                if (onlineBusy) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = ComposeColor(0xFFD84CFF),
                    )
                } else {
                    Text("Ara", color = ComposeColor(0xFFD84CFF), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (onlineError != null) Text(
            onlineError,
            color = ComposeColor(0xFFFF8A8A),
            style = MaterialTheme.typography.labelSmall,
        )
        if (onlineResults.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            onlineResults.forEach { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(ComposeColor(0xFF241F32))
                        .clickable { onApplyOnline(result) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        result.language.uppercase(),
                        color = ComposeColor(0xFFD84CFF),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        result.release,
                        color = ComposeColor(0xFFB8B4C8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text("Boyut  %${prefs.subtitleSize}", color = ComposeColor(0xFF9A94AC), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = prefs.subtitleSize.toFloat(),
            onValueChange = { onPrefsChange(prefs.copy(subtitleSize = it.toInt())) },
            valueRange = 70f..180f,
            steps = 10,
        )

        Text("Renk", color = ComposeColor(0xFF9A94AC), style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            subtitleColorPresets.forEach { (hex, _) ->
                val active = prefs.subtitleColor.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ComposeColor(android.graphics.Color.parseColor(hex)))
                        .border(
                            width = if (active) 3.dp else 1.dp,
                            color = if (active) ComposeColor(0xFFD84CFF) else ComposeColor(0x33FFFFFF),
                            shape = CircleShape,
                        )
                        .clickable { onPrefsChange(prefs.copy(subtitleColor = hex)) },
                )
            }
        }

        Text("Arka plan", color = ComposeColor(0xFF9A94AC), style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("shadow" to "Gölge", "box" to "Koyu kutu", "none" to "Yok").forEach { (value, label) ->
                SubtitleChip(label = label, active = prefs.subtitleBackground == value) {
                    onPrefsChange(prefs.copy(subtitleBackground = value))
                }
            }
        }
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
            .background(ComposeColor(0xFF000000))
            .padding(vertical = 22.dp, horizontal = 10.dp),
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
private fun SubtitleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) ComposeColor.White else ComposeColor(0xFFB8B4C8),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (active) ComposeColor(0xFF7849DB) else ComposeColor(0xFF241F32))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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
