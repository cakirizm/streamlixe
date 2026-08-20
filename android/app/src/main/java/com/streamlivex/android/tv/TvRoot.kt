package com.streamlivex.android.tv

import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.content.TvHomeScreen
import com.streamlivex.android.tv.content.TvMoviesScreen
import com.streamlivex.android.tv.content.TvMyListScreen
import com.streamlivex.android.tv.content.TvSearchScreen
import com.streamlivex.android.tv.content.TvSeriesScreen
import com.streamlivex.android.tv.data.TvContentCache
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvLibraryIndex
import com.streamlivex.android.tv.data.TvLiveLibraryCache
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.i18n.TvLocale
import com.streamlivex.android.tv.i18n.TvLocaleStore
import com.streamlivex.android.tv.i18n.TvStrings
import com.streamlivex.android.tv.live.LiveTvScreen
import com.streamlivex.android.tv.setup.TvProviderStorage
import com.streamlivex.android.tv.setup.TvSetupScreen

enum class TvSection {
    Home,
    Live,
    Movies,
    Series,
    Search,
    MyList,
    Settings,
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
    var locale by remember {
        mutableStateOf(TvLocaleStore.get(context))
    }

    if (provider == null) {
        TvSetupScreen(
            onConnected = {
                provider = it
            },
        )
        return
    }

    val activeProvider = provider!!
    val libraryIndex = remember(activeProvider.server, activeProvider.username) {
        TvLibraryIndex(context)
    }

    var libraryReady by remember(activeProvider.server, activeProvider.username) {
        mutableStateOf(libraryIndex.isReady(activeProvider))
    }
    var preparationStage by remember { mutableStateOf("Kütüphane hazırlanıyor…") }
    var preparationCount by remember { mutableIntStateOf(libraryIndex.count(activeProvider)) }
    var preparationPercent by remember { mutableIntStateOf(if (libraryReady) 100 else 0) }
    var preparationError by remember { mutableStateOf("") }
    var preparationRetry by remember { mutableIntStateOf(0) }

    LaunchedEffect(
        activeProvider.server,
        activeProvider.username,
        preparationRetry,
    ) {
        if (libraryIndex.isReady(activeProvider)) {
            libraryReady = true
            preparationCount = libraryIndex.count(activeProvider)
            preparationPercent = 100
            return@LaunchedEffect
        }

        libraryReady = false
        preparationError = ""
        preparationStage = "Oynatma listesi sayılıyor…"
        preparationCount = 0
        preparationPercent = 0

        Thread {
            val handler = Handler(Looper.getMainLooper())
            val client = XtreamClient()

            val result = runCatching {
                handler.post {
                    preparationStage = "Kategoriler hazırlanıyor…"
                    preparationPercent = 1
                }

                val vodCategories =
                    client.loadVodCategories(activeProvider)
                        .getOrThrow()
                val seriesCategories =
                    client.loadSeriesCategories(activeProvider)
                        .getOrThrow()

                libraryIndex.saveVodCategories(
                    activeProvider,
                    vodCategories,
                )
                libraryIndex.saveSeriesCategories(
                    activeProvider,
                    seriesCategories,
                )

                handler.post {
                    preparationStage = "İçerik sayısı belirleniyor…"
                    preparationPercent = 2
                }

                val movieTotal =
                    client.scanVod(activeProvider) { }
                        .getOrThrow()

                handler.post {
                    preparationStage = "Diziler sayılıyor…"
                    preparationPercent = 6
                }

                val seriesTotal =
                    client.scanSeries(activeProvider) { }
                        .getOrThrow()

                val totalExpected =
                    (movieTotal + seriesTotal)
                        .coerceAtLeast(1)

                libraryIndex.rebuildProvider(
                    provider = activeProvider,
                    client = client,
                    onProgress = { stage, processed ->
                        val exact =
                            10 +
                                (
                                    processed
                                        .toDouble() /
                                        totalExpected
                                            .toDouble() *
                                        89.0
                                ).toInt()

                        handler.post {
                            preparationCount =
                                processed
                            preparationPercent =
                                exact.coerceIn(
                                    10,
                                    99,
                                )
                            preparationStage =
                                when (stage) {
                                    "movies" ->
                                        "Filmler hazırlanıyor…"
                                    "series" ->
                                        "Diziler hazırlanıyor…"
                                    "done" ->
                                        "Kütüphane tamamlanıyor…"
                                    else ->
                                        "Kütüphane hazırlanıyor…"
                                }
                        }
                    },
                ).getOrThrow()
            }

            handler.post {
                result
                    .onSuccess {
                        preparationCount = it
                        preparationPercent = 100
                        preparationStage = "Kütüphane hazır."
                        libraryReady = true
                    }
                    .onFailure {
                        libraryReady = false
                        preparationError =
                            it.message
                                ?: "Kütüphane hazırlanamadı."
                    }
            }
        }.start()
    }

