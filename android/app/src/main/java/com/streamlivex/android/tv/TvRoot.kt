package com.streamlivex.android.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.content.TvHomeScreen
import com.streamlivex.android.tv.content.TvMoviesScreen
import com.streamlivex.android.tv.content.TvMyListScreen
import com.streamlivex.android.tv.content.TvSearchScreen
import com.streamlivex.android.tv.content.TvSeriesScreen
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvLiveLibraryCache
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.TvSeriesLibraryCache
import com.streamlivex.android.tv.data.TvVodLibraryCache
import com.streamlivex.android.tv.live.LiveTvScreen
import com.streamlivex.android.tv.setup.TvProviderStorage
import com.streamlivex.android.tv.setup.TvSetupScreen

enum class TvSection(val title: String, val shortTitle: String) {
    Home("Ana Sayfa", "A"),
    Live("Canlı TV", "TV"),
    Movies("Filmler", "F"),
    Series("Diziler", "D"),
    Search("Ara", "⌕"),
    MyList("Listem", "★"),
    Settings("Ayarlar", "⚙"),
}

@Composable
fun TvRoot(
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var provider by remember {
        mutableStateOf<TvProviderConfig?>(TvProviderStorage.load(context))
    }

    if (provider == null) {
        TvSetupScreen(onConnected = { provider = it })
        return
    }

    TvMainScreen(
        provider = provider!!,
        playerFor = playerFor,
        releasePlayer = releasePlayer,
        externalPlayerKeyEvent = externalPlayerKeyEvent,
        onFullscreenStateChanged = onFullscreenStateChanged,
        onDisconnect = {
            TvProviderStorage.clear(context)
            TvLiveLibraryCache.clear()
            TvVodLibraryCache.clear()
            TvSeriesLibraryCache.clear()
            TvContentStore(context).clear()
            provider = null
        },
    )
}

@Composable
private fun TvMainScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    var selectedSection by remember { mutableStateOf(TvSection.Live) }
    var menuExpanded by remember { mutableStateOf(true) }
    var anyFullscreen by remember { mutableStateOf(false) }
    val liveMenuFocusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080B12)),
    ) {
        if (!anyFullscreen) {
            TvSideMenu(
                selectedSection = selectedSection,
                expanded = menuExpanded,
                onMenuFocused = { menuExpanded = true },
                onSectionSelected = { selectedSection = it },
                liveMenuFocusRequester = liveMenuFocusRequester,
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)),
        ) {
            val fullscreenCallback: (Boolean) -> Unit = { active ->
                anyFullscreen = active
                onFullscreenStateChanged(active)
            }

            when (selectedSection) {
                TvSection.Home -> TvHomeScreen(
                    provider = provider,
                    playerFor = playerFor,
                    releasePlayer = releasePlayer,
                    externalPlayerKeyEvent = externalPlayerKeyEvent,
                    onFullscreenStateChanged = fullscreenCallback,
                )

                TvSection.Live -> LiveTvScreen(
                    provider = provider,
                    playerFor = playerFor,
                    releasePlayer = releasePlayer,
                    externalPlayerKeyEvent = externalPlayerKeyEvent,
                    onFullscreenStateChanged = fullscreenCallback,
                    onContentFocused = {
                        if (!anyFullscreen) menuExpanded = false
                    },
                    menuFocusRequester = liveMenuFocusRequester,
                )

                TvSection.Movies -> TvMoviesScreen(
                    provider = provider,
                    playerFor = playerFor,
                    releasePlayer = releasePlayer,
                    externalPlayerKeyEvent = externalPlayerKeyEvent,
                    onFullscreenStateChanged = fullscreenCallback,
                    onContentFocused = {
                        if (!anyFullscreen) menuExpanded = false
                    },
                )

                TvSection.Series -> TvSeriesScreen(
                    provider = provider,
                    playerFor = playerFor,
                    releasePlayer = releasePlayer,
                    externalPlayerKeyEvent = externalPlayerKeyEvent,
                    onFullscreenStateChanged = fullscreenCallback,
                    onContentFocused = {
                        if (!anyFullscreen) menuExpanded = false
                    },
                )

                TvSection.Search -> TvSearchScreen(
                    provider = provider,
                    playerFor = playerFor,
                    releasePlayer = releasePlayer,
                    externalPlayerKeyEvent = externalPlayerKeyEvent,
                    onFullscreenStateChanged = fullscreenCallback,
                )

                TvSection.MyList -> TvMyListScreen(
                    playerFor = playerFor,
                    releasePlayer = releasePlayer,
                    externalPlayerKeyEvent = externalPlayerKeyEvent,
                    onFullscreenStateChanged = fullscreenCallback,
                )

                TvSection.Settings -> TvSettingsScreen(
                    provider = provider,
                    onDisconnect = onDisconnect,
                )
            }
        }
    }
}

@Composable
private fun TvSideMenu(
    selectedSection: TvSection,
    expanded: Boolean,
    onMenuFocused: () -> Unit,
    onSectionSelected: (TvSection) -> Unit,
    liveMenuFocusRequester: FocusRequester,
) {
    val menuWidth = if (expanded) 190.dp else 72.dp

    Column(
        modifier = Modifier
            .width(menuWidth)
            .fillMaxHeight()
            .background(Color(0xFF080B12))
            .padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = if (expanded) "StreamLiveX" else "SLX",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (expanded) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = if (expanded) 10.dp else 6.dp, bottom = 14.dp),
        )

        TvSection.entries.forEach { section ->
            var focused by remember { mutableStateOf(false) }
            val background = when {
                focused -> Color(0xFF2563EB)
                section == selectedSection -> Color(0xFF172554)
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background, RoundedCornerShape(9.dp))
                    .then(
                        if (section == TvSection.Live) Modifier.focusRequester(liveMenuFocusRequester)
                        else Modifier,
                    )
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onMenuFocused()
                    }
                    .clickable { onSectionSelected(section) }
                    .padding(horizontal = if (expanded) 13.dp else 6.dp, vertical = 12.dp),
                contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
            ) {
                Text(
                    text = if (expanded) section.title else section.shortTitle,
                    color = Color.White,
                    fontWeight = if (focused || section == selectedSection) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TvSettingsScreen(
    provider: TvProviderConfig,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(42.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Ayarlar",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111827), RoundedCornerShape(14.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Aktif oynatma listesi", color = Color(0xFF94A3B8))
            Text(provider.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(provider.server, color = Color(0xFF94A3B8))
        }

        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .width(280.dp)
                .background(if (focused) Color(0xFFDC2626) else Color(0xFF7F1D1D), RoundedCornerShape(10.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable { onDisconnect() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Oynatma listesini kaldır", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
