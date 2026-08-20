package com.streamlivex.android.tv.diagnostics

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.streamlivex.android.tv.data.TvDiagnosticsStore
import com.streamlivex.android.tv.data.TvLibraryHealth
import com.streamlivex.android.tv.data.TvLibraryHealthChecker
import com.streamlivex.android.tv.data.TvLibraryIndex
import com.streamlivex.android.tv.data.TvProviderConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvDiagnosticsScreen(
    provider: TvProviderConfig,
    onBack: () -> Unit,
) {
    BackHandler(
        onBack = onBack,
    )

    val context =
        LocalContext.current
    val store =
        remember {
            TvDiagnosticsStore(
                context,
            )
        }
    val index =
        remember {
            TvLibraryIndex(
                context,
            )
        }

    var health by
        remember {
            mutableStateOf(
                store.health(
                    provider,
                ),
            )
        }
    var checking by
        remember {
            mutableStateOf(
                false,
            )
        }
    var errorRefresh by
        remember {
            mutableStateOf(0)
        }

    fun runHealthCheck() {
        if (checking) return
        checking = true

        Thread {
            val result =
                runCatching {
                    TvLibraryHealthChecker()
                        .check(
                            provider,
                        )
                }

            android.os.Handler(
                android.os.Looper
                    .getMainLooper(),
            ).post {
                checking = false

                result
                    .onSuccess {
                        row ->

                        health = row
                        store.saveHealth(
                            provider,
                            row,
                        )

                        if (
                            row.error != null
                        ) {
                            store.log(
                                "health",
                                row.error,
                            )
                            errorRefresh += 1
                        }
                    }
                    .onFailure {
                        throwable ->

                        store.log(
                            "health",
                            throwable.message
                                ?: "Sağlık kontrolü başarısız.",
                        )
                        errorRefresh += 1
                    }
            }
        }.start()
    }

    val errors =
        remember(
            errorRefresh,
        ) {
            store.errors()
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(
                        0xFF0D111B,
                    ),
                )
                .padding(28.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                16.dp,
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
                DiagnosticsButton(
                    "← Ayarlara Dön",
                    onClick = onBack,
                )

                Text(
                    "Tanılama",
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

        item {
            DiagnosticCard(
                title =
                    "Cihaz",
                lines =
                    listOf(
                        store.deviceSummary(),
                        "Kayıtlı playlist: ${store.playlistCount()}",
                    ),
            )
        }

        item {
            DiagnosticCard(
                title =
                    "Aktif Kütüphane",
                lines =
                    listOf(
                        provider.name,
                        provider.server,
                        "İçerik: ${index.count(provider)}",
                        "Son index: ${formatDate(index.updatedAt(provider))}",
                        "24 saatlik yenileme: ${
                            if (
                                index.needsRefresh(
                                    provider,
                                    24,
                                )
                            ) {
                                "Gerekli"
                            } else {
                                "Güncel"
                            }
                        }",
                    ),
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                DiagnosticsButton(
                    "Bağlantıyı Test Et",
                    selected =
                        checking,
                ) {
                    runHealthCheck()
                }

                if (checking) {
                    CircularProgressIndicator()
                }
            }
        }

        health?.let {
            row ->

            item {
                DiagnosticCard(
                    title =
                        "Son Sağlık Kontrolü",
                    lines =
                        listOf(
                            "Canlı TV: ${status(row.liveOk)} · ${row.liveCount} kanal",
                            "Filmler: ${status(row.moviesOk)} · ${row.movieCategoryCount} kategori",
                            "Diziler: ${status(row.seriesOk)} · ${row.seriesCategoryCount} kategori",
                            "Kontrol: ${formatDate(row.checkedAt)}",
                            row.error
                                ?.let {
                                    "Hata: $it"
                                }
                                ?: "Hata yok",
                        ),
                )
            }
        }

        item {
            Text(
                "Son Hatalar",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
            )
        }

        if (errors.isEmpty()) {
            item {
                Text(
                    "Kayıtlı hata yok.",
                    color =
                        Color(
                            0xFF94A3B8,
                        ),
                )
            }
        } else {
            items(
                count =
                    errors.size,
            ) {
                index ->

                val row =
                    errors[index]

                DiagnosticCard(
                    title =
                        row.module,
                    lines =
                        listOf(
                            formatDate(
                                row.timestamp,
                            ),
                            row.message,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    lines: List<String>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color(
                        0xFF151C28,
                    ),
                    RoundedCornerShape(
                        12.dp,
                    ),
                )
                .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                6.dp,
            ),
    ) {
        Text(
            title,
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
        )

        lines.forEach {
            line ->

            Text(
                line,
                color =
                    Color(
                        0xFFCBD5E1,
                    ),
            )
        }
    }
}

@Composable
private fun DiagnosticsButton(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by
        remember {
            mutableStateOf(
                false,
            )
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
                    onClick =
                        onClick,
                )
                .padding(
                    horizontal =
                        16.dp,
                    vertical =
                        12.dp,
                ),
    ) {
        Text(
            text,
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
        )
    }
}

private fun status(
    ok: Boolean,
): String =
    if (ok) {
        "OK"
    } else {
        "HATA"
    }

private fun formatDate(
    timestamp: Long,
): String {
    if (timestamp <= 0L) {
        return "Henüz yok"
    }

    return SimpleDateFormat(
        "dd.MM.yyyy HH:mm",
        Locale.getDefault(),
    ).format(
        Date(timestamp),
    )
}
