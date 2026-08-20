package com.streamlivex.android.tv.live

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackPreferences
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.NativeLiveCategory
import com.streamlivex.android.tv.data.NativeLiveChannel
import com.streamlivex.android.tv.data.TvLiveLibraryCache
import com.streamlivex.android.tv.data.TvLiveProfileStore
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.data.XtreamLiveLibrary
import com.streamlivex.android.tv.profile.TvActiveScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
    menuFocusRequester: FocusRequester,
) {
    val client = remember { XtreamClient() }
    val context = LocalContext.current
    val liveStore =
        remember(
            TvActiveScope.storageKey(),
        ) {
            TvLiveProfileStore(
                context,
            )
        }

    val sessionId = remember(provider.server, provider.username) {
        "tv-live-${provider.server.hashCode()}-${provider.username.hashCode()}"
    }

    var library by remember(provider.server, provider.username) {
        mutableStateOf<XtreamLiveLibrary?>(TvLiveLibraryCache.library)
    }

    var loading by remember(provider.server, provider.username) {
        mutableStateOf(library == null)
    }

    var error by remember { mutableStateOf("") }
    var selectedCategoryId by remember(
        TvActiveScope.storageKey(),
    ) {
        mutableStateOf(
            liveStore.selectedCategoryId(),
        )
    }
    var selectedChannelId by remember(
        TvActiveScope.storageKey(),
    ) {
        mutableStateOf(
            liveStore.focusedChannelId()
                ?: liveStore.lastChannelId(),
        )
    }
    var playbackChannelId by remember(
        TvActiveScope.storageKey(),
    ) {
        mutableStateOf(
            liveStore.playbackChannelId(),
        )
    }
    var favoriteChannelIds by remember(
        TvActiveScope.storageKey(),
    ) {
        mutableStateOf(
            liveStore.favoriteChannelIds(),
        )
    }
    var fullscreen by remember { mutableStateOf(false) }
    var channelOverlayVisible by remember { mutableStateOf(false) }

    val categoryReturnFocusRequester = remember { FocusRequester() }
    val channelReturnFocusRequester = remember { FocusRequester() }

    LaunchedEffect(
        selectedCategoryId,
        selectedChannelId,
        playbackChannelId,
    ) {
        liveStore.saveUiState(
            selectedCategoryId =
                selectedCategoryId,
            focusedChannelId =
                selectedChannelId,
            playbackChannelId =
                playbackChannelId,
        )
    }

    LaunchedEffect(
        playbackChannelId,
    ) {
        playbackChannelId
            ?.let {
                liveStore.setLastChannel(
                    it,
                )
            }
    }

    fun requestFor(channel: NativeLiveChannel): PlaybackRequest {
        return PlaybackRequest(
            sessionId = sessionId,
            item = PlaybackItem(
                name = channel.name,
                url = channel.streamUrl,
                kind = "live",
            ),
            preferences = PlaybackPreferences(showInfo = false),
        )
    }

    DisposableEffect(sessionId) {
        onDispose {
            onFullscreenStateChanged(false)
            releasePlayer(sessionId)
        }
    }

    LaunchedEffect(fullscreen) {
        onFullscreenStateChanged(fullscreen)
    }

    LaunchedEffect(
        provider.server,
        provider.username,
        provider.password,
    ) {
        if (library != null) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        error = ""

        Thread {
            val result = client.loadLiveLibrary(provider)

            Handler(Looper.getMainLooper()).post {
                result
                    .onSuccess { loaded ->
                        library = loaded
                        loading = false
                        error = ""
                    }
                    .onFailure { throwable ->
                        loading = false
                        error = throwable.message ?: "Canlı TV listesi yüklenemedi."
                    }
            }
        }.start()
    }

    if (loading) {
        LiveLoadingScreen()
        return
    }

    if (error.isNotBlank()) {
        LiveErrorScreen(message = error)
        return
    }

    val currentLibrary = library
    if (currentLibrary == null) {
        LiveErrorScreen(message = "Canlı TV verisi bulunamadı.")
        return
    }

    if (currentLibrary.categories.isEmpty()) {
        LiveErrorScreen(message = "Canlı TV kategorisi bulunamadı.")
        return
    }

    val selectedCategory =
        currentLibrary.categories.firstOrNull { it.id == selectedCategoryId }
            ?: currentLibrary.categories.first()

    val visibleChannels = remember(currentLibrary.channels, selectedCategory.id) {
        if (selectedCategory.id == "all") {
            currentLibrary.channels
        } else {
            currentLibrary.channels.filter { it.categoryId == selectedCategory.id }
        }
    }

    val selectedChannel =
        visibleChannels.firstOrNull { it.id == selectedChannelId }
            ?: visibleChannels.firstOrNull()

    val playbackChannel =
        currentLibrary.channels.firstOrNull { it.id == playbackChannelId }

    var livePlayer by remember(sessionId) {
        mutableStateOf<ExoPlayer?>(null)
    }

    fun moveChannel(direction: Int) {
        if (visibleChannels.isEmpty()) return

        val baseChannelId =
            playbackChannelId ?: selectedChannelId ?: visibleChannels.firstOrNull()?.id

        val currentIndex = visibleChannels.indexOfFirst { it.id == baseChannelId }
            .let { if (it >= 0) it else 0 }

        val nextIndex =
            (currentIndex + direction + visibleChannels.size) % visibleChannels.size

        val nextChannel = visibleChannels[nextIndex]
        selectedChannelId = nextChannel.id
        playbackChannelId = nextChannel.id
    }

    // Focus yalnızca seçim vurgusunu değiştirir; yayın BAŞLATMAZ.
    // İlk OK: küçük preview. Aynı kanalda ikinci OK: fullscreen.
    LaunchedEffect(playbackChannel?.id) {
        val channel = playbackChannel ?: return@LaunchedEffect

        val player = livePlayer
        if (player == null) {
            livePlayer = playerFor(requestFor(channel))
        } else {
            player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
            player.prepare()
            player.playWhenReady = true
        }
    }

    // Live yayın asla pause durumda kalmasın.
    DisposableEffect(livePlayer, fullscreen) {
        val player = livePlayer
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady) {
                        player.playWhenReady = true
                    }
                }
            }

            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
            }
        }
    }

    // Fullscreen'den çıkınca aynı kanal satırına odak dönsün.
    LaunchedEffect(fullscreen) {
        if (!fullscreen && selectedChannelId != null) {
            delay(80)
            runCatching { channelReturnFocusRequester.requestFocus() }
        }
    }

    // Fullscreen zapping: yukarı/aşağı kanal değiştirir.
    LaunchedEffect(externalPlayerKeyEvent, fullscreen) {
        if (!fullscreen) return@LaunchedEffect

        val event = externalPlayerKeyEvent ?: return@LaunchedEffect
        val (keyCode, action, _) = event

        if (action != KeyEvent.ACTION_DOWN) return@LaunchedEffect

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveChannel(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveChannel(1)

            // Canlı TV'de ileri/geri ve merkez tuş player kontrolü yapmaz.
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> Unit
        }
    }

    // Kanal değişiminde gerçek TV/TiviMate tarzı alt bilgi bandı.
    LaunchedEffect(fullscreen, selectedChannel?.id) {
        if (!fullscreen || selectedChannel == null) {
            channelOverlayVisible = false
            return@LaunchedEffect
        }

        channelOverlayVisible = true
        delay(2600)
        channelOverlayVisible = false
    }

    BackHandler(enabled = fullscreen) {
        fullscreen = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!fullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D111B)),
            ) {
                CategoryColumn(
                    categories = currentLibrary.categories,
                    selectedCategory = selectedCategory,
                    selectedCategoryFocusRequester = categoryReturnFocusRequester,
                    menuFocusRequester = menuFocusRequester,
                    onSelected = { category ->
                        onContentFocused()
                        selectedCategoryId = category.id
                    },
                    modifier = Modifier.weight(0.30f),
                )

                ChannelColumn(
                    channels = visibleChannels,
                    selectedChannel = selectedChannel,
                    favoriteChannelIds =
                        favoriteChannelIds,
                    onFocused = { channel ->
                        onContentFocused()
                        selectedChannelId = channel.id
                    },
                    categoryFocusRequester = categoryReturnFocusRequester,
                    selectedChannelFocusRequester = channelReturnFocusRequester,
                    onActivate = { channel ->
                        onContentFocused()
                        selectedChannelId = channel.id
                        if (playbackChannelId == channel.id) {
                            fullscreen = true
                        } else {
                            playbackChannelId = channel.id
                        }
                    },
                    modifier = Modifier.weight(0.42f),
                )

                ChannelPreviewPanel(
                    channel = playbackChannel,
                    player = livePlayer,
                    isFavorite =
                        playbackChannel
                            ?.id
                            ?.let {
                                favoriteChannelIds
                                    .contains(
                                        it,
                                    )
                            }
                            ?: false,
                    onToggleFavorite = {
                        channel ->

                        liveStore.toggleFavorite(
                            channel.id,
                        )
                        favoriteChannelIds =
                            liveStore
                                .favoriteChannelIds()
                    },
                    modifier = Modifier.weight(0.58f),
                )
            }
        } else {
            val player = livePlayer
            if (player != null) {
                LiveVideoSurface(
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (channelOverlayVisible && selectedChannel != null) {
                ChannelOverlay(
                    channel = selectedChannel,
                    channelNumber = visibleChannels.indexOfFirst {
                        it.id == selectedChannel.id
                    }.let { if (it >= 0) it + 1 else 0 },
                    categoryName = selectedCategory.name,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = 34.dp,
                            end = 34.dp,
                            bottom = 30.dp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LiveLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Canlı TV yükleniyor...",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Kategoriler ve kanallar hazırlanıyor",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LiveErrorScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B))
            .padding(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Canlı TV yüklenemedi",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = message,
                color = Color(0xFFF87171),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun CategoryColumn(
    categories: List<NativeLiveCategory>,
    selectedCategory: NativeLiveCategory,
    selectedCategoryFocusRequester: FocusRequester,
    menuFocusRequester: FocusRequester,
    onSelected: (NativeLiveCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF101722))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Kategoriler",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = categories.size.toString(),
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                items = categories,
                key = { _, category -> category.id },
            ) { _, category ->
                var focused by remember { mutableStateOf(false) }

                val background = when {
                    focused -> Color(0xFF2563EB)
                    category.id == selectedCategory.id -> Color(0xFF172554)
                    else -> Color(0xFF151C28)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(background, RoundedCornerShape(8.dp))
                        .then(
                            if (category.id == selectedCategory.id) {
                                Modifier.focusRequester(selectedCategoryFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .focusProperties {
                            left = menuFocusRequester
                        }
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) {
                                onSelected(category)
                            }
                        }
                        .focusable()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = category.name,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = category.count.toString(),
                        color = if (focused) Color.White else Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelColumn(
    channels: List<NativeLiveChannel>,
    selectedChannel: NativeLiveChannel?,
    favoriteChannelIds: Set<String>,
    categoryFocusRequester: FocusRequester,
    selectedChannelFocusRequester: FocusRequester,
    onFocused: (NativeLiveChannel) -> Unit,
    onActivate: (NativeLiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0F141E))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Kanallar",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = channels.size.toString(),
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Bu kategoride kanal yok",
                    color = Color(0xFF94A3B8),
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                items = channels,
                key = { _, channel -> channel.id },
            ) { index, channel ->
                var focused by remember { mutableStateOf(false) }

                val background = when {
                    focused -> Color(0xFF1D4ED8)
                    channel.id == selectedChannel?.id -> Color(0xFF172554)
                    else -> Color(0xFF141B26)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(background, RoundedCornerShape(8.dp))
                        .then(
                            if (channel.id == selectedChannel?.id) {
                                Modifier.focusRequester(selectedChannelFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .focusProperties {
                            left = categoryFocusRequester
                        }
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) {
                                onFocused(channel)
                            }
                        }
                        // Kumanda OK / Enter sadece burada fullscreen açar.
                        // Focus tek başına fullscreen açmaz; yalnızca küçük preview'i değiştirir.
                        .clickable {
                            onActivate(channel)
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Color(0xFF64748B),
                        modifier = Modifier.width(38.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                if (
                                    favoriteChannelIds
                                        .contains(
                                            channel.id,
                                        )
                                ) {
                                    "★ ${channel.name}"
                                } else {
                                    channel.name
                                },
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (channel.epgId.isNullOrBlank()) {
                                "Canlı yayın"
                            } else {
                                "EPG mevcut"
                            },
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ChannelPreviewPanel(
    channel: NativeLiveChannel?,
    player: ExoPlayer?,
    isFavorite: Boolean,
    onToggleFavorite: (NativeLiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0B1018))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
                .background(
                    color = Color.Black,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            if (player != null) {
                LiveVideoSurface(
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Kanalı açmak için OK",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Text(
                text = "CANLI",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(
                        color = Color(0xFFD91E36),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .background(
                    color = Color(0xFF111827),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = channel?.name ?: "Kanal seç",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (channel == null) {
                    "Kanal üzerinde OK ile ön izleme açılır"
                } else {
                    "Aynı kanalda tekrar OK ile tam ekran"
                },
                color = Color(0xFF60A5FA),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (channel?.epgId.isNullOrBlank()) {
                    "Program bilgisi bulunamadı"
                } else {
                    "EPG mevcut"
                },
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
            )

            if (channel != null) {
                var favoriteFocused by
                    remember(
                        channel.id,
                    ) {
                        mutableStateOf(
                            false,
                        )
                    }

                Box(
                    modifier =
                        Modifier
                            .background(
                                if (
                                    favoriteFocused
                                ) {
                                    Color(
                                        0xFF2563EB,
                                    )
                                } else {
                                    Color(
                                        0xFF1E293B,
                                    )
                                },
                                RoundedCornerShape(
                                    8.dp,
                                ),
                            )
                            .onFocusChanged {
                                favoriteFocused =
                                    it.isFocused
                            }
                            .clickable {
                                onToggleFavorite(
                                    channel,
                                )
                            }
                            .padding(
                                horizontal =
                                    12.dp,
                                vertical =
                                    8.dp,
                            ),
                ) {
                    Text(
                        text =
                            if (
                                isFavorite
                            ) {
                                "★ Favorilerden Çıkar"
                            } else {
                                "☆ Favoriye Ekle"
                            },
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveVideoSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = false
                controllerAutoShow = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
            view.useController = false
            view.controllerAutoShow = false
            view.keepScreenOn = true
        },
    )
}

@Composable
private fun ChannelOverlay(
    channel: NativeLiveChannel,
    channelNumber: Int,
    categoryName: String,
    modifier: Modifier = Modifier,
) {
    val now = remember(channel.id) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    Row(
        modifier = modifier
            .background(
                color = Color(0xE6151A23),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = if (channelNumber > 0) {
                channelNumber.toString().padStart(3, '0')
            } else {
                "TV"
            },
            color = Color(0xFF60A5FA),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (channel.epgId.isNullOrBlank()) {
                    "$categoryName • Canlı yayın"
                } else {
                    "$categoryName • EPG mevcut"
                },
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "CANLI",
                color = Color(0xFFF87171),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = now,
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
