@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamlivex.android.tv.content

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackPreferences
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.NativeSeriesEpisode
import com.streamlivex.android.tv.data.NativeSeriesInfo
import com.streamlivex.android.tv.data.NativeSeriesItem
import com.streamlivex.android.tv.data.NativeVodItem
import com.streamlivex.android.tv.data.TvContentCache
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvIndexedMedia
import com.streamlivex.android.tv.data.TvLibraryIndex
import com.streamlivex.android.tv.data.TvLiveLibraryCache
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.TvSavedItem
import com.streamlivex.android.tv.data.TvTmdbClient
import com.streamlivex.android.tv.data.TvTmdbDetail
import com.streamlivex.android.tv.data.TvTmdbEpisode
import com.streamlivex.android.tv.data.TvTmdbMedia
import com.streamlivex.android.tv.data.TvTmdbPerson
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.i18n.TvLocale
import com.streamlivex.android.tv.i18n.TvStrings
import com.streamlivex.android.tv.player.TvNativePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ContentTarget(
    val kind: String,
    val name: String,
    val tmdbId: Long? = null,
    val poster: String? = null,
    val local: TvIndexedMedia? = null,
    val vod: NativeVodItem? = null,
    val series: NativeSeriesItem? = null,
)

private data class HomeCard(
    val id: String,
    val title: String,
    val artwork: String?,
    val subtitle: String,
    val target: ContentTarget?,
)

@Composable
fun TvHomeScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val store = remember { TvContentStore(context) }
    val tmdb = remember { TvTmdbClient() }
    val index = remember { TvLibraryIndex(context) }

    var trendingMovies by remember(locale) { mutableStateOf<List<TvTmdbMedia>>(emptyList()) }
    var trendingSeries by remember(locale) { mutableStateOf<List<TvTmdbMedia>>(emptyList()) }
    var forYou by remember { mutableStateOf<List<TvIndexedMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf<ContentTarget?>(null) }
    var playing by remember { mutableStateOf<TvSavedItem?>(null) }
    var currentPlaylist by remember {
        mutableStateOf<List<Pair<TvSavedItem, PlaybackItem>>>(emptyList())
    }
    var playlistIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(provider.server, provider.username, locale) {
        Thread {
            val movies = tmdb.trending("movie", locale).getOrDefault(emptyList()).take(14)
            val series = tmdb.trending("series", locale).getOrDefault(emptyList()).take(14)
            val suggestions = index.suggestions(provider, limit = 14)

            Handler(Looper.getMainLooper()).post {
                trendingMovies = movies
                trendingSeries = series
                forYou = suggestions
                loading = false
            }
        }.start()
    }

    LaunchedEffect(provider.server, provider.username) {
        repeat(20) {
            if (forYou.isNotEmpty()) return@LaunchedEffect
            delay(1_500)
            val next = index.suggestions(provider, limit = 14)
            if (next.isNotEmpty()) {
                forYou = next
            }
        }
    }

    if (playing != null) {
        val saved = playing!!
        TvNativePlayer(
            saved = saved,
            request = PlaybackRequest(
                sessionId = "tv-home-${saved.id}",
                item = PlaybackItem(saved.name, saved.url, saved.kind),
                resumeTimeMs = store.progressFor(saved.id)?.positionMs ?: saved.positionMs,
                preferences = PlaybackPreferences(showInfo = true),
            ),
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            store = store,
            locale = locale,
            onFullscreenStateChanged = onFullscreenStateChanged,
            onClose = { playing = null },
            playlist = if (saved.kind == "episode") currentPlaylist else emptyList(),
            playlistStartIndex = if (saved.kind == "episode") playlistIndex else 0,
        )
        return
    }

    if (target != null) {
        GenericDetailScreen(
            provider = provider,
            locale = locale,
            target = target!!,
            store = store,
            index = index,
            onPlay = { saved ->
                currentPlaylist = emptyList()
                playlistIndex = 0
                playing = saved
            },
            onEpisodePlaylist = { playlist, indexPosition ->
                currentPlaylist = playlist
                playlistIndex = indexPosition
                playing = playlist.getOrNull(indexPosition)?.first
            },
            onOpen = { target = it },
            onBack = { target = null },
            onFullscreenStateChanged = onFullscreenStateChanged,
        )
        return
    }

    val continueRows = store.continueWatching()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                "StreamLiveX",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (continueRows.isNotEmpty()) {
            item {
                HomeRail(
                    title = strings["continue"],
                    cards = continueRows.take(12).map {
                        HomeCard(
                            id = it.id,
                            title = it.name,
                            artwork = it.artwork,
                            subtitle = it.subtitle.orEmpty(),
                            target = null,
                        )
                    },
                    onClick = { card ->
                        continueRows.firstOrNull { it.id == card.id }?.let { playing = it }
                    },
                )
            }
        }

        val localTrendingMovies =
            trendingMovies.mapNotNull { media ->
                val local =
                    index.findByTitle(
                        provider,
                        "movie",
                        media.name,
                    ) ?: return@mapNotNull null

                HomeCard(
                    id = "tmdb-m-${media.id}",
                    title = media.name,
                    artwork = media.poster ?: local.artwork,
                    subtitle = strings["in_playlist"],
                    target =
                        ContentTarget(
                            kind = "movie",
                            name = media.name,
                            tmdbId = media.id,
                            poster = media.poster ?: local.artwork,
                            local = local,
                        ),
                )
            }

        if (localTrendingMovies.isNotEmpty()) {
            item {
                HomeRail(
                    title = strings["trending_movies"],
                    cards = localTrendingMovies,
                    onClick = { card ->
                        card.target?.let {
                            target = it
                        }
                    },
                )
            }
        }

        val localTrendingSeries =
            trendingSeries.mapNotNull { media ->
                val local =
                    index.findByTitle(
                        provider,
                        "series",
                        media.name,
                    ) ?: return@mapNotNull null

                HomeCard(
                    id = "tmdb-s-${media.id}",
                    title = media.name,
                    artwork = media.poster ?: local.artwork,
                    subtitle = strings["in_playlist"],
                    target =
                        ContentTarget(
                            kind = "series",
                            name = media.name,
                            tmdbId = media.id,
                            poster = media.poster ?: local.artwork,
                            local = local,
                        ),
                )
            }

        if (localTrendingSeries.isNotEmpty()) {
            item {
                HomeRail(
                    title = strings["trending_series"],
                    cards = localTrendingSeries,
                    onClick = { card ->
                        card.target?.let {
                            target = it
                        }
                    },
                )
            }
        }

        item {
            HomeRail(
                title = strings["for_you"],
                cards = forYou.map {
                    HomeCard(
                        id = it.localId,
                        title = it.name,
                        artwork = it.artwork,
                        subtitle = availabilityText(strings, true),
                        target = ContentTarget(
                            kind = it.kind,
                            name = it.name,
                            poster = it.artwork,
                            local = it,
                        ),
                    )
                },
                onClick = { card -> card.target?.let { target = it } },
            )
        }

        if (loading) {
            item { Text(strings["loading"], color = Color(0xFF94A3B8)) }
        } else if (forYou.isEmpty()) {
            item { Text(strings["library_preparing"], color = Color(0xFF64748B)) }
        }
    }
}

