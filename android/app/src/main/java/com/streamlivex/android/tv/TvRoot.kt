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
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.live.LiveTvScreen
import com.streamlivex.android.tv.setup.TvProviderStorage
import com.streamlivex.android.tv.setup.TvSetupScreen

enum class TvSection(
    val title: String,
) {
    Home("Ana Sayfa"),
    Live("Canlı TV"),
    Movies("Filmler"),
    Series("Diziler"),
    Search("Ara"),
    MyList("Listem"),
    Settings("Ayarlar"),
}

@Composable
fun TvRoot() {
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
        onDisconnect = {
            TvProviderStorage.clear(context)
            provider = null
        },
    )
}

@Composable
private fun TvMainScreen(
    provider: TvProviderConfig,
    onDisconnect: () -> Unit,
) {
    var selectedSection by remember {
        mutableStateOf(TvSection.Live)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B12)),
    ) {
        TvSideMenu(
            selectedSection = selectedSection,
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
    onSectionSelected: (TvSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(190.dp)
            .fillMaxHeight()
            .background(Color(0xFF080B12))
            .padding(
                horizontal = 12.dp,
                vertical = 18.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "StreamLiveX",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = 10.dp,
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
                            onSectionSelected(section)
                        }
                    }
                    .focusable()
                    .padding(
                        horizontal = 13.dp,
                        vertical = 12.dp,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = section.title,
                    color =
                        if (focused) {
                            Color.White
                        } else {
                            Color(0xFFE2E8F0)
                        },
                    fontWeight =
                        if (
                            focused ||
                            section == selectedSection
                        ) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
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
