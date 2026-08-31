package com.streamlivex.android.tv

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.streamlivex.android.R
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.content.TvHomeScreen
import com.streamlivex.android.tv.content.TvHistoryScreen
import com.streamlivex.android.tv.content.TvMoviesScreen
import com.streamlivex.android.tv.content.TvMyListScreen
import com.streamlivex.android.tv.content.TvSearchScreen
import com.streamlivex.android.tv.content.TvSeriesScreen
import com.streamlivex.android.tv.data.TvContentCache
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvLibraryIndex
import com.streamlivex.android.tv.data.TvLiveLibraryCache
import com.streamlivex.android.tv.data.TvPlaybackSettings
import com.streamlivex.android.tv.data.TvPlaybackSettingsStore
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.TvDiagnosticsStore
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.i18n.TvLocale
import com.streamlivex.android.tv.i18n.TvLocaleStore
import com.streamlivex.android.tv.i18n.TvStrings
import com.streamlivex.android.tv.live.LiveTvScreen
import com.streamlivex.android.tv.sports.TvSportsScreen
import com.streamlivex.android.tv.setup.TvProviderStorage
import com.streamlivex.android.tv.setup.TvSetupScreen
import com.streamlivex.android.tv.diagnostics.TvDiagnosticsScreen
import kotlinx.coroutines.delay
import com.streamlivex.android.tv.setup.TvPlaylistManagerScreen
import com.streamlivex.android.tv.profile.TvProfileStore
import com.streamlivex.android.tv.profile.TvProfileSelectScreen
import com.streamlivex.android.tv.profile.TvProfileManagerScreen
import com.streamlivex.android.tv.profile.TvProfile
import com.streamlivex.android.tv.profile.TvActiveScope

enum class TvSection {
    Home,
    Live,
    Sports,
    Movies,
    Series,
    Search,
    MyList,
    History,
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

    val diagnostics =
        remember {
            TvDiagnosticsStore(
                context,
            )
        }

    var provider by remember {
        mutableStateOf<TvProviderConfig?>(TvProviderStorage.load(context))
    }
    var locale by remember {
        mutableStateOf(TvLocaleStore.get(context))
    }

    var selectedProfile by remember {
        mutableStateOf<TvProfile?>(null)
    }
    var bootstrapComplete by remember {
        mutableStateOf(false)
    }

    if (provider == null) {
        TvSetupScreen(
            onConnected = {
                provider = it
                selectedProfile = null
                bootstrapComplete = false
            },
        )
        return
    }

    val activeProvider = provider!!
    val libraryIndex = remember(activeProvider.server, activeProvider.username) {
        TvLibraryIndex(context)
    }

    var libraryReady by remember(activeProvider.server, activeProvider.username) {
        mutableStateOf(
            libraryIndex.isReady(
                activeProvider,
            ) &&
                !libraryIndex.needsRefresh(
                    activeProvider,
                    24,
                ),
        )
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
        if (
            libraryIndex.isReady(
                activeProvider,
            ) &&
            !libraryIndex.needsRefresh(
                activeProvider,
                24,
            )
        ) {
            libraryReady = true
            preparationCount =
                libraryIndex.count(
                    activeProvider,
                )
            preparationPercent = 100
            return@LaunchedEffect
        }

        libraryReady = false
        preparationError = ""
        preparationStage =
            if (
                libraryIndex.isReady(
                    activeProvider,
                )
            ) {
                "Kütüphane güncelleniyor…"
            } else {
                "Oynatma listesi sayılıyor…"
            }
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
                        diagnostics.log(
                            "library",
                            preparationError,
                        )
                    }
            }
        }.start()
    }

    LaunchedEffect(
        activeProvider.server,
        activeProvider.username,
        libraryReady,
    ) {
        if (!libraryReady) {
            bootstrapComplete = false
            return@LaunchedEffect
        }

        bootstrapComplete = false
        delay(5_000)
        bootstrapComplete = true
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

    if (!bootstrapComplete) {
        LibraryWarmupScreen(
            providerName = activeProvider.name,
        )
        return
    }

    val profileStore =
        remember {
            TvProfileStore(context)
        }
    profileStore.all()

    if (selectedProfile == null) {
        TvProfileSelectScreen(
            onSelected = {
                profile ->
                TvPerformanceTrace.profileSelectedAtMs = android.os.SystemClock.elapsedRealtime()
                TvActiveScope.activate(
                    playlistId =
                        TvProviderStorage
                            .activeId(context),
                    profile =
                        profile,
                )
                selectedProfile =
                    profile
            },
        )
        return
    }

    TvActiveScope.activate(
        playlistId =
            TvProviderStorage
                .activeId(context),
        profile =
            selectedProfile!!,
    )

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
        onRefreshLibrary = {
            TvContentCache.clear()
            libraryIndex.markNotReady(
                activeProvider,
            )
            libraryReady = false
            preparationPercent = 0
            preparationCount = 0
            preparationRetry += 1
        },
        onClearMemoryCache = {
            TvContentCache.clear()
        },
        onProviderSelected = {
            next ->
            TvLiveLibraryCache.clear()
            TvContentCache.clear()
            provider = next
            selectedProfile = null
            bootstrapComplete = false
        },
        onChooseProfile = {
            selectedProfile = null
        },
        onDisconnect = {
            TvLiveLibraryCache.clear()
            TvContentCache.clear()
            TvContentStore(context).clear()
            TvPlaybackSettingsStore(context).clear()
            libraryIndex.clearProvider(activeProvider)

            val next =
                TvProviderStorage
                    .removeActive(context)

            provider = next
            selectedProfile = null
            bootstrapComplete = false
        },
    )
}