@Composable
fun TvMoviesScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }
    val index = remember { TvLibraryIndex(context) }
    val restoreRequester = remember { FocusRequester() }
    val movieGridState = rememberLazyListState()

    var categories by remember { mutableStateOf(TvContentCache.vodCategories.orEmpty()) }
    var selectedCategoryId by remember { mutableStateOf(TvContentCache.movieCategoryId) }
    var movies by remember { mutableStateOf<List<TvIndexedMedia>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var loadingCategories by remember { mutableStateOf(categories.isEmpty()) }
    var loadingPage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loadGeneration by remember { mutableIntStateOf(0) }

    var target by remember { mutableStateOf<ContentTarget?>(null) }
    var playing by remember { mutableStateOf<TvSavedItem?>(null) }

    val pageSize = 120

    fun loadPage(
        categoryId: String,
        reset: Boolean,
    ) {
        if (loadingPage) return

        if (reset) {
            loadGeneration += 1
            movies = emptyList()
            totalCount = 0
        }

        val generation = loadGeneration
        val offset = if (reset) 0 else movies.size
        loadingPage = true
        error = ""

        Thread {
            val count =
                index.categoryCount(
                    provider = provider,
                    kind = "movie",
                    categoryId = categoryId,
                )
            val rows =
                index.categoryPage(
                    provider = provider,
                    kind = "movie",
                    categoryId = categoryId,
                    limit = pageSize,
                    offset = offset,
                )

            Handler(Looper.getMainLooper()).post {
                if (
                    selectedCategoryId == categoryId &&
                    loadGeneration == generation
                ) {
                    totalCount = count
                    movies =
                        if (reset) {
                            rows
                        } else {
                            (movies + rows).distinctBy { it.localId }
                        }
                    loadingPage = false
                }
            }
        }.start()
    }

    fun selectCategory(categoryId: String) {
        TvContentCache.movieCategoryId = categoryId
        selectedCategoryId = categoryId
        loadPage(categoryId, reset = true)
    }

    LaunchedEffect(provider.server, provider.username) {
        if (categories.isEmpty()) {
            val diskCategories =
                index.loadVodCategories(provider)
            if (diskCategories.isNotEmpty()) {
                categories = diskCategories
                TvContentCache.vodCategories =
                    diskCategories
            }
        }

        if (categories.isNotEmpty()) {
            val targetId =
                selectedCategoryId
                    ?: categories.firstOrNull()?.id
            if (targetId != null) {
                selectedCategoryId = targetId
                loadPage(targetId, reset = true)
            }
            loadingCategories = false
            return@LaunchedEffect
        }

        Thread {
            val result = client.loadVodCategories(provider)
            Handler(Looper.getMainLooper()).post {
                result
                    .onSuccess { rows ->
                        categories = rows
                        loadingCategories = false
                        val targetId =
                            TvContentCache.movieCategoryId
                                ?: rows.firstOrNull()?.id
                        if (targetId != null) {
                            selectCategory(targetId)
                        }
                    }
                    .onFailure {
                        loadingCategories = false
                        error =
                            it.message
                                ?: "Film kategorileri alınamadı."
                    }
            }
        }.start()
    }

    if (playing != null) {
        val saved = playing!!
        TvNativePlayer(
            saved = saved,
            request =
                PlaybackRequest(
                    sessionId = "tv-movie-${saved.id}",
                    item =
                        PlaybackItem(
                            saved.name,
                            saved.url,
                            "movie",
                        ),
                    resumeTimeMs =
                        store.progressFor(saved.id)?.positionMs
                            ?: 0L,
                    preferences =
                        PlaybackPreferences(
                            showInfo = true,
                        ),
                ),
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            store = store,
            locale = locale,
            onFullscreenStateChanged =
                onFullscreenStateChanged,
            onClose = {
                playing = null
            },
        )
        return
    }

    if (target != null) {
        GenericDetailScreen(
            provider = provider,
            locale = locale,
            target = target!!,
            store = store,
            index = index,
            onPlay = { saved ->
                playing = saved
            },
            onOpen = {
                target = it
            },
            onBack = {
                target = null
            },
            onFullscreenStateChanged =
                onFullscreenStateChanged,
        )
        return
    }

    LaunchedEffect(target, playing) {
        if (
            target == null &&
            playing == null &&
            TvContentCache.movieFocusedId != null
        ) {
            delay(120)
            runCatching {
                restoreRequester.requestFocus()
            }
        }
    }

    if (loadingCategories && categories.isEmpty()) {
        LoadingState(strings["loading"])
        return
    }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D111B)),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF101722))
                    .padding(10.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp),
        ) {
            items(
                categories,
                key = { it.id },
            ) { category ->
                FocusRow(
                    title = category.name,
                    selected =
                        category.id ==
                            selectedCategoryId,
                    onFocus = {
                        onContentFocused()
                    },
                    onClick = {
                        onContentFocused()
                        if (
                            category.id !=
                            selectedCategoryId
                        ) {
                            selectCategory(
                                category.id,
                            )
                        }
                    },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            when {
                error.isNotBlank() ->
                    ErrorState(error)

                loadingPage &&
                    movies.isEmpty() ->
                    LoadingState(
                        strings["loading"],
                    )

                else ->
                    IndexedPosterGrid(
                        rows = movies,
                        restoreRequester =
                            restoreRequester,
                        restoreId =
                            TvContentCache
                                .movieFocusedId,
                        listState =
                            movieGridState,
                        totalCount =
                            totalCount,
                        loadingMore =
                            loadingPage,
                        onNeedMore = {
                            val categoryId =
                                selectedCategoryId

                            if (
                                categoryId != null &&
                                movies.size <
                                totalCount
                            ) {
                                loadPage(
                                    categoryId,
                                    reset = false,
                                )
                            }
                        },
                        onFocus = { movie ->
                            TvContentCache
                                .movieFocusedId =
                                movie.localId
                            onContentFocused()
                        },
                        onClick = { movie ->
                            target =
                                ContentTarget(
                                    kind = "movie",
                                    name = movie.name,
                                    poster =
                                        movie.artwork,
                                    local = movie,
                                )
                        },
                    )
            }
        }
    }
}

