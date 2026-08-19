package com.streamlivex.android.tv.live

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.streamlivex.android.NativeInlinePlayerSurface
import com.streamlivex.android.NativePlayerSurface
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackPreferences
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.NativeLiveCategory
import com.streamlivex.android.tv.data.NativeLiveChannel
import com.streamlivex.android.tv.data.TvLiveLibraryCache
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.data.XtreamLiveLibrary
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
) {
    val client = remember {
        XtreamClient()
    }

    /*
     * Canli TV boyunca ayni sessionId kullaniliyor.
     *
     * Boylece preview -> fullscreen gecisinde
     * ayni ExoPlayer instance korunuyor.
     *
     * Kanal degistiginde URL degisiyor ama
     * player instance degismiyor.
     */
    val sessionId =
        remember(
            provider.server,
            provider.username,
        ) {
            "tv-live-" +
                provider.server.hashCode() +
                "-" +
                provider.username.hashCode()
        }

    var library by remember(
        provider.server,
        provider.username,
    ) {
        mutableStateOf<XtreamLiveLibrary?>(
            TvLiveLibraryCache.library,
        )
    }

    var loading by remember(
        provider.server,
        provider.username,
    ) {
        mutableStateOf(
            library == null,
        )
    }

    var error by remember {
        mutableStateOf("")
    }

    var selectedCategoryId by remember {
        mutableStateOf("all")
    }

    var selectedChannelId by remember {
        mutableStateOf<String?>(null)
    }

    /*
     * Sag tarafta gercekten oynatilan
     * preview request.
     *
     * Focus degisir degismez degil,
     * 500 ms ayni kanalda kalinca set edilir.
     */
    var previewRequest by remember {
        mutableStateOf<PlaybackRequest?>(null)
    }

    var fullscreen by remember {
        mutableStateOf(false)
    }

    fun requestFor(
        channel: NativeLiveChannel,
    ): PlaybackRequest {
        return PlaybackRequest(
            sessionId = sessionId,
            item = PlaybackItem(
                name = channel.name,
                url = channel.streamUrl,
                kind = "live",
            ),
            preferences = PlaybackPreferences(
                showInfo = true,
            ),
        )
    }

    /*
     * Canli TV ekranindan tamamen cikinca
     * player serbest birakilir.
     *
     * Preview -> fullscreen gecisinde
     * release YOK.
     */
    DisposableEffect(sessionId) {
        onDispose {
            onFullscreenStateChanged(false)
            releasePlayer(sessionId)
        }
    }

    LaunchedEffect(fullscreen) {
        onFullscreenStateChanged(
            fullscreen,
        )
    }

    /*
     * Gercek Xtream kanal/kategori kutuphanesi.
     */
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
            val result =
                client.loadLiveLibrary(
                    provider,
                )

            Handler(
                Looper.getMainLooper(),
            ).post {
                result
                    .onSuccess { loaded ->
                        library = loaded
                        loading = false
                        error = ""
                    }
                    .onFailure { throwable ->
                        loading = false
                        error =
                            throwable.message
                                ?: "Canlı TV listesi yüklenemedi."
                    }
            }
        }.start()
    }

    if (loading) {
        LiveLoadingScreen()
        return
    }

    if (error.isNotBlank()) {
        LiveErrorScreen(
            message = error,
        )
        return
    }

    val currentLibrary =
        library

    if (currentLibrary == null) {
        LiveErrorScreen(
            message = "Canlı TV verisi bulunamadı.",
        )
        return
    }

    if (currentLibrary.categories.isEmpty()) {
        LiveErrorScreen(
            message = "Canlı TV kategorisi bulunamadı.",
        )
        return
    }

    val selectedCategory =
        currentLibrary.categories
            .firstOrNull {
                it.id == selectedCategoryId
            }
            ?: currentLibrary.categories.first()

    /*
     * Sadece secilen kategorinin kanallari.
     *
     * 6325 kanalin tamamini Compose
     * elemani olarak yaratmiyoruz.
     */
    val visibleChannels =
        remember(
            currentLibrary.channels,
            selectedCategory.id,
        ) {
            if (selectedCategory.id == "all") {
                currentLibrary.channels
            } else {
                currentLibrary.channels.filter {
                    it.categoryId ==
                        selectedCategory.id
                }
            }
        }

    val selectedChannel =
        visibleChannels.firstOrNull {
            it.id == selectedChannelId
        }
            ?: visibleChannels.firstOrNull()

    /*
     * KANAL PREVIEW GECIKMESI
     *
     * Kullanici kumandada hizli hizli
     * kanallar arasinda gezerken her focus
     * degisiminde stream baslatmiyoruz.
     *
     * 500 ms ayni kanalda durursa yayin acilir.
     */
    LaunchedEffect(
        selectedChannel?.id,
        fullscreen,
    ) {
        if (
            fullscreen ||
            selectedChannel == null
        ) {
            return@LaunchedEffect
        }

        delay(500)

        previewRequest =
            requestFor(
                selectedChannel,
            )
    }

    /*
     * Tam ekranda onceki/sonraki kanal.
     */
    fun moveChannel(
        direction: Int,
    ) {
        if (visibleChannels.isEmpty()) {
            return
        }

        val currentIndex =
            visibleChannels
                .indexOfFirst {
                    it.id ==
                        selectedChannel?.id
                }
                .let {
                    if (it >= 0) {
                        it
                    } else {
                        0
                    }
                }

        /*
         * Listenin sonunda asagi basilirsa
         * basa doner.
         *
         * Ilk kanalda yukari basilirsa
         * son kanala gider.
         */
        val nextIndex =
            (
                currentIndex +
                    direction +
                    visibleChannels.size
                ) %
                visibleChannels.size

        val nextChannel =
            visibleChannels[
                nextIndex
            ]

        selectedChannelId =
            nextChannel.id

        if (fullscreen) {
            /*
             * Tam ekranda kanal degisimi
             * aninda baslasin.
             * Preview'daki 500 ms gecikme yok.
             */
            previewRequest =
                requestFor(
                    nextChannel,
                )
        }
    }

    /*
     * MainActivity.dispatchKeyEvent
     * fullscreen TV tuslarini buraya yollar.
     *
     * ↑ onceki kanal
     * ↓ sonraki kanal
     */
    LaunchedEffect(
        externalPlayerKeyEvent,
        fullscreen,
    ) {
        if (!fullscreen) {
            return@LaunchedEffect
        }

        val event =
            externalPlayerKeyEvent
                ?: return@LaunchedEffect

        val (
            keyCode,
            action,
            _,
        ) = event

        if (
            action !=
            KeyEvent.ACTION_DOWN
        ) {
            return@LaunchedEffect
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveChannel(-1)
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveChannel(1)
            }
        }
    }

    /*
     * BACK:
     * Uygulamadan cikma.
     * Canli TV'den cikma.
     *
     * Sadece fullscreen'i kapatip
     * ayni kanal listesine geri don.
     */
    BackHandler(
        enabled = fullscreen,
    ) {
        fullscreen = false
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!fullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFF0D111B),
                    ),
            ) {
                CategoryColumn(
                    categories =
                        currentLibrary.categories,
                    selectedCategory =
                        selectedCategory,
                    onSelected = { category ->
                        onContentFocused()

                        selectedCategoryId =
                            category.id

                        selectedChannelId =
                            null

                        previewRequest =
                            null
                    },
                    modifier =
                        Modifier.weight(
                            0.31f,
                        ),
                )

                ChannelColumn(
                    channels =
                        visibleChannels,
                    selectedChannel =
                        selectedChannel,
                    onFocused = { channel ->
                        onContentFocused()

                        selectedChannelId =
                            channel.id
                    },
                    onActivate = { channel ->
                        onContentFocused()

                        selectedChannelId =
                            channel.id

                        previewRequest =
                            requestFor(channel)

                        fullscreen =
                            true
                    },
                    modifier =
                        Modifier.weight(
                            0.42f,
                        ),
                )

                PreviewPanel(
                    channel =
                        selectedChannel,
                    request =
                        previewRequest,
                    playerFor =
                        playerFor,
                    onFullscreen = {
                        val channel =
                            selectedChannel

                        if (channel != null) {
                            previewRequest =
                                requestFor(channel)

                            fullscreen = true
                        }
                    },
                    modifier =
                        Modifier.weight(
                            0.55f,
                        ),
                )
            }
        }

        /*
         * TAM EKRAN
         */
        if (fullscreen) {
            val channel =
                selectedChannel

            if (channel != null) {
                val request =
                    requestFor(
                        channel,
                    )

                NativePlayerSurface(
                    request = request,
                    player =
                        playerFor(
                            request,
                        ),
                    externalKeyEvent =
                        externalPlayerKeyEvent,
                    onClose = {
                        fullscreen = false
                    },
                    onProgress = {},
                    onFailure = {},
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
            .background(
                Color(0xFF0D111B),
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp,
                ),
        ) {
            CircularProgressIndicator()

            Text(
                text =
                    "Canlı TV yükleniyor...",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.SemiBold,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Text(
                text =
                    "Kategoriler ve kanallar hazırlanıyor",
                color =
                    Color(0xFF94A3B8),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )
        }
    }
}

@Composable
private fun LiveErrorScreen(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFF0D111B),
            )
            .padding(
                36.dp,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp,
                ),
        ) {
            Text(
                text =
                    "Canlı TV yüklenemedi",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
            )

            Text(
                text = message,
                color =
                    Color(0xFFF87171),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )
        }
    }
}

