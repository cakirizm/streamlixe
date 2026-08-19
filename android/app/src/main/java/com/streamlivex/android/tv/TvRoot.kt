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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    var selectedSection by remember { mutableStateOf(TvSection.Live) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B12)),
    ) {
        TvSideMenu(
            selectedSection = selectedSection,
            onSectionSelected = { selectedSection = it },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D111B)),
        ) {
            when (selectedSection) {
                TvSection.Home -> PlaceholderScreen("Ana Sayfa")
                TvSection.Live -> PlaceholderScreen("Canlı TV")
                TvSection.Movies -> PlaceholderScreen("Filmler")
                TvSection.Series -> PlaceholderScreen("Diziler")
                TvSection.Search -> PlaceholderScreen("Ara")
                TvSection.MyList -> PlaceholderScreen("Listem")
                TvSection.Settings -> PlaceholderScreen("Ayarlar")
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
            .width(220.dp)
            .fillMaxHeight()
            .background(Color(0xFF090C13))
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "StreamLiveX",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                start = 12.dp,
                bottom = 22.dp,
            ),
        )

        TvSection.entries.forEach { section ->
            TvMenuItem(
                section = section,
                selected = section == selectedSection,
                onSelected = {
                    onSectionSelected(section)
                },
            )
        }
    }
}

@Composable
private fun TvMenuItem(
    section: TvSection,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    val background = when {
        focused -> Color(0xFF2563EB)
        selected -> Color(0xFF172554)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = background,
                shape = RoundedCornerShape(10.dp),
            )
            .onFocusChanged {
                focused = it.isFocused

                if (it.isFocused) {
                    onSelected()
                }
            }
            .focusable()
            .padding(
                horizontal = 16.dp,
                vertical = 13.dp,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = section.title,
            color = if (focused || selected) {
                Color.White
            } else {
                Color(0xFF9CA3AF)
            },
            fontWeight = if (focused) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
        )
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "StreamLiveX TV",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