@Composable
private fun LibraryWarmupScreen(
    providerName: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF080B12),
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            TvBrandHeader()

            StartupNavigation()

            CircularProgressIndicator()

            Text(
                "Kütüphaneniz yükleniyor",
                color = Color.White,
                fontWeight =
                    FontWeight.SemiBold,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
            )

            Text(
                providerName,
                color =
                    Color(0xFF60A5FA),
            )

            Text(
                "Profil ve yerel kütüphane hazırlanıyor…",
                color =
                    Color(0xFF94A3B8),
            )
        }
    }
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
            TvBrandHeader()

            StartupNavigation()

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
    onRefreshLibrary: () -> Unit,
    onClearMemoryCache: () -> Unit,
    onProviderSelected: (TvProviderConfig) -> Unit,
    onChooseProfile: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val strings = remember(locale) {
        TvStrings(locale)
    }

    var selectedSection by remember {
        mutableStateOf(TvSection.Home)
    }
    var anyFullscreen by remember {
        mutableStateOf(false)
    }
    var menuExpanded by remember {
        mutableStateOf(true)
    }
    var pendingSportsEventId by remember { mutableStateOf<String?>(null) }
    var sportsDetailActive by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    var settingsRoute by remember {
        mutableStateOf<String?>(null)
    }

    val liveMenuFocusRequester =
        remember {
            FocusRequester()
        }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { liveMenuFocusRequester.requestFocus() }
    }

    BackHandler(enabled = !anyFullscreen && settingsRoute == null && !sportsDetailActive) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Uygulamadan çıkılsın mı?") },
            text = { Text("StreamLiveX uygulamasını kapatmak istiyor musunuz?") },
            confirmButton = {
                TextButton(onClick = { activity?.finishAffinity() }) { Text("Çıkış Yap") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Vazgeç") }
            },
        )
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
                onMenuFocused = { menuExpanded = true },
                onSectionSelected = {
                    selectedSection = it
                    if (it != TvSection.Settings) {
                        settingsRoute = null
                    }
                },
                liveMenuFocusRequester =
                    liveMenuFocusRequester,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onFocusChanged { state ->
                        if (state.hasFocus) menuExpanded = false
                    }
                    // İçerik alanını bir odak grubu yap: içeride odaktaki bir öğe
                    // (buton/diyalog) kaybolunca odak sol menüye kaçmasın, içerik
                    // alanının içinde kalsın. Menüye ancak geri tuşu veya kumandayla
                    // sola giderek geçilir.
                    .focusGroup()
                    .background(TvDesignTokens.Background),
        ) {
            val fullscreenCallback:
                (Boolean) -> Unit = { active ->
                    anyFullscreen = active
                    onFullscreenStateChanged(active)
                }

            key(selectedSection) {
                when (selectedSection) {
                TvSection.Home ->
                    TvHomeScreen(
                        provider = provider,
                        locale = locale,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        onFullscreenStateChanged =
                            fullscreenCallback,
                        onOpenSportsEvent = { eventId ->
                            pendingSportsEventId = eventId
                            selectedSection = TvSection.Sports
                        },
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
                            menuExpanded = false
                        },
                        menuFocusRequester =
                            liveMenuFocusRequester,
                    )

                TvSection.Sports ->
                    TvSportsScreen(
                        provider = provider,
                        locale = locale,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        onFullscreenStateChanged = fullscreenCallback,
                        menuFocusRequester = liveMenuFocusRequester,
                        onContentFocused = { menuExpanded = false },
                        initialEventId = pendingSportsEventId,
                        onInitialEventConsumed = { pendingSportsEventId = null },
                        onDetailStateChanged = { sportsDetailActive = it },
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
                            menuExpanded = false
                        },
                        menuFocusRequester = liveMenuFocusRequester,
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
                            menuExpanded = false
                        },
                        menuFocusRequester = liveMenuFocusRequester,
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
                            menuExpanded = false
                        },
                    )

                TvSection.MyList ->
                    TvMyListScreen(
                        provider = provider,
                        locale = locale,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        onFullscreenStateChanged =
                            fullscreenCallback,
                        onContentFocused = {
                            menuExpanded = false
                        },
                    )

                TvSection.History ->
                    TvHistoryScreen(
                        provider = provider,
                        locale = locale,
                        playerFor = playerFor,
                        releasePlayer = releasePlayer,
                        onFullscreenStateChanged =
                            fullscreenCallback,
                        onContentFocused = {
                            menuExpanded = false
                        },
                    )

                TvSection.Settings -> {
                    when (settingsRoute) {
                        "playlists" ->
                            TvPlaylistManagerScreen(
                                onBack = {
                                    settingsRoute =
                                        null
                                },
                                onSelected = {
                                    next ->
                                    onProviderSelected(
                                        next,
                                    )
                                },
                            )

                        "profiles" ->
                            TvProfileManagerScreen(
                                onBack = {
                                    settingsRoute =
                                        null
                                },
                            )

                        "diagnostics" ->
                            TvDiagnosticsScreen(
                                provider =
                                    provider,
                                onBack = {
                                    settingsRoute =
                                        null
                                },
                            )

                        else ->
                            TvSettingsScreen(
                                provider = provider,
                                locale = locale,
                                strings = strings,
                                onLocaleChanged =
                                    onLocaleChanged,
                                onRefreshLibrary =
                                    onRefreshLibrary,
                                onClearMemoryCache =
                                    onClearMemoryCache,
                                onManagePlaylists = {
                                    settingsRoute =
                                        "playlists"
                                },
                                onManageProfiles = {
                                    settingsRoute =
                                        "profiles"
                                },
                                onDiagnostics = {
                                    settingsRoute =
                                        "diagnostics"
                                },
                                onHistory = { selectedSection = TvSection.History },
                                onChooseProfile =
                                    onChooseProfile,
                                onDisconnect =
                                    onDisconnect,
                            )
                    }
                }
            }
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

    // Genislik animasyonu her karede tum poster/grid agacini yeniden olcuyordu.
    // TV'de akici gecis icin yalniz renk/focus animasyonu kalsin.
    val menuWidth = if (expanded) TvDesignTokens.HomeSidebarWidth else TvDesignTokens.CollapsedSidebarWidth

    fun title(
        section: TvSection,
    ): String =
        when (section) {
            TvSection.Home -> strings["home"]
            TvSection.Live -> strings["live"]
            TvSection.Sports -> "Spor"
            TvSection.Movies -> strings["movies"]
            TvSection.Series -> strings["series"]
            TvSection.Search -> strings["search"]
            TvSection.MyList -> strings["my_list"]
            TvSection.History -> "İzleme Geçmişi"
            TvSection.Settings -> strings["settings"]
        }

    fun short(
        section: TvSection,
    ): String =
        when (section) {
            TvSection.Home -> "⌂"
            TvSection.Live -> "●"
            TvSection.Sports -> "⚽"
            TvSection.Movies -> "▶"
            TvSection.Series -> "▤"
            TvSection.Search -> "⌕"
            TvSection.MyList -> "★"
            TvSection.History -> "↺"
            TvSection.Settings -> "⚙"
        }

    Column(
        modifier =
            Modifier
                .width(menuWidth)
                .fillMaxHeight()
                .background(TvDesignTokens.Sidebar)
                .padding(
                    horizontal =
                        14.dp,
                    vertical = 14.dp,
                ),
        verticalArrangement =
            Arrangement.spacedBy(3.dp),
    ) {
        TvBrandHeader(
            compact = !expanded,
            stacked = false,
            modifier =
                Modifier.padding(
                    start = if (expanded) 4.dp else 0.dp,
                    bottom = 8.dp,
                ),
        )

        TvSection.entries.filterNot { it == TvSection.History }.forEach {
            section ->

            var focused by
                remember {
                    mutableStateOf(false)
                }

            val targetBackground =
                when {
                    focused ->
                        Color(0xFF0E7490)

                    section == selectedSection ->
                        Color(0xFF134E4A)

                    else ->
                        Color.Transparent
                }
            val background by
                animateColorAsState(
                    targetValue = targetBackground,
                    label = "tv-menu-item-color",
                )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            background,
                            RoundedCornerShape(9.dp),
                        )
                        .then(
                            if (section == selectedSection) {
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
                            horizontal = if (expanded) 11.dp else 4.dp,
                            vertical = 8.dp,
                        ),
                contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TvNavIcon(section, if (focused || section == selectedSection) Color.White else TvDesignTokens.TextSecondary)
                    if (expanded) {
                        Text(
                            text = title(section),
                            color = Color.White,
                            fontWeight = if (focused || section == selectedSection) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvNavIcon(section: TvSection, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val selected = color == Color.White
        val face = if (selected) Color(0xFFEAF9FF) else color
        val accent = if (selected) Color(0xFF35BDF5) else color.copy(alpha = .72f)
        val stroke = Stroke(width = 1.75.dp.toPx())
        val w = size.width
        val h = size.height
        when (section) {
            TvSection.Home -> {
                drawLine(face, Offset(w * .22f, h * .49f), Offset(w * .47f, h * .27f), strokeWidth = stroke.width)
                drawLine(face, Offset(w * .47f, h * .27f), Offset(w * .72f, h * .49f), strokeWidth = stroke.width)
                drawRect(face, Offset(w * .29f, h * .47f), androidx.compose.ui.geometry.Size(w * .36f, h * .28f), style = stroke)
            }
            TvSection.Live -> {
                drawRoundRect(face, Offset(w * .20f, h * .28f), androidx.compose.ui.geometry.Size(w * .55f, h * .40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f), style = stroke)
                drawCircle(accent, w * .055f, Offset(w * .66f, h * .36f))
                drawLine(accent, Offset(w * .31f, h * .27f), Offset(w * .22f, h * .17f), strokeWidth = stroke.width)
                drawLine(accent, Offset(w * .57f, h * .27f), Offset(w * .66f, h * .17f), strokeWidth = stroke.width)
                drawLine(face, Offset(w * .35f, h * .72f), Offset(w * .60f, h * .72f), strokeWidth = stroke.width)
            }
            TvSection.Sports -> {
                drawCircle(face, w * .23f, Offset(w * .47f, h * .48f), style = stroke)
                drawCircle(accent, w * .065f, Offset(w * .47f, h * .48f), style = stroke)
                drawLine(accent, Offset(w * .47f, h * .25f), Offset(w * .47f, h * .41f), strokeWidth = 1.dp.toPx())
                drawLine(accent, Offset(w * .27f, h * .43f), Offset(w * .41f, h * .47f), strokeWidth = 1.dp.toPx())
                drawLine(accent, Offset(w * .63f, h * .43f), Offset(w * .53f, h * .47f), strokeWidth = 1.dp.toPx())
            }
            TvSection.Movies -> {
                drawRoundRect(face, Offset(w * .20f, h * .34f), androidx.compose.ui.geometry.Size(w * .54f, h * .38f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .04f), style = stroke)
                drawLine(accent, Offset(w * .21f, h * .27f), Offset(w * .73f, h * .20f), strokeWidth = stroke.width)
                drawLine(accent, Offset(w * .34f, h * .25f), Offset(w * .29f, h * .18f), strokeWidth = 1.dp.toPx())
                drawLine(accent, Offset(w * .50f, h * .23f), Offset(w * .45f, h * .16f), strokeWidth = 1.dp.toPx())
                val play = Path().apply {
                    moveTo(w * .42f, h * .43f)
                    lineTo(w * .42f, h * .64f)
                    lineTo(w * .60f, h * .535f)
                    close()
                }
                drawPath(play, accent)
            }
            TvSection.Series -> {
                drawRoundRect(face, Offset(w * .22f, h * .22f), androidx.compose.ui.geometry.Size(w * .50f, h * .52f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f), style = stroke)
                drawLine(accent, Offset(w * .30f, h * .22f), Offset(w * .23f, h * .14f), strokeWidth = 1.dp.toPx())
                drawLine(accent, Offset(w * .63f, h * .22f), Offset(w * .70f, h * .14f), strokeWidth = 1.dp.toPx())
                drawLine(accent, Offset(w * .30f, h * .61f), Offset(w * .64f, h * .61f), strokeWidth = 1.dp.toPx())
                drawLine(face, Offset(w * .39f, h * .78f), Offset(w * .56f, h * .78f), strokeWidth = stroke.width)
            }
            TvSection.Search -> {
                drawCircle(face, w * .19f, Offset(w * .41f, h * .40f), style = stroke)
                drawLine(accent, Offset(w * .55f, h * .55f), Offset(w * .72f, h * .72f), strokeWidth = stroke.width)
            }
            TvSection.MyList -> drawRoundRect(face, Offset(w * .29f, h * .20f), androidx.compose.ui.geometry.Size(w * .38f, h * .55f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .04f), style = stroke)
            TvSection.Settings -> {
                drawCircle(face, w * .22f, center, style = stroke)
                drawCircle(accent, w * .07f, center, style = stroke)
            }
            TvSection.History -> {
                drawCircle(face, w * .21f, center, style = stroke)
                drawLine(accent, center, Offset(w * .50f, h * .36f), strokeWidth = stroke.width)
            }
        }
    }
}

@Composable
private fun TvBrandHeader(
    compact: Boolean = false,
    stacked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "StreamLiveX logo",
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "StreamLiveX",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "StreamLiveX logo",
            modifier = Modifier.size(if (compact) 34.dp else 38.dp),
        )

        if (!compact) {
            Column {
                Text(
                    text = "StreamLiveX",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                Text(
                    text = "SMART IPTV PLAYER",
                    color = Color(0xFF60A5FA),
                    style = MaterialTheme.typography.labelSmall,
                )
                }
            }
        }
    }

@Composable
private fun StartupNavigation() {
    Row(
        modifier =
            Modifier
                .background(
                    Color(0xCC111827),
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        listOf("⌂ Ana Sayfa", "TV Canlı TV", "▶ Filmler", "▤ Diziler")
            .forEachIndexed {
                index,
                label ->

                Text(
                    text = label,
                    color = if (index == 0) Color(0xFF22D3EE) else Color(0xFFCBD5E1),
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
    }
}

@Composable
private fun TvSettingsScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    strings: TvStrings,
    onLocaleChanged: (TvLocale) -> Unit,
    onRefreshLibrary: () -> Unit,
    onClearMemoryCache: () -> Unit,
    onManagePlaylists: () -> Unit,
    onManageProfiles: () -> Unit,
    onDiagnostics: () -> Unit,
    onHistory: () -> Unit,
    onChooseProfile: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context =
        LocalContext.current
    val playbackStore =
        remember {
            TvPlaybackSettingsStore(
                context,
            )
        }
    val contentStore =
        remember {
            TvContentStore(
                context,
            )
        }

    var playbackSettings by
        remember {
            mutableStateOf(
                playbackStore.load(),
            )
        }

    fun savePlayback(
        next: TvPlaybackSettings,
    ) {
        playbackSettings = next
        playbackStore.save(next)
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF0D111B),
                )
                .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                9.dp,
            ),
    ) {
        item {
            Text(
                strings["settings"],
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
            )
        }

        item {
            SettingsSectionTitle(
                "Hesap ve Profiller",
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                SettingsButton(
                    text =
                        "Oynatma Listeleri",
                ) {
                    onManagePlaylists()
                }

                SettingsButton(
                    text =
                        "Profilleri Yönet",
                ) {
                    onManageProfiles()
                }

                SettingsButton(
                    text =
                        "Profil Değiştir",
                ) {
                    onChooseProfile()
                }

                SettingsButton(
                    text =
                        "Tanılama",
                ) {
                    onDiagnostics()
                }

                SettingsButton(text = "İzleme Geçmişi") { onHistory() }
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color(
                                0xFF111827,
                            ),
                            RoundedCornerShape(
                                11.dp,
                            ),
                        )
                        .padding(14.dp),
                verticalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                Text(
                    provider.name,
                    color = Color.White,
                    fontWeight =
                        FontWeight.Bold,
                )
                Text(
                    provider.server,
                    color =
                        Color(
                            0xFF94A3B8,
                        ),
                )
            }
        }

        item {
            SettingsSectionTitle(
                strings["language"],
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                TvLocale.entries.forEach {
                    row ->

                    SettingsButton(
                        text =
                            row.displayName,
                        selected =
                            row == locale,
                    ) {
                        onLocaleChanged(
                            row,
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionTitle(
                "Player Görüntüsü",
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                SettingsButton(
                    text = "Fit",
                    selected =
                        playbackSettings
                            .fitMode ==
                            "fit",
                ) {
                    savePlayback(
                        playbackSettings
                            .copy(
                                fitMode =
                                    "fit",
                            ),
                    )
                }

                SettingsButton(
                    text = "Fill",
                    selected =
                        playbackSettings
                            .fitMode ==
                            "fill",
                ) {
                    savePlayback(
                        playbackSettings
                            .copy(
                                fitMode =
                                    "fill",
                            ),
                    )
                }
            }
        }

        item {
            SettingsSectionTitle(
                "Varsayılan Ses Dili",
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                listOf(
                    "auto" to "Otomatik",
                    "tr" to "TR",
                    "en" to "EN",
                    "ar" to "AR",
                    "de" to "DE",
                    "fr" to "FR",
                    "es" to "ES",
                ).forEach {
                    option ->

                    SettingsButton(
                        text =
                            option.second,
                        selected =
                            playbackSettings
                                .audioLanguage ==
                                option.first,
                    ) {
                        savePlayback(
                            playbackSettings
                                .copy(
                                    audioLanguage =
                                        option.first,
                                ),
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionTitle(
                "Altyazı",
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                SettingsButton(
                    text =
                        if (
                            playbackSettings
                                .subtitlesEnabled
                        ) {
                            "Açık"
                        } else {
                            "Kapalı"
                        },
                    selected =
                        playbackSettings
                            .subtitlesEnabled,
                ) {
                    savePlayback(
                        playbackSettings
                            .copy(
                                subtitlesEnabled =
                                    !playbackSettings
                                        .subtitlesEnabled,
                            ),
                    )
                }

                listOf(
                    "tr" to "TR",
                    "en" to "EN",
                    "ar" to "AR",
                    "de" to "DE",
                    "fr" to "FR",
                    "es" to "ES",
                ).forEach {
                    option ->

                    SettingsButton(
                        text =
                            option.second,
                        selected =
                            playbackSettings
                                .subtitleLanguage ==
                                option.first,
                    ) {
                        savePlayback(
                            playbackSettings
                                .copy(
                                    subtitlesEnabled =
                                        true,
                                    subtitleLanguage =
                                        option.first,
                                ),
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionTitle(
                "Dizi Oynatma",
            )
        }

        item {
            SettingsButton(
                text =
                    if (
                        playbackSettings
                            .autoNextEpisode
                    ) {
                        "Sonraki Bölüm: Otomatik"
                    } else {
                        "Sonraki Bölüm: Kapalı"
                    },
                selected =
                    playbackSettings
                        .autoNextEpisode,
            ) {
                savePlayback(
                    playbackSettings
                        .copy(
                            autoNextEpisode =
                                !playbackSettings
                                    .autoNextEpisode,
                        ),
                )
            }
        }

        item {
            SettingsSectionTitle(
                "Kütüphane ve Önbellek",
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                SettingsButton(
                    text =
                        "Oynatma Listesini Yenile",
                ) {
                    onRefreshLibrary()
                }

                SettingsButton(
                    text =
                        "Bellek Önbelleğini Temizle",
                ) {
                    onClearMemoryCache()
                }

                SettingsButton(
                    text =
                        "İzleme Geçmişini Temizle",
                ) {
                    contentStore
                        .clearViewingHistory()
                }
            }
        }

        item {
            SettingsButton(
                text =
                    strings[
                        "remove_playlist"
                    ],
                destructive = true,
            ) {
                onDisconnect()
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 18.dp)
                .background(Color(0xFF38BDF8), RoundedCornerShape(2.dp)),
        )
        Text(
            text.uppercase(),
            color = Color(0xFFDCE8F5),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
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
                Color(0xFF0E7490)

            selected ->
                Color(0xFF0F766E)

            else ->
                Color(0xFF1E293B)
        }

    Box(
        modifier =
            Modifier
                .background(
                    color,
                    RoundedCornerShape(8.dp),
                )
                .onFocusChanged {
                    focused = it.isFocused
                }
                .clickable(
                    onClick = onClick,
                )
                .padding(
                    horizontal = 13.dp,
                    vertical = 9.dp,
                ),
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