@Composable
private fun CategoryColumn(
    categories:
        List<NativeLiveCategory>,
    selectedCategory:
        NativeLiveCategory,
    onSelected:
        (NativeLiveCategory) -> Unit,
    modifier:
        Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                Color(0xFF101722),
            )
            .padding(
                12.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 4.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text =
                    "Kategoriler",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.weight(1f),
            )

            Text(
                text =
                    categories.size
                        .toString(),
                color =
                    Color(0xFF64748B),
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
            )
        }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(
                    6.dp,
                ),
        ) {
            itemsIndexed(
                items = categories,
                key = { _, category ->
                    category.id
                },
            ) { _, category ->

                var focused by remember {
                    mutableStateOf(false)
                }

                val background =
                    when {
                        focused -> {
                            Color(
                                0xFF2563EB,
                            )
                        }

                        category.id ==
                            selectedCategory.id -> {
                            Color(
                                0xFF172554,
                            )
                        }

                        else -> {
                            Color(
                                0xFF151C28,
                            )
                        }
                    }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color =
                                background,
                            shape =
                                RoundedCornerShape(
                                    8.dp,
                                ),
                        )
                        .onFocusChanged {
                            focused =
                                it.isFocused

                            if (
                                it.isFocused
                            ) {
                                onSelected(
                                    category,
                                )
                            }
                        }
                        .focusable()
                        .padding(
                            horizontal =
                                10.dp,
                            vertical =
                                10.dp,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            category.name,
                        color =
                            Color.White,
                        modifier =
                            Modifier.weight(1f),
                        maxLines = 1,
                        overflow =
                            TextOverflow
                                .Ellipsis,
                    )

                    Text(
                        text =
                            category.count
                                .toString(),
                        color =
                            if (focused) {
                                Color.White
                            } else {
                                Color(
                                    0xFF94A3B8,
                                )
                            },
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelColumn(
    channels:
        List<NativeLiveChannel>,
    selectedChannel:
        NativeLiveChannel?,
    onFocused:
        (NativeLiveChannel) -> Unit,
    onActivate:
        (NativeLiveChannel) -> Unit,
    modifier:
        Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                Color(0xFF0F141E),
            )
            .padding(
                12.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 4.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text =
                    "Kanallar",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.weight(1f),
            )

            Text(
                text =
                    channels.size
                        .toString(),
                color =
                    Color(0xFF64748B),
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
            )
        }

        if (channels.isEmpty()) {
            Box(
                modifier =
                    Modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center,
            ) {
                Text(
                    text =
                        "Bu kategoride kanal yok",
                    color =
                        Color(0xFF94A3B8),
                )
            }

            return
        }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(
                    6.dp,
                ),
        ) {
            itemsIndexed(
                items = channels,
                key = { _, channel ->
                    channel.id
                },
            ) { index, channel ->

                var focused by remember {
                    mutableStateOf(false)
                }

                val background =
                    when {
                        focused -> {
                            Color(
                                0xFF1D4ED8,
                            )
                        }

                        channel.id ==
                            selectedChannel?.id -> {
                            Color(
                                0xFF172554,
                            )
                        }

                        else -> {
                            Color(
                                0xFF141B26,
                            )
                        }
                    }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color =
                                background,
                            shape =
                                RoundedCornerShape(
                                    8.dp,
                                ),
                        )
                        .onFocusChanged {
                            focused =
                                it.isFocused

                            if (
                                it.isFocused
                            ) {
                                onFocused(
                                    channel,
                                )
                            }
                        }
                        .onKeyEvent { event ->
                            if (
                                event.type ==
                                KeyEventType.KeyDown &&
                                (
                                    event.key ==
                                        Key.Enter ||
                                    event.key ==
                                        Key.DirectionCenter
                                    )
                            ) {
                                onActivate(
                                    channel,
                                )

                                true
                            } else {
                                false
                            }
                        }
                        .focusable()
                        .padding(
                            horizontal =
                                10.dp,
                            vertical =
                                10.dp,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            "${index + 1}",
                        color =
                            Color(
                                0xFF64748B,
                            ),
                        modifier =
                            Modifier.width(
                                38.dp,
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text(
                            text =
                                channel.name,
                            color =
                                Color.White,
                            fontWeight =
                                FontWeight.SemiBold,
                            maxLines = 1,
                            overflow =
                                TextOverflow
                                    .Ellipsis,
                        )

                        Text(
                            text =
                                if (
                                    channel.epgId
                                        .isNullOrBlank()
                                ) {
                                    "Canlı yayın"
                                } else {
                                    "EPG mevcut"
                                },
                            color =
                                Color(
                                    0xFF64748B,
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPanel(
    channel:
        NativeLiveChannel?,
    request:
        PlaybackRequest?,
    playerFor:
        (PlaybackRequest) -> ExoPlayer,
    onFullscreen:
        () -> Unit,
    modifier:
        Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                Color(0xFF0B1018),
            )
            .padding(
                12.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f)
                .background(
                    color =
                        Color.Black,
                    shape =
                        RoundedCornerShape(
                            10.dp,
                        ),
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            if (
                request != null &&
                channel != null
            ) {
                NativeInlinePlayerSurface(
                    request =
                        request,
                    player =
                        playerFor(
                            request,
                        ),
                    modifier =
                        Modifier.fillMaxSize(),
                    onFullScreen =
                        onFullscreen,
                    onFailure = {},
                )
            } else {
                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp,
                        ),
                    modifier =
                        Modifier.padding(
                            18.dp,
                        ),
                ) {
                    Text(
                        text =
                            "CANLI ÖNİZLEME",
                        color =
                            Color(
                                0xFF60A5FA,
                            ),
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme
                                .typography
                                .labelLarge,
                    )

                    Text(
                        text =
                            channel?.name
                                ?: "Kanal seç",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        maxLines = 3,
                        overflow =
                            TextOverflow
                                .Ellipsis,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .background(
                    color =
                        Color(
                            0xFF111827,
                        ),
                    shape =
                        RoundedCornerShape(
                            10.dp,
                        ),
                )
                .padding(
                    14.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text = "Şimdi",
                color =
                    Color(0xFF60A5FA),
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .labelLarge,
            )

            Text(
                text =
                    channel?.name
                        ?: "Kanal seçilmedi",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                maxLines = 2,
                overflow =
                    TextOverflow
                        .Ellipsis,
            )

            Text(
                text =
                    if (channel == null) {
                        "Bir kanal seç"
                    } else if (
                        channel.epgId
                            .isNullOrBlank()
                    ) {
                        "Program bilgisi bulunamadı"
                    } else {
                        "EPG bağlantısı mevcut"
                    },
                color =
                    Color(0xFF94A3B8),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )

            if (
                channel != null
            ) {
                Text(
                    text =
                        "OK: Tam ekran  •  Tam ekranda ↑/↓: Kanal değiştir",
                    color =
                        Color(
                            0xFF64748B,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                )
            }
        }
    }
}
