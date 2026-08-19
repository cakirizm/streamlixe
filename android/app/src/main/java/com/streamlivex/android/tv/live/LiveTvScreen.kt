package com.streamlivex.android.tv.live

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class TvCategory(
    val id: String,
    val name: String,
    val count: Int,
)

data class TvChannel(
    val id: String,
    val name: String,
    val nowPlaying: String,
    val timeRange: String,
)

@Composable
fun LiveTvScreen() {
    val categories = remember {
        listOf(
            TvCategory("all", "Tümü", 318),
            TvCategory("favorites", "Favoriler", 7),
            TvCategory("news", "Haber", 24),
            TvCategory("sports", "Spor", 34),
            TvCategory("kids", "Çocuk", 18),
            TvCategory("documentary", "Belgesel", 23),
            TvCategory("local", "Yerli", 42),
            TvCategory("music", "Müzik", 15),
        )
    }

    val channels = remember {
        listOf(
            TvChannel(
                "1",
                "beIN SPORTS 1 HD",
                "Fenerbahçe - Galatasaray",
                "20:00 - 22:00",
            ),
            TvChannel(
                "2",
                "beIN SPORTS 2 HD",
                "Premier League Özetler",
                "20:00 - 21:00",
            ),
            TvChannel(
                "3",
                "TRT SPOR HD",
                "Stadyum",
                "20:30 - 22:00",
            ),
            TvChannel(
                "4",
                "S SPORT HD",
                "NBA: Play-Off Özel",
                "21:00 - 23:00",
            ),
            TvChannel(
                "5",
                "A SPOR HD",
                "Spor Gündemi",
                "20:00 - 21:30",
            ),
            TvChannel(
                "6",
                "EUROSPORT 1 HD",
                "Bisiklet Turu",
                "20:15 - 22:15",
            ),
        )
    }

    var selectedCategory by remember {
        mutableStateOf(categories[3])
    }

    var selectedChannel by remember {
        mutableStateOf(channels.first())
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B)),
    ) {
        CategoryColumn(
            categories = categories,
            selectedCategory = selectedCategory,
            onSelected = {
                selectedCategory = it
            },
            modifier = Modifier.weight(0.28f),
        )

        ChannelColumn(
            channels = channels,
            selectedChannel = selectedChannel,
            onSelected = {
                selectedChannel = it
            },
            modifier = Modifier.weight(0.42f),
        )

        PreviewAndEpgPanel(
            channel = selectedChannel,
            modifier = Modifier.weight(0.50f),
        )
    }
}

@Composable
private fun CategoryColumn(
    categories: List<TvCategory>,
    selectedCategory: TvCategory,
    onSelected: (TvCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF101722))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Kategoriler",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                start = 4.dp,
                bottom = 6.dp,
            ),
        )

        categories.forEach { category ->
            var focused by remember {
                mutableStateOf(false)
            }

            val background = when {
                focused -> Color(0xFF2563EB)
                category == selectedCategory -> Color(0xFF172554)
                else -> Color(0xFF151C28)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = background,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .onFocusChanged {
                        focused = it.isFocused

                        if (it.isFocused) {
                            onSelected(category)
                        }
                    }
                    .focusable()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 10.dp,
                    ),
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
                    color = if (focused) {
                        Color.White
                    } else {
                        Color(0xFF94A3B8)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ChannelColumn(
    channels: List<TvChannel>,
    selectedChannel: TvChannel,
    onSelected: (TvChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0F141E))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Kanallar",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                start = 4.dp,
                bottom = 6.dp,
            ),
        )

        channels.forEachIndexed { index, channel ->
            var focused by remember {
                mutableStateOf(false)
            }

            val background = when {
                focused -> Color(0xFF1D4ED8)
                channel == selectedChannel -> Color(0xFF172554)
                else -> Color(0xFF141B26)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = background,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .onFocusChanged {
                        focused = it.isFocused

                        if (it.isFocused) {
                            onSelected(channel)
                        }
                    }
                    .focusable()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 9.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.width(30.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = channel.nowPlaying,
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewAndEpgPanel(
    channel: TvChannel,
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
                .weight(0.55f)
                .background(
                    color = Color.Black,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "CANLI ÖNİZLEME",
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )

                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .background(
                    color = Color(0xFF111827),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Şimdi",
                color = Color(0xFF60A5FA),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )

            Text(
                text = channel.nowPlaying,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = channel.timeRange,
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1F2937),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(vertical = 3.dp),
            )
        }
    }
}