    if (!libraryReady) {
        LibraryPreparationScreen(
            stage = preparationStage,
            processed = preparationCount,
            percent = preparationPercent,
            error = preparationError,
            onRetry = {
                preparationRetry += 1
            },
        )
        return
    }

    TvMainScreen(
        provider = activeProvider,
        locale = locale,
        playerFor = playerFor,
        releasePlayer = releasePlayer,
        externalPlayerKeyEvent = externalPlayerKeyEvent,
        onFullscreenStateChanged = onFullscreenStateChanged,
        onLocaleChanged = {
            TvLocaleStore.set(context, it)
            locale = it
        },
        onDisconnect = {
            TvProviderStorage.clear(context)
            TvLiveLibraryCache.clear()
            TvContentCache.clear()
            TvContentStore(context).clear()
            libraryIndex.clearProvider(activeProvider)
            provider = null
        },
    )
}

@Composable
private fun LibraryPreparationScreen(
    stage: String,
    processed: Int,
    percent: Int,
    error: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF080B12)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "StreamLiveX",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displaySmall,
            )

            if (error.isBlank()) {
                CircularProgressIndicator()

                Text(
                    stage,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )

                Text(
                    "%$percent · $processed içerik işlendi",
                    color = Color(0xFF60A5FA),
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    "İlk kurulum 30 saniye–1 dakika sürebilir. Bu sırada film ve dizi listesi RAM'e yığılmadan diske hazırlanıyor.",
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    error,
                    color = Color(0xFFF87171),
                    textAlign = TextAlign.Center,
                )

                PreparationButton("Yeniden Dene", onRetry)
            }
        }
    }
}

@Composable
private fun PreparationButton(
    text: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .background(
                    if (focused) Color(0xFF2563EB)
                    else Color(0xFF1E293B),
                    RoundedCornerShape(9.dp),
                )
                .onFocusChanged {
                    focused = it.isFocused
                }
                .clickable(onClick = onClick)
                .padding(
                    horizontal = 18.dp,
                    vertical = 12.dp,
                ),
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TvMainScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onLocaleChanged: (TvLocale) -> Unit,
    onDisconnect: () -> Unit,
) {
    val strings = remember(locale) {
        TvStrings(locale)
    }

    var selectedSection by remember {
        mutableStateOf(TvSection.Home)
    }
    var menuExpanded by remember {
        mutableStateOf(true)
    }
    var anyFullscreen by remember {
        mutableStateOf(false)
    }

    val liveMenuFocusRequester =
        remember {
            FocusRequester()
        }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF080B12)),
    ) {
        if (!anyFullscreen) {
            TvSideMenu(
                selectedSection = selectedSection,
                expanded = menuExpanded,
                locale = locale,
                onMenuFocused = {
                    menuExpanded = true
                },
                onSectionSelected = {
                    selectedSection = it
                },
                liveMenuFocusRequester =
                    liveMenuFocusRequester,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D111B)),
        ) {
            val fullscreenCallback:
                (Boolean) -> Unit = { active ->
                    anyFullscreen = active
                    onFullscreenStateChanged(active)
                }

            when (selectedSection) {
                TvSection.Home ->
                    TvHomeScreen(
                        provider = provider,
                        locale = locale,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        onFullscreenStateChanged =
                            fullscreenCallback,
                    )

                TvSection.Live ->
                    LiveTvScreen(
                        provider = provider,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        externalPlayerKeyEvent =
                            externalPlayerKeyEvent,
                        onFullscreenStateChanged =
                            fullscreenCallback,
                        onContentFocused = {
                            if (!anyFullscreen) {
                                menuExpanded = false
                            }
                        },
                        menuFocusRequester =
                            liveMenuFocusRequester,
                    )

                TvSection.Movies ->
                    TvMoviesScreen(
                        provider = provider,
                        locale = locale,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        onFullscreenStateChanged =
                            fullscreenCallback,
                        onContentFocused = {
                            if (!anyFullscreen) {
                                menuExpanded = false
                            }
                        },
                    )

                TvSection.Series ->
                    TvSeriesScreen(
                        provider = provider,
                        locale = locale,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        onFullscreenStateChanged =
                            fullscreenCallback,
                        onContentFocused = {
                            if (!anyFullscreen) {
                                menuExpanded = false
                            }
                        },
                    )

                TvSection.Search ->
                    TvSearchScreen(
                        provider = provider,
                        locale = locale,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        onFullscreenStateChanged =
                            fullscreenCallback,
                        onContentFocused = {
                            if (!anyFullscreen) {
                                menuExpanded = false
                            }
                        },
                    )

                TvSection.MyList ->
                    TvMyListScreen(
                        locale = locale,
                    )

                TvSection.Settings ->
                    TvSettingsScreen(
                        provider = provider,
                        locale = locale,
                        strings = strings,
                        onLocaleChanged =
                            onLocaleChanged,
                        onDisconnect =
                            onDisconnect,
                    )
            }
        }
    }
}

