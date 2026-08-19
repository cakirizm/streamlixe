package com.streamlivex.android.tv.live

import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamlivex.android.tv.data.NativeLiveCategory
import com.streamlivex.android.tv.data.NativeLiveChannel
import com.streamlivex.android.tv.data.TvLiveLibraryCache
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.data.XtreamLiveLibrary

@Composable
fun LiveTvScreen(
    provider: TvProviderConfig,
) {
    val client = remember {
        XtreamClient()
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
            TvLiveLibraryCache.library == null,
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

    val currentLibrary = library

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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B)),
    ) {
        CategoryColumn(
            categories =
                currentLibrary.categories,
            selectedCategory =
                selectedCategory,
            onSelected = { category ->
                selectedCategoryId =
                    category.id

                selectedChannelId =
                    null
            },
            modifier =
                Modifier.weight(0.28f),
        )

        ChannelColumn(
            channels =
                visibleChannels,
            selectedChannel =
                selectedChannel,
            onSelected = { channel ->
                selectedChannelId =
                    channel.id
            },
            modifier =
                Modifier.weight(0.42f),
        )

        PreviewAndEpgPanel(
            channel =
                selectedChannel,
            modifier =
                Modifier.weight(0.50f),
        )
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
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator()

            Text(
                text =
                    "Canlı TV yükleniyor...",
                color = Color.White,
                fontWeight =
                    FontWeight.SemiBold,
                style =
                    MaterialTheme.typography.titleMedium,
            )

            Text(
                text =
                    "Kategoriler ve kanallar hazırlanıyor",
                color =
                    Color(0xFF94A3B8),
                style =
                    MaterialTheme.typography.bodyMedium,
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
            .background(Color(0xFF0D111B))
            .padding(36.dp),
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Canlı TV yüklenemedi",
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = message,
                color =
                    Color(0xFFF87171),
                style =
                    MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun CategoryColumn(
    categories: List<NativeLiveCategory>,
    selectedCategory: NativeLiveCategory,
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
                .padding(
                    start = 4.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text = "Kategoriler",
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.weight(1f),
            )

            Text(
                text =
                    categories.size.toString(),
                color =
                    Color(0xFF64748B),
                style =
                    MaterialTheme.typography.labelMedium,
            )
        }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(6.dp),
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
                            Color(0xFF2563EB)
                        }

                        category.id ==
                            selectedCategory.id -> {
                            Color(0xFF172554)
                        }

                        else -> {
                            Color(0xFF151C28)
                        }
                    }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = background,
                            shape =
                                RoundedCornerShape(8.dp),
                        )
                        .onFocusChanged {
                            focused =
                                it.isFocused

                            if (it.isFocused) {
                                onSelected(
                                    category,
                                )
                            }
                        }
                        .focusable()
                        .padding(
                            horizontal = 10.dp,
                            vertical = 10.dp,
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
                            TextOverflow.Ellipsis,
                    )

                    Text(
                        text =
                            category.count
                                .toString(),
                        color =
                            if (focused) {
                                Color.White
                            } else {
                                Color(0xFF94A3B8)
                            },
                        style =
                            MaterialTheme.typography
                                .labelMedium,
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
    onSelected: (NativeLiveChannel) -> Unit,
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
                .padding(
                    start = 4.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text = "Kanallar",
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.weight(1f),
            )

            Text(
                text =
                    channels.size.toString(),
                color =
                    Color(0xFF64748B),
                style =
                    MaterialTheme.typography.labelMedium,
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
                Arrangement.spacedBy(6.dp),
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
                            Color(0xFF1D4ED8)
                        }

                        channel.id ==
                            selectedChannel?.id -> {
                            Color(0xFF172554)
                        }

                        else -> {
                            Color(0xFF141B26)
                        }
                    }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = background,
                            shape =
                                RoundedCornerShape(8.dp),
                        )
                        .onFocusChanged {
                            focused =
                                it.isFocused

                            if (it.isFocused) {
                                onSelected(
                                    channel,
                                )
                            }
                        }
                        .focusable()
                        .padding(
                            horizontal = 10.dp,
                            vertical = 10.dp,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            "${index + 1}",
                        color =
                            Color(0xFF64748B),
                        modifier =
                            Modifier.width(38.dp),
                        style =
                            MaterialTheme.typography.labelMedium,
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
                                TextOverflow.Ellipsis,
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
                                Color(0xFF64748B),
                            style =
                                MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewAndEpgPanel(
    channel: NativeLiveChannel?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0B1018))
            .padding(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
                .background(
                    color = Color.Black,
                    shape =
                        RoundedCornerShape(10.dp),
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            if (channel == null) {
                Text(
                    text =
                        "Kanal seç",
                    color =
                        Color(0xFF64748B),
                    fontWeight =
                        FontWeight.SemiBold,
                )
            } else {
                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier.padding(18.dp),
                ) {
                    Text(
                        text =
                            "CANLI ÖNİZLEME",
                        color =
                            Color(0xFF60A5FA),
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme.typography.labelLarge,
                    )

                    Text(
                        text =
                            channel.name,
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow =
                            TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .background(
                    color =
                        Color(0xFF111827),
                    shape =
                        RoundedCornerShape(10.dp),
                )
                .padding(14.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Şimdi",
                color =
                    Color(0xFF60A5FA),
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme.typography.labelLarge,
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
                    MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
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
                        "EPG bilgisi alınabilir"
                    },
                color =
                    Color(0xFF94A3B8),
                style =
                    MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