@Composable
fun TvSeriesScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }
    val index = remember { TvLibraryIndex(context) }
    val restoreRequester = remember { FocusRequester() }
    val seriesGridState = rememberLazyListState()

    var categories by remember { mutableStateOf(TvContentCache.seriesCategories.orEmpty()) }
    var selectedCategoryId by remember { mutableStateOf(TvContentCache.seriesCategoryId) }
    var shows by remember { mutableStateOf<List<TvIndexedMedia>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var loadingCategories by remember { mutableStateOf(categories.isEmpty()) }
    var loadingPage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loadGeneration by remember { mutableIntStateOf(0) }

    var target by remember { mutableStateOf<ContentTarget?>(null) }
    var playingEpisode by remember { mutableStateOf<TvSavedItem?>(null) }
    var currentPlaylist by remember {
        mutableStateOf<List<Pair<TvSavedItem, PlaybackItem>>>(emptyList())
    }
    var playlistIndex by remember { mutableIntStateOf(0) }

    val pageSize = 120

    fun loadPage(
        categoryId: String,
        reset: Boolean,
    ) {
        if (loadingPage) return

        if (reset) {
            loadGeneration += 1
            shows = emptyList()
            totalCount = 0
        }

        val generation = loadGeneration
        val offset = if (reset) 0 else shows.size
        loadingPage = true
        error = ""

        Thread {
            val count =
                index.categoryCount(
                    provider = provider,
                    kind = "series",
                    categoryId = categoryId,
                )
            val rows =
                index.categoryPage(
                    provider = provider,
                    kind = "series",
                    categoryId = categoryId,
                    limit = pageSize,
                    offset = offset,
                )

            Handler(Looper.getMainLooper()).post {
                if (
                    selectedCategoryId == categoryId &&
                    loadGeneration == generation
                ) {
                    totalCount = count
                    shows =
                        if (reset) {
                            rows
                        } else {
                            (shows + rows)
                                .distinctBy {
                                    it.localId
                                }
                        }
                    loadingPage = false
                }
            }
        }.start()
    }

    fun selectCategory(categoryId: String) {
        TvContentCache.seriesCategoryId =
            categoryId
        selectedCategoryId = categoryId
        loadPage(
            categoryId,
            reset = true,
        )
    }

    LaunchedEffect(
        provider.server,
        provider.username,
    ) {
        if (categories.isEmpty()) {
            val diskCategories =
                index.loadSeriesCategories(
                    provider,
                )
            if (diskCategories.isNotEmpty()) {
                categories =
                    diskCategories
                TvContentCache
                    .seriesCategories =
                    diskCategories
            }
        }

        if (categories.isNotEmpty()) {
            val targetId =
                selectedCategoryId
                    ?: categories.firstOrNull()?.id
            if (targetId != null) {
                selectedCategoryId = targetId
                loadPage(
                    targetId,
                    reset = true,
                )
            }
            loadingCategories = false
            return@LaunchedEffect
        }

        Thread {
            val result =
                client.loadSeriesCategories(
                    provider,
                )

            Handler(Looper.getMainLooper()).post {
                result
                    .onSuccess { rows ->
                        categories = rows
                        loadingCategories = false

                        val targetId =
                            TvContentCache
                                .seriesCategoryId
                                ?: rows
                                    .firstOrNull()
                                    ?.id

                        if (targetId != null) {
                            selectCategory(
                                targetId,
                            )
                        }
                    }
                    .onFailure {
                        loadingCategories = false
                        error =
                            it.message
                                ?: "Dizi kategorileri alınamadı."
                    }
            }
        }.start()
    }

    if (playingEpisode != null) {
        val saved = playingEpisode!!

        TvNativePlayer(
            saved = saved,
            request =
                PlaybackRequest(
                    sessionId =
                        "tv-series-player-${
                            target
                                ?.local
                                ?.seriesId
                                ?: saved.id
                        }",
                    item =
                        PlaybackItem(
                            saved.name,
                            saved.url,
                            "episode",
                        ),
                    resumeTimeMs =
                        store.progressFor(
                            saved.id,
                        )?.positionMs
                            ?: 0L,
                    preferences =
                        PlaybackPreferences(
                            showInfo = true,
                        ),
                ),
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            store = store,
            locale = locale,
            onFullscreenStateChanged =
                onFullscreenStateChanged,
            onClose = {
                playingEpisode = null
            },
            playlist =
                currentPlaylist,
            playlistStartIndex =
                playlistIndex,
        )
        return
    }

    if (target != null) {
        GenericDetailScreen(
            provider = provider,
            locale = locale,
            target = target!!,
            store = store,
            index = index,
            onPlay = { saved ->
                playingEpisode = saved
            },
            onEpisodePlaylist = {
                playlist,
                indexPosition ->

                currentPlaylist = playlist
                playlistIndex = indexPosition
                playingEpisode =
                    playlist
                        .getOrNull(
                            indexPosition,
                        )
                        ?.first
            },
            onOpen = {
                target = it
            },
            onBack = {
                target = null
            },
            onFullscreenStateChanged =
                onFullscreenStateChanged,
        )
        return
    }

    LaunchedEffect(
        target,
        playingEpisode,
    ) {
        if (
            target == null &&
            playingEpisode == null &&
            TvContentCache.seriesFocusedId != null
        ) {
            delay(120)
            runCatching {
                restoreRequester.requestFocus()
            }
        }
    }

    if (
        loadingCategories &&
        categories.isEmpty()
    ) {
        LoadingState(
            strings["loading"],
        )
        return
    }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D111B)),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF101722))
                    .padding(10.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp),
        ) {
            items(
                categories,
                key = { it.id },
            ) { category ->
                FocusRow(
                    title =
                        category.name,
                    selected =
                        category.id ==
                            selectedCategoryId,
                    onFocus = {
                        onContentFocused()
                    },
                    onClick = {
                        onContentFocused()

                        if (
                            category.id !=
                            selectedCategoryId
                        ) {
                            selectCategory(
                                category.id,
                            )
                        }
                    },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            when {
                error.isNotBlank() ->
                    ErrorState(error)

                loadingPage &&
                    shows.isEmpty() ->
                    LoadingState(
                        strings["loading"],
                    )

                else ->
                    IndexedPosterGrid(
                        rows = shows,
                        restoreRequester =
                            restoreRequester,
                        restoreId =
                            TvContentCache
                                .seriesFocusedId,
                        listState =
                            seriesGridState,
                        totalCount =
                            totalCount,
                        loadingMore =
                            loadingPage,
                        onNeedMore = {
                            val categoryId =
                                selectedCategoryId

                            if (
                                categoryId != null &&
                                shows.size <
                                totalCount
                            ) {
                                loadPage(
                                    categoryId,
                                    reset = false,
                                )
                            }
                        },
                        onFocus = { show ->
                            TvContentCache
                                .seriesFocusedId =
                                show.localId
                            onContentFocused()
                        },
                        onClick = { show ->
                            target =
                                ContentTarget(
                                    kind = "series",
                                    name = show.name,
                                    poster =
                                        show.artwork,
                                    local = show,
                                )
                        },
                    )
            }
        }
    }
}

