package com.streamlivex.android.tv.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun TvPlaylistManagerScreen(
    onBack: () -> Unit,
    onSelected: (TvProviderConfig) -> Unit,
) {
    BackHandler(
        onBack = onBack,
    )

    val context =
        LocalContext.current
    val registry =
        remember {
            TvPlaylistRegistry(
                context,
            )
        }

    var rows by
        remember {
            mutableStateOf(
                registry.all(),
            )
        }
    var adding by
        remember {
            mutableStateOf(false)
        }
    var activeId by
        remember {
            mutableStateOf(
                registry.activeId(),
            )
        }

    if (adding) {
        TvSetupScreen(
            onConnected = {
                provider ->

                registry.addOrUpdate(
                    provider,
                    makeActive = true,
                )
                rows =
                    registry.all()
                activeId =
                    registry.activeId()
                adding = false
                onSelected(
                    provider,
                )
            },
            onBack = {
                adding = false
            },
        )
        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF0D111B),
                )
                .padding(30.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp,
            ),
    ) {
        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                PlaylistButton(
                    "← Ayarlara Dön",
                    onClick = onBack,
                )
                Text(
                    "Oynatma Listeleri",
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,
                )
            }
        }

        items(
            count = rows.size,
            key = {
                rows[it].id
            },
        ) {
            index ->

            val account =
                rows[index]
            val selected =
                account.id ==
                    activeId

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) {
                                Color(
                                    0xFF172554,
                                )
                            } else {
                                Color(
                                    0xFF151C28,
                                )
                            },
                            RoundedCornerShape(
                                12.dp,
                            ),
                        )
                        .padding(16.dp),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp,
                    ),
            ) {
                Column(
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            5.dp,
                        ),
                ) {
                    Text(
                        account
                            .provider
                            .name,
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                    )
                    Text(
                        account
                            .provider
                            .server,
                        color =
                            Color(
                                0xFF94A3B8,
                            ),
                    )
                    Text(
                        if (selected) {
                            "Aktif oynatma listesi"
                        } else {
                            "Kullanılabilir"
                        },
                        color =
                            if (selected) {
                                Color(
                                    0xFF60A5FA,
                                )
                            } else {
                                Color(
                                    0xFF64748B,
                                )
                            },
                    )
                }

                if (!selected) {
                    PlaylistButton(
                        "Bu Listeye Geç",
                    ) {
                        val row =
                            registry.select(
                                account.id,
                            )
                                ?: return@PlaylistButton
                        activeId =
                            row.id
                        rows =
                            registry.all()
                        onSelected(
                            row.provider,
                        )
                    }
                }

                if (rows.size > 1) {
                    PlaylistButton(
                        "Sil",
                    ) {
                        val next =
                            registry.remove(
                                account.id,
                            )
                        rows =
                            registry.all()
                        activeId =
                            registry.activeId()

                        if (
                            selected &&
                            next != null
                        ) {
                            onSelected(
                                next.provider,
                            )
                        }
                    }
                }
            }
        }

        item {
            PlaylistButton(
                "＋ Yeni Liste Ekle",
            ) {
                adding = true
            }
        }
    }
}

@Composable
private fun PlaylistButton(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by
        remember {
            mutableStateOf(false)
        }

    Box(
        modifier =
            Modifier
                .background(
                    if (
                        focused ||
                        selected
                    ) {
                        Color(
                            0xFF2563EB,
                        )
                    } else {
                        Color(
                            0xFF273449,
                        )
                    },
                    RoundedCornerShape(
                        9.dp,
                    ),
                )
                .onFocusChanged {
                    focused =
                        it.isFocused
                }
                .clickable(
                    onClick = onClick,
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight =
                FontWeight.Bold,
        )
    }
}
