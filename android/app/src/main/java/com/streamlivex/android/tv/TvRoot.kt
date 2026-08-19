package com.streamlivex.android.tv

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.live.LiveTvScreen
import com.streamlivex.android.tv.setup.TvProviderStorage
import com.streamlivex.android.tv.setup.TvSetupScreen

enum class TvSection(
    val title: String,
    val shortTitle: String,
) {
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
        mutableStateOf<TvProviderConfig?>(
            TvProviderStorage.load(context),
        )
    }

    if (provider == null) {
        TvSetupScreen(
            onConnected = { connectedProvider ->
                provider = connectedProvider
            },
        )

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
    var selectedSection by remember {
        mutableStateOf(TvSection.Live)
    }

    var menuExpanded by remember {
        mutableStateOf(true)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B12)),
    ) {
        TvSideMenu(
            selectedSection = selectedSection,
            expanded = menuExpanded,
            onMenuFocused = {
                menuExpanded = true
            },
            onSectionSelected = {
                selectedSection = it
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D111B)),
        ) {
            when (selectedSection) {
                TvSection.Home -> {
                    PlaceholderScreen(
                        title = "Ana Sayfa",
                    )
                }

                TvSection.Live -> {
                    LiveTvScreen(
                        provider = provider,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        externalPlayerKeyEvent = externalPlayerKeyEvent,
                        onFullscreenStateChanged = onFullscreenStateChanged,
                        onContentFocused = {
                            menuExpanded = false
                        },
                    )
                }

                TvSection.Movies -> {
                    PlaceholderScreen(
                        title = "Filmler",
                    )
                }

                TvSection.Series -> {
                    PlaceholderScreen(
                        title = "Diziler",
                    )
                }

                TvSection.Search -> {
                    PlaceholderScreen(
                        title = "Ara",
                    )
                }

                TvSection.MyList -> {
                    PlaceholderScreen(
                        title = "Listem",
                    )
                }

                TvSection.Settings -> {
                    TvSettingsScreen(
                        provider = provider,
                        onDisconnect = onDisconnect,
                    )
                }
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
) {
    val menuWidth =
        if (expanded) {
            190.dp
        } else {
            72.dp
        }

    Column(
        modifier = Modifier
            .width(menuWidth)
            .fillMaxHeight()
            .background(Color(0xFF080B12))
            .padding(
                horizontal =
                    if (expanded) {
                        12.dp
                    } else {
                        8.dp
                    },
                vertical = 18.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text =
                if (expanded) {
                    "StreamLiveX"
                } else {
                    "SLX"
                },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style =
                if (expanded) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.labelLarge
                },
            modifier = Modifier.padding(
                start =
                    if (expanded) {
                        10.dp
                    } else {
                        6.dp
                    },
                bottom = 14.dp,
            ),
        )

        TvSection.entries.forEach { section ->
            var focused by remember {
                mutableStateOf(false)
            }

            val background =
                when {
                    focused -> {
                        Color(0xFF2563EB)
                    }

                    section == selectedSection -> {
                        Color(0xFF172554)
                    }

                    else -> {
                        Color.Transparent
                    }
                }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = background,
                        shape = RoundedCornerShape(9.dp),
                    )
                    .onFocusChanged {
                        focused = it.isFocused

                        if (it.isFocused) {
                            onMenuFocused()
                        }
                    }
                    .onPreviewKeyEvent { composeEvent ->
                        val event = composeEvent.nativeKeyEvent

                        if (
                            event.action == android.view.KeyEvent.ACTION_DOWN &&
                            (
                                event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                event.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                            )
                        ) {
                            onSectionSelected(section)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .padding(
                        horizontal =
                            if (expanded) {
                                13.dp
                            } else {
                                6.dp
                            },
                        vertical = 12.dp,
                    ),
                contentAlignment =
                    if (expanded) {
                        Alignment.CenterStart
                    } else {
                        Alignment.Center
                    },
            ) {
                Text(
                    text =
                        if (expanded) {
                            section.title
                        } else {
                            section.shortTitle
                        },
                    color = Color.White,
                    fontWeight =
                        if (
                            focused ||
                            section == selectedSection
                        ) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
private fun TvSettingsScreen(
    provider: TvProviderConfig,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B))
            .padding(42.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Ayarlar",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF111827),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Aktif oynatma listesi",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelLarge,
            )

            Text(
                text = provider.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )

            Text(
                text = provider.server,
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        var disconnectFocused by remember {
            mutableStateOf(false)
        }

        Box(
            modifier = Modifier
                .width(280.dp)
                .background(
                    color =
                        if (disconnectFocused) {
                            Color(0xFFDC2626)
                        } else {
                            Color(0xFF7F1D1D)
                        },
                    shape = RoundedCornerShape(10.dp),
                )
                .onFocusChanged {
                    disconnectFocused = it.isFocused
                }
                .focusable()
                .padding(
                    horizontal = 18.dp,
                    vertical = 14.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Oynatma listesini kaldır",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
// tv-root-force-commit-3