@Composable
private fun GenericDetailScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    target: ContentTarget,
    store: TvContentStore,
    index: TvLibraryIndex,
    onPlay: (TvSavedItem) -> Unit,
    onEpisodePlaylist: (List<Pair<TvSavedItem, PlaybackItem>>, Int) -> Unit = { _, _ -> },
    onOpen: (ContentTarget) -> Unit,
    onBack: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val tmdb = remember { TvTmdbClient() }
    val xtream = remember { XtreamClient() }
    val detailListState = rememberLazyListState()
    val primaryFocusRequester = remember { FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var detail by remember(target.name, target.tmdbId, locale) {
        mutableStateOf<TvTmdbDetail?>(null)
    }
    var loading by remember(target.name, locale) {
        mutableStateOf(true)
    }
    var person by remember(target.name, target.tmdbId) {
        mutableStateOf<Pair<TvTmdbPerson, List<TvTmdbMedia>>?>(null)
    }
    var seriesInfo by remember(
        target.local?.seriesId,
        target.series?.seriesId,
    ) {
        mutableStateOf<NativeSeriesInfo?>(null)
    }
    var selectedSeason by remember {
        mutableIntStateOf(0)
    }
    var tmdbEpisodes by remember {
        mutableStateOf<List<TvTmdbEpisode>>(emptyList())
    }
    var seasonLoading by remember {
        mutableStateOf(false)
    }
    var favoriteRefresh by remember {
        mutableIntStateOf(0)
    }

    DisposableEffect(Unit) {
        onFullscreenStateChanged(true)
        onDispose {
            onFullscreenStateChanged(false)
        }
    }

    BackHandler {
        if (person != null) {
            person = null
        } else {
            onBack()
        }
    }

    LaunchedEffect(target.name, target.local?.localId) {
        delay(180)
        runCatching {
            primaryFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(
        target.name,
        target.tmdbId,
        target.kind,
        locale,
    ) {
        loading = true

        Thread {
            val result =
                tmdb.detail(
                    name = target.name,
                    kind = target.kind,
                    locale = locale,
                    tmdbId = target.tmdbId,
                ).getOrNull()

            Handler(Looper.getMainLooper()).post {
                detail = result
                loading = false
            }
        }.start()
    }

    LaunchedEffect(
        target.local?.seriesId,
        target.series?.seriesId,
    ) {
        if (target.kind != "series") {
            return@LaunchedEffect
        }

        val seriesId =
            target.series?.seriesId
                ?: target.local?.seriesId
                ?: return@LaunchedEffect

        val seriesItem =
            target.series
                ?: NativeSeriesItem(
                    id =
                        target.local?.localId
                            ?: "s$seriesId",
                    seriesId = seriesId,
                    categoryId =
                        target.local
                            ?.categoryId
                            .orEmpty(),
                    name = target.name,
                    cover =
                        target.poster
                            ?: target.local
                                ?.artwork,
                    plot = null,
                    rating = null,
                    year = null,
                    genre = null,
                )

        Thread {
            val info =
                xtream
                    .loadSeriesInfo(
                        provider,
                        seriesItem,
                    )
                    .getOrNull()

            Handler(Looper.getMainLooper()).post {
                seriesInfo = info
                selectedSeason =
                    info
                        ?.episodes
                        ?.minOfOrNull {
                            it.season
                        }
                        ?: 0
            }
        }.start()
    }

    LaunchedEffect(
        selectedSeason,
        detail?.media?.id,
        target.name,
        locale,
    ) {
        if (
            target.kind != "series" ||
            selectedSeason <= 0
        ) {
            return@LaunchedEffect
        }

        seasonLoading = true

        Thread {
            val rows =
                tmdb.season(
                    title = target.name,
                    season = selectedSeason,
                    locale = locale,
                    tmdbId =
                        detail
                            ?.media
                            ?.id
                            ?.takeIf {
                                it > 0L
                            },
                ).getOrDefault(
                    emptyList(),
                )

            Handler(Looper.getMainLooper()).post {
                tmdbEpisodes = rows
                seasonLoading = false
            }
        }.start()
    }

    if (person != null) {
        PersonFilmographyScreen(
            locale = locale,
            person = person!!.first,
            rows = person!!.second,
            provider = provider,
            index = index,
            onBack = {
                person = null
            },
            onOpen = onOpen,
        )
        return
    }

    val media = detail?.media
    val local =
        target.local
            ?: index.findByTitle(
                provider,
                target.kind,
                target.name,
            )

    val displayPoster =
        media?.poster
            ?: target.poster
            ?: local?.artwork

    val description =
        media?.overview
            ?: target.vod?.plot
            ?: target.series?.plot
            ?: strings["no_description"]

    val favoriteId =
        local?.localId
            ?: "tmdb-${target.kind}-${media?.id ?: target.name}"

    val favorite =
        remember(
            favoriteId,
            favoriteRefresh,
        ) {
            store.isFavorite(
                favoriteId,
            )
        }

    LazyColumn(
        state = detailListState,
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D111B))
                .padding(26.dp),
        verticalArrangement =
            Arrangement.spacedBy(22.dp),
    ) {
        item(
            key = "detail-main",
        ) {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(26.dp),
            ) {
                TvPosterImage(
                    displayPoster,
                    modifier =
                        Modifier
                            .width(250.dp)
                            .aspectRatio(2f / 3f),
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        media?.name
                            ?: target.name,
                        color = Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme
                                .typography
                                .headlineLarge,
                    )

                    Text(
                        listOfNotNull(
                            media?.year
                                ?: target.vod?.year
                                ?: target.series?.year,
                            media
                                ?.rating
                                ?.let {
                                    "★ %.1f".format(
                                        it,
                                    )
                                },
                            availabilityText(
                                strings,
                                local != null,
                            ),
                        ).joinToString(" • "),
                        color =
                            Color(0xFF94A3B8),
                    )

                    Text(
                        description,
                        color =
                            Color(0xFFCBD5E1),
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        maxLines = 7,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp),
                    ) {
                        if (
                            target.kind ==
                            "movie"
                        ) {
                            if (
                                local
                                    ?.streamUrl
                                    ?.isNotBlank()
                                    == true
                            ) {
                                ActionButton(
                                    text =
                                        "▶ ${strings["play"]}",
                                    modifier =
                                        Modifier
                                            .focusRequester(
                                                primaryFocusRequester,
                                            ),
                                ) {
                                    onPlay(
                                        TvSavedItem(
                                            id =
                                                local.localId,
                                            kind =
                                                "movie",
                                            name =
                                                local.name,
                                            url =
                                                local.streamUrl,
                                            artwork =
                                                displayPoster,
                                            subtitle =
                                                media?.year,
                                        ),
                                    )
                                }
                            } else {
                                ActionButton(
                                    text =
                                        strings[
                                            "not_in_playlist"
                                        ],
                                    modifier =
                                        Modifier
                                            .focusRequester(
                                                primaryFocusRequester,
                                            ),
                                ) {}
                            }
                        } else {
                            ActionButton(
                                text =
                                    "▶ ${strings["seasons_episodes"]}",
                                modifier =
                                    Modifier
                                        .focusRequester(
                                            primaryFocusRequester,
                                        ),
                            ) {
                                scope.launch {
                                    detailListState
                                        .animateScrollToItem(
                                            1,
                                        )
                                }
                            }
                        }

                        ActionButton(
                            text =
                                if (favorite) {
                                    "★ ${strings["remove_list"]}"
                                } else {
                                    "☆ ${strings["add_list"]}"
                                },
                            selected = favorite,
                        ) {
                            store.toggleFavorite(
                                TvSavedItem(
                                    id = favoriteId,
                                    kind =
                                        target.kind,
                                    name =
                                        media?.name
                                            ?: target.name,
                                    url =
                                        local
                                            ?.streamUrl
                                            .orEmpty(),
                                    artwork =
                                        displayPoster,
                                    subtitle =
                                        media?.year,
                                ),
                            )
                            favoriteRefresh += 1
                        }

                        detail
                            ?.trailerUrl
                            ?.let { trailerUrl ->
                                ActionButton(
                                    text = "▶ Fragman",
                                ) {
                                    runCatching {
                                        val intent =
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    trailerUrl,
                                                ),
                                            ).apply {
                                                addFlags(
                                                    Intent.FLAG_ACTIVITY_NEW_TASK,
                                                )
                                            }
                                        context.startActivity(
                                            intent,
                                        )
                                    }
                                }
                            }
                    }

                    PeopleLine(
                        title =
                            strings["director"],
                        rows =
                            detail
                                ?.directors
                                .orEmpty(),
                        onClick = { selected ->
                            Thread {
                                val rows =
                                    tmdb
                                        .person(
                                            selected.id,
                                            locale,
                                        )
                                        .getOrDefault(
                                            emptyList(),
                                        )

                                Handler(
                                    Looper
                                        .getMainLooper(),
                                ).post {
                                    person =
                                        selected to rows
                                }
                            }.start()
                        },
                    )

                    PeopleLine(
                        title =
                            strings["cast"],
                        rows =
                            detail
                                ?.cast
                                .orEmpty(),
                        onClick = { selected ->
                            Thread {
                                val rows =
                                    tmdb
                                        .person(
                                            selected.id,
                                            locale,
                                        )
                                        .getOrDefault(
                                            emptyList(),
                                        )

                                Handler(
                                    Looper
                                        .getMainLooper(),
                                ).post {
                                    person =
                                        selected to rows
                                }
                            }.start()
                        },
                    )

                    if (loading) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        if (target.kind == "series") {
            item(
                key = "episodes",
            ) {
                SeriesEpisodesBlock(
                    locale = locale,
                    series = seriesInfo,
                    selectedSeason =
                        selectedSeason,
                    onSeason = {
                        selectedSeason = it
                    },
                    tmdbEpisodes =
                        tmdbEpisodes,
                    loading =
                        seasonLoading,
                    artwork =
                        displayPoster,
                    onPlayEpisode = {
                        episode,
                        episodes ->

                        val playlist =
                            episodes.map {
                                ep ->

                                val saved =
                                    TvSavedItem(
                                        id =
                                            "series-${seriesInfo?.series?.seriesId}-${ep.episodeId}",
                                        kind =
                                            "episode",
                                        name =
                                            "${seriesInfo?.series?.name ?: target.name} • S${ep.season} E${ep.episode}",
                                        url =
                                            ep.streamUrl,
                                        artwork =
                                            displayPoster,
                                        subtitle =
                                            target.name,
                                    )

                                saved to
                                    PlaybackItem(
                                        name =
                                            saved.name,
                                        url =
                                            ep.streamUrl,
                                        kind =
                                            "episode",
                                        hasNext =
                                            ep !=
                                            episodes
                                                .lastOrNull(),
                                    )
                            }

                        val selectedIndex =
                            episodes
                                .indexOfFirst {
                                    it.id ==
                                        episode.id
                                }
                                .coerceAtLeast(
                                    0,
                                )

                        onEpisodePlaylist(
                            playlist,
                            selectedIndex,
                        )
                    },
                )
            }
        }

        val recommendations =
            detail
                ?.recommendations
                .orEmpty()

        if (recommendations.isNotEmpty()) {
            item(
                key = "recommendations",
            ) {
                TmdbRail(
                    locale = locale,
                    title =
                        if (
                            target.kind ==
                            "series"
                        ) {
                            strings[
                                "similar_series"
                            ]
                        } else {
                            strings[
                                "similar_movies"
                            ]
                        },
                    rows =
                        recommendations,
                    provider =
                        provider,
                    index =
                        index,
                    onOpen =
                        onOpen,
                )
            }
        }
    }
}