@Composable
private fun TvSideMenu(
    selectedSection: TvSection,
    expanded: Boolean,
    locale: TvLocale,
    onMenuFocused: () -> Unit,
    onSectionSelected: (TvSection) -> Unit,
    liveMenuFocusRequester: FocusRequester,
) {
    val strings =
        remember(locale) {
            TvStrings(locale)
        }

    val menuWidth =
        if (expanded) 190.dp
        else 72.dp

    fun title(
        section: TvSection,
    ): String =
        when (section) {
            TvSection.Home -> strings["home"]
            TvSection.Live -> strings["live"]
            TvSection.Movies -> strings["movies"]
            TvSection.Series -> strings["series"]
            TvSection.Search -> strings["search"]
            TvSection.MyList -> strings["my_list"]
            TvSection.Settings -> strings["settings"]
        }

    fun short(
        section: TvSection,
    ): String =
        when (section) {
            TvSection.Home -> "A"
            TvSection.Live -> "TV"
            TvSection.Movies -> "F"
            TvSection.Series -> "D"
            TvSection.Search -> "⌕"
            TvSection.MyList -> "★"
            TvSection.Settings -> "⚙"
        }

    Column(
        modifier =
            Modifier
                .width(menuWidth)
                .fillMaxHeight()
                .background(Color(0xFF080B12))
                .padding(
                    horizontal =
                        if (expanded) 12.dp
                        else 8.dp,
                    vertical = 18.dp,
                ),
        verticalArrangement =
            Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text =
                if (expanded) "StreamLiveX"
                else "SLX",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style =
                if (expanded) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.labelLarge
                },
            modifier =
                Modifier.padding(
                    start =
                        if (expanded) 10.dp
                        else 6.dp,
                    bottom = 14.dp,
                ),
        )

        TvSection.entries.forEach {
            section ->

            var focused by
                remember {
                    mutableStateOf(false)
                }

            val background =
                when {
                    focused ->
                        Color(0xFF2563EB)

                    section == selectedSection ->
                        Color(0xFF172554)

                    else ->
                        Color.Transparent
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            background,
                            RoundedCornerShape(9.dp),
                        )
                        .then(
                            if (
                                section ==
                                TvSection.Live
                            ) {
                                Modifier
                                    .focusRequester(
                                        liveMenuFocusRequester,
                                    )
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged {
                            focused =
                                it.isFocused

                            if (it.isFocused) {
                                onMenuFocused()
                            }
                        }
                        .clickable {
                            onSectionSelected(
                                section,
                            )
                        }
                        .padding(
                            horizontal =
                                if (expanded) 13.dp
                                else 6.dp,
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
                            title(section)
                        } else {
                            short(section)
                        },
                    color = Color.White,
                    fontWeight =
                        if (
                            focused ||
                            section ==
                            selectedSection
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
private fun TvSettingsScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    strings: TvStrings,
    onLocaleChanged: (TvLocale) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D111B))
                .padding(36.dp),
        verticalArrangement =
            Arrangement.spacedBy(18.dp),
    ) {
        Text(
            strings["settings"],
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style =
                MaterialTheme
                    .typography
                    .headlineLarge,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF111827),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(20.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                provider.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                provider.server,
                color = Color(0xFF94A3B8),
            )
        }

        Text(
            strings["language"],
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(10.dp),
        ) {
            TvLocale.entries.forEach { row ->
                SettingsButton(
                    text = row.displayName,
                    selected =
                        row == locale,
                ) {
                    onLocaleChanged(row)
                }
            }
        }

        SettingsButton(
            text =
                strings["remove_playlist"],
            destructive = true,
        ) {
            onDisconnect()
        }
    }
}

@Composable
private fun SettingsButton(
    text: String,
    selected: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by
        remember {
            mutableStateOf(false)
        }

    val color =
        when {
            focused && destructive ->
                Color(0xFFDC2626)

            destructive ->
                Color(0xFF7F1D1D)

            focused ->
                Color(0xFF2563EB)

            selected ->
                Color(0xFF1D4ED8)

            else ->
                Color(0xFF1E293B)
        }

    Box(
        modifier =
            Modifier
                .background(
                    color,
                    RoundedCornerShape(9.dp),
                )
                .onFocusChanged {
                    focused = it.isFocused
                }
                .clickable(
                    onClick = onClick,
                )
                .padding(
                    horizontal = 15.dp,
                    vertical = 12.dp,
                ),
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
