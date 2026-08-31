package com.streamlivex.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    onNext: () -> Unit = {},
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
    // Yayin "hazirlaniyor" (buffering) durumunda sonsuza kadar takilabiliyordu (yavas/erisilemez
    // kaynak ya da bu cihazin cozemedigi codec) -- kullaniciya hicbir hata gosterilmeden ekran
    // donup duruyordu. Bu durumu izleyip belirli bir sure sonra hata/aday-degistirme yoluna
    // sokuyoruz (asagidaki LaunchedEffect).
    var buffering by remember(request.sessionId) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
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
    // odak sistemine hic guvenmeden kendimiz takip ediyoruz -- TEK duz liste (bkz. PanelRow).
    var panelFocusIndex by remember(request.sessionId) { mutableIntStateOf(0) }
    val panelRows = remember(audioOptions, subtitleOptions, onlineSubtitleResults) {
        buildPanelRows(audioOptions, subtitleOptions, onlineSubtitleResults)
    }
    var playerViewRef by remember(request.sessionId) { mutableStateOf<PlayerView?>(null) }
    // Kumanda solda/sagda basitce +10sn/-10sn saniye atlayinca gorunur hicbir geri bildirim
    // yoktu ("boşa gidiyor" sikayeti) -- kisa sureli bir "+10sn/-10sn" rozeti gosterip
    // otomatik olarak soluyoruz.
    var seekIndicator by remember(request.sessionId) { mutableStateOf<Pair<Boolean, Long>?>(null) }
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
                buffering = state == Player.STATE_BUFFERING
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

    // Buffering zaman asimi: yayin ~25 sn boyunca "hazirlaniyor"da takili kalir ve hazir olmazsa
    // (kaynak yanit vermiyor / bu cihaz codec'i cozemiyor), sonsuz donme yerine varsa siradaki
    // adayi deniyoruz; aday kalmadiysa net bir hatayla bitiriyoruz. Efekt `buffering`'e bagli
    // oldugundan, yayin bu sure icinde hazir olursa (buffering=false) otomatik iptal olur.
    LaunchedEffect(buffering, candidateIndex, candidateRetry, request.sessionId) {
        if (!buffering || failed || ready) return@LaunchedEffect
        delay(25_000)
        if (buffering && !ready && !failed) {
            if (candidateIndex + 1 < candidates.size) {
                candidateRetry = 0
                candidateIndex += 1
            } else {
                failed = true
                ready = false
                onFailure(streamFailedMessage)
            }
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

    // Panel acilinca/kapaninca secili satiri sifirla ve PlayerView'in gercek Android View
    // odagini acikca birak/geri al -- Compose'un kendi FocusRequester/focusGroup sistemi
    // PlayerView'dan BAGIMSIZ calisir, bu yuzden bu olmadan panel "odaklanmis" gorunse bile
    // donanim tuslari hala PlayerView'a gidip hicbir sey olmuyordu.
    LaunchedEffect(tracksPanelVisible) {
        if (tracksPanelVisible) {
            panelFocusIndex = 0
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
            val current = panelRows.getOrNull(panelFocusIndex)
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> panelFocusIndex = (panelFocusIndex - 1).coerceAtLeast(0)
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> panelFocusIndex = (panelFocusIndex + 1).coerceAtMost((panelRows.size - 1).coerceAtLeast(0))
                // Sol/sag yalnizca "ayarlanabilir" satirlarda (Boyut/Renk/Arka plan) deger
                // degistirir -- listeleri (ses/altyazi/arama sonuclari) etkilemez.
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> adjustPanelRow(current, subtitlePrefs, -1)?.let(::updateSubtitleStyle)
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> adjustPanelRow(current, subtitlePrefs, 1)?.let(::updateSubtitleStyle)
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> when (current) {
                    is PanelRow.AudioRow -> selectAudio(current.option)
                    is PanelRow.SubtitleOffRow -> selectSubtitle(null)
                    is PanelRow.SubtitleRow -> selectSubtitle(current.option)
                    is PanelRow.SearchOnlineRow -> searchOnlineSubtitles()
                    is PanelRow.OnlineResultRow -> applyOnlineSubtitle(current.result)
                    else -> Unit
                }
                android.view.KeyEvent.KEYCODE_BACK -> tracksPanelVisible = false
            }
        } else {
            // Panel kapaliyken: ExoPlayer'in kendi PlayerControlView butonlari (oynat/duraklat,
            // ileri/geri sarma) da ayni View odak sorunundan muzdarip -- kumandayla o
            // butonlara erisilemiyordu ("dur calismiyor"). Kontrolleri sadece gorunur/gizli
            // yapmak yerine, gorunurken OK dogrudan oynat/duraklat yapiyor, yukari da her
            // zaman doğrudan Ses ve Altyazı panelini aciyor (ayri bir "yukari git" gezinme
            // zinciri gerekmeden -- "yukarida altyazıya gitmiyor" sikayeti).
            val view = playerViewRef
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                    if (view != null && !view.isControllerFullyVisible) {
                        view.showController()
                    } else {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> if (!request.item.isLive) tracksPanelVisible = true
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                    seekIndicator = false to System.nanoTime()
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    player.seekTo(player.currentPosition + 10_000)
                    seekIndicator = true to System.nanoTime()
                }
            }
        }
    }

    // Seek rozeti kisa bir sure sonra kendiliğinden kaybolsun diye.
    LaunchedEffect(seekIndicator) {
        if (seekIndicator == null) return@LaunchedEffect
        delay(900)
        seekIndicator = null
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
                    // ExoPlayer'in kendi ileri/geri sarma dugmeleri kumandayla tetiklenemiyordu
                    // (View odak sorunu) ve kendi varsayilan atlama suresini (5sn/10sn) gosterip
                    // aslinda bizim 10sn'lik dogrudan sol/sag isleyicimizle celisiyordu -- ekranda
                    // gorunen ama isleve yaramayan bu dugmeler "boşa gidiyor" hissi yaratiyordu.
                    // Kaldirip tek, tutarli bir sol/sag + SeekIndicator akisi birakiyoruz.
                    setShowFastForwardButton(false)
                    setShowRewindButton(false)
                    setShowPreviousButton(false)
                    setShowNextButton(false)
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
            if (!request.item.isLive && request.item.hasNext) OutlinedButton(
                onClick = onNext,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text("⏭ " + stringResource(R.string.next_episode), maxLines = 1, style = MaterialTheme.typography.labelSmall)
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
            modifier = Modifier.align(Alignment.CenterEnd),
            rows = panelRows,
            focusIndex = panelFocusIndex,
            selectedAudioGroup = selectedAudioGroup,
            selectedSubtitleGroup = selectedSubtitleGroup,
            prefs = subtitlePrefs,
            onSelectAudio = { selectAudio(it) },
            onSelectSubtitle = { selectSubtitle(it) },
            onPrefsChange = { updateSubtitleStyle(it) },
            onClose = { tracksPanelVisible = false },
            onlineBusy = onlineSubtitleBusy,
            onlineError = onlineSubtitleError,
            onSearchOnline = { searchOnlineSubtitles() },
            onApplyOnline = { applyOnlineSubtitle(it) },
        )

        SeekIndicator(state = seekIndicator, modifier = Modifier.align(Alignment.Center))

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

// Panel eskiden iki ayri "sutun" (ses/altyazi) + kendi basina, kumandayla hic
// erisilemeyen "Gorunum" ve "Internetten altyazi bul" bolumlerinden olusuyordu --
// kullanicinin "internetten altyazi bul kismina gelmiyor" sikayetinin sebebi buydu.
// Artik TUM satirlar (ses, altyazi, arama, sonuclar, boyut/renk/arka plan) TEK bir
// dikey liste; yukari/asagi bu listede gezinir, sol/sag yalnizca "ayarlanabilir"
// satirlarda (Boyut/Renk/Arka plan) degeri degistirir, geri kalaninda hicbir sey yapmaz.
internal sealed class PanelRow {
    data class AudioRow(val option: AudioOption) : PanelRow()
    object SubtitleOffRow : PanelRow()
    data class SubtitleRow(val option: SubtitleOption) : PanelRow()
    object SearchOnlineRow : PanelRow()
    data class OnlineResultRow(val result: OnlineSubtitleResult) : PanelRow()
    object SizeRow : PanelRow()
    object ColorRow : PanelRow()
    object BackgroundRow : PanelRow()
}

private val panelBackgroundOptions = listOf("shadow" to "Gölge", "box" to "Koyu kutu", "none" to "Yok")

// "Boyut/Renk/Arka plan" satirlarinda sol/sag basildiginda bir sonraki/onceki degere gecer;
// diger tum satir turlerinde (listeler) hicbir sey yapmaz (null doner).
internal fun adjustPanelRow(row: PanelRow?, prefs: PlaybackPreferences, direction: Int): PlaybackPreferences? = when (row) {
    is PanelRow.SizeRow -> prefs.copy(subtitleSize = (prefs.subtitleSize + direction * 10).coerceIn(70, 180))
    is PanelRow.ColorRow -> {
        val index = subtitleColorPresets.indexOfFirst { it.first.equals(prefs.subtitleColor, ignoreCase = true) }.let { if (it < 0) 0 else it }
        val next = (index + direction).mod(subtitleColorPresets.size)
        prefs.copy(subtitleColor = subtitleColorPresets[next].first)
    }
    is PanelRow.BackgroundRow -> {
        val index = panelBackgroundOptions.indexOfFirst { it.first == prefs.subtitleBackground }.let { if (it < 0) 0 else it }
        val next = (index + direction).mod(panelBackgroundOptions.size)
        prefs.copy(subtitleBackground = panelBackgroundOptions[next].first)
    }
    else -> null
}

internal fun buildPanelRows(
    audioOptions: List<AudioOption>,
    subtitleOptions: List<SubtitleOption>,
    onlineResults: List<OnlineSubtitleResult>,
): List<PanelRow> = buildList {
    audioOptions.forEach { add(PanelRow.AudioRow(it)) }
    add(PanelRow.SubtitleOffRow)
    subtitleOptions.forEach { add(PanelRow.SubtitleRow(it)) }
    add(PanelRow.SearchOnlineRow)
    onlineResults.forEach { add(PanelRow.OnlineResultRow(it)) }
    add(PanelRow.SizeRow)
    add(PanelRow.ColorRow)
    add(PanelRow.BackgroundRow)
}

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
    rows: List<PanelRow>,
    focusIndex: Int,
    selectedAudioGroup: Tracks.Group?,
    selectedSubtitleGroup: Tracks.Group?,
    prefs: PlaybackPreferences,
    onSelectAudio: (AudioOption) -> Unit,
    onSelectSubtitle: (SubtitleOption?) -> Unit,
    onPrefsChange: (PlaybackPreferences) -> Unit,
    onClose: () -> Unit,
    onlineBusy: Boolean = false,
    onlineError: String? = null,
    onSearchOnline: () -> Unit = {},
    onApplyOnline: (OnlineSubtitleResult) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // Kumandayla asagi/yukari giderken vurgulanan satir listenin gorunur alaninin disina
    // cikabiliyordu ("cok altyazi varsa sayfanin altina gidiyor ve gozukmuyor" sikayeti) --
    // odak index'i degistikce o satiri otomatik gorunur alana kaydiriyoruz.
    LaunchedEffect(focusIndex, rows.size) {
        if (focusIndex in rows.indices) listState.animateScrollToItem(focusIndex)
    }
    // Panel eskiden ekranin sabit bir yuksekligini (520dp) kullaniyordu -- kucuk TV
    // ekranlarinda bu, panelin alt kismini gorunur alanin disina tasiriyordu. fillMaxHeight
    // bir oran (%88) kullanarak HER ekranda sigmasini garanti ediyoruz; ayrica istenen
    // seffafligi geri getirmek icin arka plan alfasini dusurduk.
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxHeight(0.88f)
            .fillMaxWidth(0.42f)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = ComposeColor(0xB3120E1C),
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
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
                    Text("↑↓ gezin · Enter seç · ←→ ayarla", color = PanelMuted, style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ComposeColor(0x1AFFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = ComposeColor.White, style = MaterialTheme.typography.labelMedium) }
            }

            Spacer(Modifier.height(10.dp))

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

            Spacer(Modifier.height(10.dp))

            // Eskiden ses/altyazi iki ayri sutunda, "Gorunum" ve "Internetten altyazi bul"
            // ise kumandayla hic erisilemeyen ayri kartlardaydi. Artik HEPSI tek dikey
            // listede (rows) -- boylece her satir kumandayla ulasilabilir oluyor.
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
                    val highlighted = index == focusIndex
                    when (row) {
                        is PanelRow.AudioRow -> TrackRow(
                            label = "🔊 ${row.option.label}",
                            active = row.option.group == selectedAudioGroup,
                            highlighted = highlighted,
                        ) { onSelectAudio(row.option) }
                        is PanelRow.SubtitleOffRow -> TrackRow(
                            label = "💬 Kapalı",
                            active = selectedSubtitleGroup == null,
                            highlighted = highlighted,
                        ) { onSelectSubtitle(null) }
                        is PanelRow.SubtitleRow -> TrackRow(
                            label = "💬 ${row.option.label}",
                            active = row.option.group == selectedSubtitleGroup,
                            highlighted = highlighted,
                        ) { onSelectSubtitle(row.option) }
                        is PanelRow.SearchOnlineRow -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .let { if (highlighted) it.border(2.dp, PanelAccent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) else it }
                                .background(if (onlineBusy) ComposeColor(0x14FFFFFF) else PanelAccentDim)
                                .clickable(enabled = !onlineBusy) { onSearchOnline() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("🌐", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "İnternetten altyazı bul",
                                color = ComposeColor.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            if (onlineBusy) androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = ComposeColor.White,
                            ) else Text("Ara", color = ComposeColor.White, style = MaterialTheme.typography.labelSmall)
                        }
                        is PanelRow.OnlineResultRow -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .let { if (highlighted) it.border(2.dp, PanelAccent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) else it }
                                .background(ComposeColor(0x1F000000))
                                .clickable { onApplyOnline(row.result) }
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
                                Text(row.result.language.uppercase(), color = PanelAccent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                row.result.release,
                                color = ComposeColor(0xFFCFC9DC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text("↓", color = PanelMuted, style = MaterialTheme.typography.labelMedium)
                        }
                        is PanelRow.SizeRow -> AdjustRow(
                            icon = "🔤",
                            label = "Boyut",
                            value = "%${prefs.subtitleSize}",
                            highlighted = highlighted,
                        ) { direction -> adjustPanelRow(row, prefs, direction)?.let(onPrefsChange) }
                        is PanelRow.ColorRow -> AdjustRow(
                            icon = "🎨",
                            label = "Renk",
                            value = subtitleColorPresets.firstOrNull { it.first.equals(prefs.subtitleColor, ignoreCase = true) }?.second ?: "-",
                            highlighted = highlighted,
                            swatch = runCatching { ComposeColor(android.graphics.Color.parseColor(prefs.subtitleColor)) }.getOrNull(),
                        ) { direction -> adjustPanelRow(row, prefs, direction)?.let(onPrefsChange) }
                        is PanelRow.BackgroundRow -> AdjustRow(
                            icon = "🖼️",
                            label = "Arka plan",
                            value = panelBackgroundOptions.firstOrNull { it.first == prefs.subtitleBackground }?.second ?: "-",
                            highlighted = highlighted,
                        ) { direction -> adjustPanelRow(row, prefs, direction)?.let(onPrefsChange) }
                    }
                }
                if (onlineError != null) item {
                    Text(onlineError, color = ComposeColor(0xFFFF8A8A), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// Boyut/Renk/Arka plan gibi "ayarlanabilir" satirlar -- kumandada sol/sag deger degistirir,
// dokunmatikte satira tiklamak bir sonraki degere gecer (tek yonlu ama en azindan
// erisilebilir; TV kumandasi zaten sol/sagi kullaniyor).
@Composable
private fun AdjustRow(icon: String, label: String, value: String, highlighted: Boolean, swatch: ComposeColor? = null, onAdjust: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .let { if (highlighted) it.border(2.dp, PanelAccent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) else it }
            .background(ComposeColor(0x14FFFFFF))
            .clickable { onAdjust(1) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.labelMedium)
        Text(label, color = ComposeColor(0xFFB8B4C8), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        if (swatch != null) Box(
            modifier = Modifier.size(14.dp).clip(CircleShape).background(swatch).border(1.dp, ComposeColor(0x33FFFFFF), CircleShape),
        )
        Text("‹", color = PanelMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onAdjust(-1) })
        Text(value, color = ComposeColor.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        Text("›", color = PanelMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onAdjust(1) })
    }
}

// Sol/sag kumanda tuslariyla +10sn/-10sn atlanirken hicbir gorsel geri bildirim yoktu
// ("bosa gidiyor" sikayeti) -- kisa sureli, soluk-buyuk animasyonlu bir rozet gosteriyoruz.
@Composable
private fun SeekIndicator(state: Pair<Boolean, Long>?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state != null,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        val forward = state?.first ?: true
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(ComposeColor(0xB3000000))
                .padding(horizontal = 22.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (forward) "⏩ +10sn" else "⏪ -10sn",
                color = ComposeColor.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
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