@Composable
private fun PersonFilmographyScreen(
    locale: TvLocale,
    person: TvTmdbPerson,
    rows: List<TvTmdbMedia>,
    provider: TvProviderConfig,
    index: TvLibraryIndex,
    onBack: () -> Unit,
    onOpen: (ContentTarget) -> Unit,
) {
    val strings = remember(locale) { TvStrings(locale) }
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "← ${strings["back"]}",
            color = Color(0xFF60A5FA),
            modifier = Modifier.clickable { onBack() }.padding(8.dp),
        )
        Text(
            "${person.name} · ${strings["people_movies"]}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )

        val chunks = rows.take(60).chunked(5)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(chunks) { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    chunk.forEach { media ->
                        val local = index.findByTitle(provider, media.kind, media.name)
                        MediaCard(
                            title = media.name,
                            artwork = media.poster,
                            subtitle = availabilityText(strings, local != null),
                            modifier = Modifier.weight(1f),
                        ) {
                            onOpen(
                                ContentTarget(
                                    kind = media.kind,
                                    name = media.name,
                                    tmdbId = media.id,
                                    poster = media.poster,
                                    local = local,
                                ),
                            )
                        }
                    }
                    repeat(5 - chunk.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SeriesEpisodesBlock(
    locale: TvLocale,
    series: NativeSeriesInfo?,
    selectedSeason: Int,
    onSeason: (Int) -> Unit,
    tmdbEpisodes: List<TvTmdbEpisode>,
    loading: Boolean,
    artwork: String?,
    onPlayEpisode: (NativeSeriesEpisode, List<NativeSeriesEpisode>) -> Unit,
) {
    val strings = remember(locale) { TvStrings(locale) }
    val episodes = series?.episodes.orEmpty()
    val seasons = episodes.map { it.season }.distinct().sorted()
    val seasonEpisodes = episodes.filter { it.season == selectedSeason }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            strings["seasons_episodes"],
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(seasons) { season ->
                ActionButton(
                    "${strings["season"]} $season",
                    selected = season == selectedSeason,
                ) {
                    onSeason(season)
                }
            }
        }

        if (loading) Text(strings["loading"], color = Color(0xFF94A3B8))

        seasonEpisodes.forEach { episode ->
            val tmdb = tmdbEpisodes.firstOrNull { it.episodeNumber == episode.episode }
            EpisodeCard(
                title = tmdb?.name ?: episode.name,
                subtitle = buildString {
                    append("${strings["episode"]} ${episode.episode}")
                    tmdb?.airDate?.let { append(" • $it") }
                    tmdb?.runtime?.let { append(" • $it dk") }
                },
                description = tmdb?.overview,
                image = tmdb?.still ?: artwork,
                onClick = { onPlayEpisode(episode, seasonEpisodes) },
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    title: String,
    subtitle: String,
    description: String?,
    image: String?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused) Color(0xFF1D4ED8) else Color(0xFF151C28),
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TvPosterImage(
            image,
            modifier = Modifier.width(210.dp).aspectRatio(16f / 9f),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF94A3B8))
            Text(
                description.orEmpty(),
                color = Color(0xFFCBD5E1),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PeopleLine(
    title: String,
    rows: List<TvTmdbPerson>,
    onClick: (TvTmdbPerson) -> Unit,
) {
    if (rows.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { person ->
                ActionButton(person.name) { onClick(person) }
            }
        }
    }
}

@Composable
private fun TmdbRail(
    locale: TvLocale,
    title: String,
    rows: List<TvTmdbMedia>,
    provider: TvProviderConfig,
    index: TvLibraryIndex,
    onOpen: (ContentTarget) -> Unit,
) {
    val strings = remember(locale) { TvStrings(locale) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = { "${it.kind}-${it.id}" }) { media ->
                val local = index.findByTitle(provider, media.kind, media.name)
                MediaCard(
                    title = media.name,
                    artwork = media.poster,
                    subtitle = availabilityText(strings, local != null),
                    modifier = Modifier.width(170.dp),
                ) {
                    onOpen(
                        ContentTarget(
                            kind = media.kind,
                            name = media.name,
                            tmdbId = media.id,
                            poster = media.poster,
                            local = local,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun TvSearchScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val index = remember { TvLibraryIndex(context) }
    val store = remember { TvContentStore(context) }

    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("movie") }
    var results by remember { mutableStateOf<List<TvIndexedMedia>>(emptyList()) }
    var channelResults by remember {
        mutableStateOf(
            emptyList<com.streamlivex.android.tv.data.NativeLiveChannel>(),
        )
    }
    var target by remember { mutableStateOf<ContentTarget?>(null) }
    var playing by remember { mutableStateOf<TvSavedItem?>(null) }
    var liveChannel by remember {
        mutableStateOf<com.streamlivex.android.tv.data.NativeLiveChannel?>(null)
    }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(
        selectedType,
        provider.server,
        provider.username,
    ) {
        if (
            selectedType == "channel" &&
            TvLiveLibraryCache.library == null
        ) {
            loading = true
            Thread {
                val result =
                    XtreamClient()
                        .loadLiveLibrary(
                            provider,
                        )
                Handler(
                    Looper.getMainLooper(),
                ).post {
                    loading = false
                    if (
                        result.isSuccess &&
                        query.trim().length >= 2
                    ) {
                        val q =
                            query.trim()
                        channelResults =
                            TvLiveLibraryCache
                                .library
                                ?.channels
                                .orEmpty()
                                .asSequence()
                                .filter {
                                    it.name.contains(
                                        q,
                                        ignoreCase = true,
                                    )
                                }
                                .take(60)
                                .toList()
                    }
                }
            }.start()
        }
    }

    LaunchedEffect(
        query,
        selectedType,
        provider.server,
        provider.username,
    ) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            channelResults = emptyList()
            return@LaunchedEffect
        }

        loading = true
        delay(180)

        if (selectedType == "channel") {
            val live =
                TvLiveLibraryCache
                    .library
                    ?.channels
                    .orEmpty()

            channelResults =
                live
                    .asSequence()
                    .filter {
                        it.name.contains(
                            q,
                            ignoreCase = true,
                        )
                    }
                    .take(60)
                    .toList()

            results = emptyList()
            loading = false
        } else {
            Thread {
                val rows =
                    index.search(
                        provider = provider,
                        query = q,
                        kind = selectedType,
                        limit = 60,
                    )

                Handler(
                    Looper.getMainLooper(),
                ).post {
                    results = rows
                    channelResults =
                        emptyList()
                    loading = false
                }
            }.start()
        }
    }

    if (liveChannel != null) {
        SearchLivePlayer(
            channel = liveChannel!!,
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            onFullscreenStateChanged =
                onFullscreenStateChanged,
            onClose = {
                liveChannel = null
            },
        )
        return
    }

    if (playing != null) {
        val saved = playing!!
        TvNativePlayer(
            saved = saved,
            request =
                PlaybackRequest(
                    sessionId =
                        "tv-search-${saved.id}",
                    item =
                        PlaybackItem(
                            saved.name,
                            saved.url,
                            saved.kind,
                        ),
                    resumeTimeMs =
                        store
                            .progressFor(
                                saved.id,
                            )
                            ?.positionMs
                            ?: 0L,
                    preferences =
                        PlaybackPreferences(
                            showInfo = true,
                        ),
                ),
            playerFor = playerFor,
            releasePlayer =
                releasePlayer,
            store = store,
            locale = locale,
            onFullscreenStateChanged =
                onFullscreenStateChanged,
            onClose = {
                playing = null
            },
        )
        return
    }

    if (target != null) {
        GenericDetailScreen(
            provider = provider,
            locale = locale,
            target = target!!,
            store = store,
            index = index,
            onPlay = {
                playing = it
            },
            onOpen = {
                target = it
            },
            onBack = {
                target = null
            },
            onFullscreenStateChanged =
                onFullscreenStateChanged,
        )
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF0D111B),
                )
                .padding(24.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp),
    ) {
        Text(
            strings["search"],
            color = Color.White,
            fontWeight =
                FontWeight.Bold,
            style =
                MaterialTheme
                    .typography
                    .headlineMedium,
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                text =
                    strings["movies"],
                selected =
                    selectedType ==
                        "movie",
            ) {
                selectedType = "movie"
                onContentFocused()
            }

            ActionButton(
                text =
                    strings["series"],
                selected =
                    selectedType ==
                        "series",
            ) {
                selectedType = "series"
                onContentFocused()
            }

            ActionButton(
                text = "Kanallar",
                selected =
                    selectedType ==
                        "channel",
            ) {
                selectedType = "channel"
                onContentFocused()
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
            },
            label = {
                Text(
                    strings["search_hint"],
                )
            },
            modifier =
                Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (loading) {
            CircularProgressIndicator()
        }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            if (
                selectedType ==
                "channel"
            ) {
                items(
                    channelResults,
                    key = { it.id },
                ) { channel ->
                    SearchResultRow(
                        title =
                            channel.name,
                        subtitle =
                            "Canlı TV",
                        artwork =
                            channel.logo,
                    ) {
                        liveChannel =
                            channel
                    }
                }
            } else {
                items(
                    results,
                    key = {
                        "${it.kind}-${it.localId}"
                    },
                ) { item ->
                    SearchResultRow(
                        title =
                            item.name,
                        subtitle =
                            if (
                                item.kind ==
                                "movie"
                            ) {
                                strings[
                                    "movies"
                                ]
                            } else {
                                strings[
                                    "series"
                                ]
                            },
                        artwork =
                            item.artwork,
                    ) {
                        target =
                            ContentTarget(
                                kind =
                                    item.kind,
                                name =
                                    item.name,
                                poster =
                                    item.artwork,
                                local =
                                    item,
                            )
                    }
                }
            }

            if (
                !loading &&
                query.trim().length >= 2 &&
                (
                    (
                        selectedType ==
                        "channel" &&
                        channelResults
                            .isEmpty()
                    ) ||
                    (
                        selectedType !=
                        "channel" &&
                        results.isEmpty()
                    )
                )
            ) {
                item {
                    Text(
                        strings["empty"],
                        color =
                            Color(
                                0xFF94A3B8,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    artwork: String?,
    onClick: () -> Unit,
) {
    var focused by remember {
        mutableStateOf(false)
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (focused) {
                        Color(0xFF2563EB)
                    } else {
                        Color(0xFF151C28)
                    },
                    RoundedCornerShape(9.dp),
                )
                .onFocusChanged {
                    focused =
                        it.isFocused
                }
                .clickable(
                    onClick = onClick,
                )
                .padding(10.dp),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        TvPosterImage(
            artwork,
            modifier =
                Modifier
                    .width(54.dp)
                    .height(78.dp),
        )

        Column {
            Text(
                title,
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
            )
            Text(
                subtitle,
                color =
                    Color(0xFFCBD5E1),
            )
        }
    }
}

@Composable
private fun SearchLivePlayer(
    channel: com.streamlivex.android.tv.data.NativeLiveChannel,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val sessionId =
        "tv-search-live-${channel.id}"

    val request =
        remember(
            channel.id,
        ) {
            PlaybackRequest(
                sessionId = sessionId,
                item =
                    PlaybackItem(
                        name =
                            channel.name,
                        url =
                            channel.streamUrl,
                        kind =
                            "live",
                    ),
                preferences =
                    PlaybackPreferences(
                        showInfo = false,
                    ),
            )
        }

    val player =
        remember(sessionId) {
            playerFor(request)
        }

    DisposableEffect(
        sessionId,
    ) {
        onFullscreenStateChanged(
            true,
        )
        onDispose {
            onFullscreenStateChanged(
                false,
            )
            releasePlayer(
                sessionId,
            )
        }
    }

    LaunchedEffect(
        channel.streamUrl,
    ) {
        player.setMediaItem(
            MediaItem.fromUri(
                channel.streamUrl,
            ),
        )
        player.prepare()
        player.playWhenReady = true
    }

    BackHandler {
        onClose()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black,
                ),
    ) {
        AndroidView(
            modifier =
                Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(
                    viewContext,
                ).apply {
                    layoutParams =
                        ViewGroup
                            .LayoutParams(
                                ViewGroup
                                    .LayoutParams
                                    .MATCH_PARENT,
                                ViewGroup
                                    .LayoutParams
                                    .MATCH_PARENT,
                            )
                    this.player =
                        player
                    useController =
                        false
                    keepScreenOn =
                        true
                }
            },
            update = {
                it.player = player
                it.useController =
                    false
            },
        )

        ActionButton(
            text =
                "← ${channel.name}",
            modifier =
                Modifier
                    .align(
                        Alignment.TopStart,
                    )
                    .padding(20.dp),
        ) {
            onClose()
        }
    }
}

@Composable
fun TvMyListScreen(locale: TvLocale) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val store = remember { TvContentStore(context) }
    val rows = remember { store.favorites() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            strings["my_list"],
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )

        if (rows.isEmpty()) {
            Text(strings["empty"], color = Color(0xFF94A3B8))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF151C28), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvPosterImage(item.artwork, modifier = Modifier.width(58.dp).height(84.dp))
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(item.kind.uppercase(), color = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRail(
    title: String,
    cards: List<HomeCard>,
    onClick: (HomeCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards, key = { it.id }) { card ->
                MediaCard(
                    title = card.title,
                    artwork = card.artwork,
                    subtitle = card.subtitle,
                    modifier = Modifier.width(170.dp),
                ) {
                    onClick(card)
                }
            }
        }
    }
}

@Composable
private fun IndexedPosterGrid(
    rows: List<TvIndexedMedia>,
    restoreRequester: FocusRequester,
    restoreId: String?,
    listState: LazyListState,
    totalCount: Int,
    loadingMore: Boolean,
    onNeedMore: () -> Unit,
    onFocus: (TvIndexedMedia) -> Unit,
    onClick: (TvIndexedMedia) -> Unit,
) {
    val chunks =
        remember(rows) {
            rows.chunked(5)
        }

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(
            items = chunks,
        ) { rowIndex, row ->
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(14.dp),
            ) {
                row.forEachIndexed {
                    itemIndex,
                    media ->

                    val globalIndex =
                        rowIndex * 5 +
                            itemIndex

                    MediaCard(
                        title =
                            media.name,
                        artwork =
                            media.artwork,
                        subtitle =
                            if (
                                totalCount > 0
                            ) {
                                "${globalIndex + 1} / $totalCount"
                            } else {
                                ""
                            },
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(
                                    if (
                                        media.localId ==
                                        restoreId
                                    ) {
                                        Modifier
                                            .focusRequester(
                                                restoreRequester,
                                            )
                                    } else {
                                        Modifier
                                    },
                                ),
                        onFocus = {
                            onFocus(media)

                            if (
                                !loadingMore &&
                                rows.size <
                                totalCount &&
                                globalIndex >=
                                rows.size - 20
                            ) {
                                onNeedMore()
                            }
                        },
                    ) {
                        onClick(media)
                    }
                }

                repeat(
                    5 - row.size,
                ) {
                    Box(
                        Modifier.weight(1f),
                    )
                }
            }
        }

        if (
            loadingMore &&
            rows.isNotEmpty()
        ) {
            item(
                key = "loading-more",
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    title: String,
    artwork: String?,
    subtitle: String,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(
                if (focused) Color(0xFF2563EB) else Color(0xFF151C28),
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TvPosterImage(artwork, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = if (focused) Color.White else Color(0xFF94A3B8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FocusRow(
    title: String,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    focused -> Color(0xFF2563EB)
                    selected -> Color(0xFF172554)
                    else -> Color(0xFF151C28)
                },
                RoundedCornerShape(8.dp),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                when {
                    focused -> Color(0xFF2563EB)
                    selected -> Color(0xFF1D4ED8)
                    else -> Color(0xFF1E293B)
                },
                RoundedCornerShape(9.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SimpleResultRow(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF151C28), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Color(0xFF94A3B8))
    }
}

@Composable
private fun LoadingState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator()
            Text(text, color = Color.White)
        }
    }
}

@Composable
private fun ErrorState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFFF87171))
    }
}

private fun NativeVodItem.toIndexed(): TvIndexedMedia =
    TvIndexedMedia(
        kind = "movie",
        localId = id,
        name = name,
        streamUrl = streamUrl,
        artwork = poster,
        seriesId = null,
        categoryId = categoryId,
    )

private fun NativeSeriesItem.toIndexed(): TvIndexedMedia =
    TvIndexedMedia(
        kind = "series",
        localId = id,
        name = name,
        streamUrl = "",
        artwork = cover,
        seriesId = seriesId,
        categoryId = categoryId,
    )

private fun availabilityText(strings: TvStrings, available: Boolean): String =
    if (available) strings["in_playlist"] else strings["not_in_playlist"]
