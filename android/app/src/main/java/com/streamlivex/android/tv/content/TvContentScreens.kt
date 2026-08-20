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
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.streamlivex.android.tv.data.TvPerformanceManager
import com.streamlivex.android.tv.data.TvSavedItem
import com.streamlivex.android.tv.data.TvTmdbClient
import com.streamlivex.android.tv.data.TvTmdbDetail
import com.streamlivex.android.tv.data.TvTmdbEpisode
import com.streamlivex.android.tv.data.TvTmdbMedia
import com.streamlivex.android.tv.data.TvTmdbPerson
import com.streamlivex.android.tv.data.TvUnifiedResult
import com.streamlivex.android.tv.data.TvUnifiedLibrary
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.i18n.TvLocale
import com.streamlivex.android.tv.i18n.TvStrings
import com.streamlivex.android.tv.player.TvNativePlayer
import com.streamlivex.android.tv.profile.TvProfilePolicy
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
    val sourceProvider: TvProviderConfig? = null,
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
    var newMovies by remember { mutableStateOf<List<TvIndexedMedia>>(emptyList()) }
    var newSeries by remember { mutableStateOf<List<TvIndexedMedia>>(emptyList()) }
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
            val addedMovies =
                index.newItems(
                    provider = provider,
                    kind = "movie",
                    limit = 14,
                )
            val addedSeries =
                index.newItems(
                    provider = provider,
                    kind = "series",
                    limit = 14,
                )

            Handler(Looper.getMainLooper()).post {
                trendingMovies = movies
                trendingSeries = series
                forYou = suggestions
                newMovies = addedMovies
                newSeries = addedSeries
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
            provider =
                target!!
                    .sourceProvider
                    ?: provider,
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

        if (newMovies.isNotEmpty()) {
            item {
                HomeRail(
                    title = "Yeni Eklenen Filmler",
                    cards =
                        newMovies
                            .filter {
                                TvProfilePolicy.allow(
                                    it.name,
                                )
                            }
                            .map {
                                media ->

                                HomeCard(
                                    id =
                                        "new-m-${media.localId}",
                                    title =
                                        media.name,
                                    artwork =
                                        media.artwork,
                                    subtitle =
                                        strings[
                                            "movies"
                                        ],
                                    target =
                                        ContentTarget(
                                            kind =
                                                "movie",
                                            name =
                                                media.name,
                                            poster =
                                                media.artwork,
                                            local =
                                                media,
                                        ),
                                )
                            },
                    onClick = {
                        card ->
                        card.target?.let {
                            target = it
                        }
                    },
                )
            }
        }

        if (newSeries.isNotEmpty()) {
            item {
                HomeRail(
                    title = "Yeni Eklenen Diziler",
                    cards =
                        newSeries
                            .filter {
                                TvProfilePolicy.allow(
                                    it.name,
                                )
                            }
                            .map {
                                media ->

                                HomeCard(
                                    id =
                                        "new-s-${media.localId}",
                                    title =
                                        media.name,
                                    artwork =
                                        media.artwork,
                                    subtitle =
                                        strings[
                                            "series"
                                        ],
                                    target =
                                        ContentTarget(
                                            kind =
                                                "series",
                                            name =
                                                media.name,
                                            poster =
                                                media.artwork,
                                            local =
                                                media,
                                        ),
                                )
                            },
                    onClick = {
                        card ->
                        card.target?.let {
                            target = it
                        }
                    },
                )
            }
        }

        val localTrendingMovies =
            trendingMovies.mapNotNull { media ->
                if (!TvProfilePolicy.allow(media.name)) {
                    return@mapNotNull null
                }

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
                if (!TvProfilePolicy.allow(media.name)) {
                    return@mapNotNull null
                }

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
    val movieGridEntryRequester = remember { FocusRequester() }
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

    val pageSize = remember { TvPerformanceManager.pageSize(context) }

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
                    .filter {
                        TvProfilePolicy.allow(
                            title = null,
                            category = it.name,
                        )
                    }
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
                        val allowedRows =
                            rows.filter {
                                TvProfilePolicy.allow(
                                    title = null,
                                    category = it.name,
                                )
                            }
                        categories = allowedRows
                        loadingCategories = false
                        val targetId =
                            TvContentCache.movieCategoryId
                                ?: allowedRows.firstOrNull()?.id
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
                        if (
                            category.id !=
                            selectedCategoryId
                        ) {
                            selectCategory(
                                category.id,
                            )
                        }
                    },
                    onClick = {
                        onContentFocused()
                    },
                    onRight = {
                        runCatching {
                            movieGridEntryRequester
                                .requestFocus()
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
                        entryFocusRequester =
                            movieGridEntryRequester,
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
    val seriesGridEntryRequester = remember { FocusRequester() }
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

    val pageSize = remember { TvPerformanceManager.pageSize(context) }

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
                ).filter {
                    TvProfilePolicy.allow(
                        title = null,
                        category = it.name,
                    )
                }
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
                        val allowedRows =
                            rows.filter {
                                TvProfilePolicy.allow(
                                    title = null,
                                    category = it.name,
                                )
                            }
                        categories = allowedRows
                        loadingCategories = false

                        val targetId =
                            TvContentCache
                                .seriesCategoryId
                                ?: allowedRows
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

                        if (
                            category.id !=
                            selectedCategoryId
                        ) {
                            selectCategory(
                                category.id,
                            )
                        }
                    },
                    onClick = {
                        onContentFocused()
                    },
                    onRight = {
                        runCatching {
                            seriesGridEntryRequester
                                .requestFocus()
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
                        entryFocusRequester =
                            seriesGridEntryRequester,
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
    val episodesFocusRequester = remember { FocusRequester() }
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
        // Detail is a full-screen content surface: hide the root chrome
        // while keeping its own DPAD graph active.
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
        detailListState.scrollToItem(
            0,
            0,
        )
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

        tmdbEpisodes = emptyList()
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

    fun openEpisodes() {
        if (
            target.kind !=
            "series"
        ) {
            return
        }

        scope.launch {
            detailListState.scrollToItem(
                1,
                0,
            )
            delay(160)
            runCatching {
                episodesFocusRequester
                    .requestFocus()
            }
        }
    }

    val playMovie: () -> Unit = {
        if (
            target.kind ==
                "movie" &&
            local
                ?.streamUrl
                ?.isNotBlank() ==
                true
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
    }

    val toggleFavorite: () -> Unit = {
        store.toggleFavorite(
            TvSavedItem(
                id =
                    favoriteId,
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
        favoriteRefresh +=
            1
    }

    LazyColumn(
        state =
            detailListState,
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(
                        0xFF0D111B,
                    ),
                )
                .padding(
                    horizontal = 28.dp,
                    vertical = 22.dp,
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                22.dp,
            ),
    ) {
        item(
            key =
                "detail-main",
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        28.dp,
                    ),
                verticalAlignment =
                    Alignment.Top,
            ) {
                TvPosterImage(
                    displayPoster,
                    modifier =
                        Modifier
                            .width(
                                220.dp,
                            )
                            .aspectRatio(
                                2f / 3f,
                            ),
                )

                Column(
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp,
                        ),
                ) {
                    Text(
                        media?.name
                            ?: target.name,
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme
                                .typography
                                .headlineLarge,
                        maxLines = 2,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    Text(
                        listOfNotNull(
                            media?.year
                                ?: target.vod
                                    ?.year
                                ?: target.series
                                    ?.year,
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
                        ).joinToString(
                            " • ",
                        ),
                        color =
                            Color(
                                0xFF94A3B8,
                            ),
                    )

                    Text(
                        description,
                        color =
                            Color(
                                0xFFCBD5E1,
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        maxLines = 5,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp,
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        if (
                            target.kind ==
                            "movie"
                        ) {
                            ActionButton(
                                text =
                                    if (
                                        local
                                            ?.streamUrl
                                            ?.isNotBlank() ==
                                        true
                                    ) {
                                        "▶ ${strings["play"]}"
                                    } else {
                                        strings[
                                            "not_in_playlist"
                                        ]
                                    },
                                modifier =
                                    Modifier
                                        .focusRequester(
                                            primaryFocusRequester,
                                        ),
                            ) {
                                playMovie()
                            }
                        } else {
                            ActionButton(
                                text =
                                    "↓ Bölümler",
                                modifier =
                                    Modifier
                                        .focusRequester(
                                            primaryFocusRequester,
                                        )
                                        .onKeyEvent {
                                            event ->

                                            if (
                                                event.type ==
                                                KeyEventType.KeyDown &&
                                                event.key ==
                                                Key.DirectionDown
                                            ) {
                                                openEpisodes()
                                                true
                                            } else {
                                                false
                                            }
                                        },
                            ) {
                                openEpisodes()
                            }
                        }

                        ActionButton(
                            text =
                                if (
                                    favorite
                                ) {
                                    "★ ${strings["remove_list"]}"
                                } else {
                                    "☆ ${strings["add_list"]}"
                                },
                            selected =
                                favorite,
                        ) {
                            toggleFavorite()
                        }

                        detail
                            ?.trailerUrl
                            ?.let {
                                trailerUrl ->

                                ActionButton(
                                    text =
                                        "▶ Fragman",
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
                            strings[
                                "director"
                            ],
                        rows =
                            detail
                                ?.directors
                                .orEmpty(),
                        onClick = {
                            selected ->

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
                                        selected to
                                        rows
                                }
                            }.start()
                        },
                    )

                    PeopleLine(
                        title =
                            strings[
                                "cast"
                            ],
                        rows =
                            detail
                                ?.cast
                                .orEmpty(),
                        onClick = {
                            selected ->

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
                                        selected to
                                        rows
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

        if (
            target.kind ==
            "series"
        ) {
            item(
                key =
                    "episodes",
            ) {
                SeriesEpisodesBlock(
                    locale =
                        locale,
                    store =
                        store,
                    series =
                        seriesInfo,
                    selectedSeason =
                        selectedSeason,
                    onSeason = {
                        selectedSeason =
                            it
                    },
                    firstFocusRequester =
                        episodesFocusRequester,
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

        if (
            recommendations
                .isNotEmpty()
        ) {
            item(
                key =
                    "recommendations",
            ) {
                TmdbRail(
                    locale =
                        locale,
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
    store: TvContentStore,
    series: NativeSeriesInfo?,
    selectedSeason: Int,
    onSeason: (Int) -> Unit,
    firstFocusRequester: FocusRequester,
    tmdbEpisodes: List<TvTmdbEpisode>,
    loading: Boolean,
    artwork: String?,
    onPlayEpisode: (NativeSeriesEpisode, List<NativeSeriesEpisode>) -> Unit,
) {
    val strings =
        remember(locale) {
            TvStrings(locale)
        }
    val episodes =
        series?.episodes.orEmpty()
    val seasons =
        episodes
            .map { it.season }
            .distinct()
            .sorted()
    val seasonEpisodes =
        episodes.filter {
            it.season == selectedSeason
        }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            strings["seasons_episodes"],
            color = Color.White,
            fontWeight =
                FontWeight.Bold,
            style =
                MaterialTheme
                    .typography
                    .headlineSmall,
        )

        LazyRow(
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp,
                ),
        ) {
            itemsIndexed(
                seasons,
            ) {
                index,
                season ->

                ActionButton(
                    text =
                        "${strings["season"]} $season",
                    selected =
                        season ==
                            selectedSeason,
                    modifier =
                        if (
                            index == 0
                        ) {
                            Modifier
                                .focusRequester(
                                    firstFocusRequester,
                                )
                        } else {
                            Modifier
                        },
                ) {
                    onSeason(season)
                }
            }
        }

        if (
            seasons.isEmpty()
        ) {
            ActionButton(
                text =
                    if (loading) {
                        "Bölümler yükleniyor…"
                    } else {
                        "Bölüm bulunamadı"
                    },
                modifier =
                    Modifier
                        .focusRequester(
                            firstFocusRequester,
                        ),
            ) {}
        } else if (loading) {
            Text(
                strings["loading"],
                color =
                    Color(0xFF94A3B8),
            )
        }

        seasonEpisodes.forEach {
            episode ->

            val tmdb =
                tmdbEpisodes
                    .firstOrNull {
                        it.episodeNumber ==
                            episode.episode
                    }

            val savedId =
                "series-${series?.series?.seriesId}-${episode.episodeId}"

            val watched =
                store.isWatched(
                    savedId,
                )

            EpisodeCard(
                title =
                    tmdb?.name
                        ?: episode.name,
                subtitle =
                    buildString {
                        append(
                            "${strings["episode"]} ${episode.episode}",
                        )
                        tmdb
                            ?.airDate
                            ?.let {
                                append(
                                    " • $it",
                                )
                            }
                        tmdb
                            ?.runtime
                            ?.let {
                                append(
                                    " • $it dk",
                                )
                            }
                        if (watched) {
                            append(
                                " • ✓ İzlendi",
                            )
                        }
                    },
                description =
                    tmdb?.overview,
                image =
                    tmdb?.still
                        ?: artwork,
                watched =
                    watched,
                onToggleWatched = {
                    store.toggleWatched(
                        savedId,
                    )
                },
                onClick = {
                    onPlayEpisode(
                        episode,
                        seasonEpisodes,
                    )
                },
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
    watched: Boolean,
    onToggleWatched: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF151C28),
                    RoundedCornerShape(
                        10.dp,
                    ),
                )
                .padding(10.dp),
        horizontalArrangement =
            Arrangement.spacedBy(
                14.dp,
            ),
    ) {
        TvPosterImage(
            image,
            modifier =
                Modifier
                    .width(210.dp)
                    .aspectRatio(
                        16f / 9f,
                    ),
        )

        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    6.dp,
                ),
        ) {
            Text(
                title,
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
            )

            Text(
                subtitle,
                color =
                    Color(0xFF94A3B8),
            )

            Text(
                description.orEmpty(),
                color =
                    Color(0xFFCBD5E1),
                maxLines = 3,
                overflow =
                    TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                ActionButton(
                    text =
                        "▶ Oynat",
                ) {
                    onClick()
                }

                ActionButton(
                    text =
                        if (watched) {
                            "✓ İzlendi"
                        } else {
                            "İzlendi olarak işaretle"
                        },
                    selected =
                        watched,
                ) {
                    onToggleWatched()
                }
            }
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
    val unified =
        remember {
            TvUnifiedLibrary(
                context,
            )
        }

    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("movie") }
    var results by remember { mutableStateOf<List<TvUnifiedResult>>(emptyList()) }
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
    var recentRefresh by remember { mutableIntStateOf(0) }

    val recentSearches =
        remember(recentRefresh) {
            store.recentSearches()
        }

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
                                    ) &&
                                        TvProfilePolicy.allow(
                                            it.name,
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
                        ) &&
                            TvProfilePolicy.allow(
                                it.name,
                            )
                    }
                    .take(60)
                    .toList()

            results = emptyList()
            loading = false
        } else {
            Thread {
                val rows =
                    unified.search(
                        query = q,
                        kind = selectedType,
                        limitPerProvider = 40,
                        totalLimit = 60,
                    )

                Handler(
                    Looper.getMainLooper(),
                ).post {
                    results =
                        rows.filter {
                            TvProfilePolicy.allow(
                                it.media.name,
                            )
                        }
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
            provider =
                target!!
                    .sourceProvider
                    ?: provider,
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

        if (
            query.isBlank() &&
            recentSearches.isNotEmpty()
        ) {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                recentSearches
                    .take(6)
                    .forEach {
                        recent ->

                        ActionButton(
                            text = recent,
                        ) {
                            query = recent
                            onContentFocused()
                        }
                    }

                ActionButton(
                    text =
                        "Son Aramaları Temizle",
                ) {
                    store.clearRecentSearches()
                    recentRefresh += 1
                }
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
                        store.addRecentSearch(
                            query,
                        )
                        recentRefresh += 1
                        liveChannel =
                            channel
                    }
                }
            } else {
                items(
                    results,
                    key = {
                        "${it.media.kind}-${it.media.localId}-${it.provider.username}"
                    },
                ) { result ->
                    val item =
                        result.media

                    SearchResultRow(
                        title =
                            item.name,
                        subtitle =
                            buildString {
                                append(
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
                                )

                                if (
                                    result.sourceCount >
                                    1
                                ) {
                                    append(
                                        " · ${result.sourceCount} kaynak",
                                    )
                                }
                            },
                        artwork =
                            item.artwork,
                    ) {
                        store.addRecentSearch(
                            query,
                        )
                        recentRefresh += 1
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
                                sourceProvider =
                                    result.provider,
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
fun TvMyListScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context =
        LocalContext.current
    val strings =
        remember(locale) {
            TvStrings(locale)
        }
    val store =
        remember {
            TvContentStore(
                context,
            )
        }
    val index =
        remember {
            TvLibraryIndex(
                context,
            )
        }

    var filter by
        remember {
            mutableStateOf(
                "all",
            )
        }
    var refresh by
        remember {
            mutableIntStateOf(
                0,
            )
        }
    var target by
        remember {
            mutableStateOf<ContentTarget?>(
                null,
            )
        }
    var playing by
        remember {
            mutableStateOf<TvSavedItem?>(
                null,
            )
        }

    val rows =
        remember(
            filter,
            refresh,
        ) {
            store.favorites()
                .filter { item ->
                    when (filter) {
                        "movie" ->
                            item.kind ==
                                "movie"

                        "series" ->
                            item.kind ==
                                "series"

                        else ->
                            true
                    }
                }
        }

    if (playing != null) {
        val saved =
            playing!!

        TvNativePlayer(
            saved = saved,
            request =
                PlaybackRequest(
                    sessionId =
                        "tv-my-list-${saved.id}",
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
            playerFor =
                playerFor,
            releasePlayer =
                releasePlayer,
            store =
                store,
            locale =
                locale,
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
                refresh += 1
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
                    Color(
                        0xFF0D111B,
                    ),
                )
                .padding(24.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp,
            ),
    ) {
        Text(
            strings["my_list"],
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
                Arrangement.spacedBy(
                    10.dp,
                ),
        ) {
            ActionButton(
                text = "Tümü",
                selected =
                    filter ==
                        "all",
            ) {
                filter = "all"
                onContentFocused()
            }

            ActionButton(
                text =
                    strings["movies"],
                selected =
                    filter ==
                        "movie",
            ) {
                filter = "movie"
                onContentFocused()
            }

            ActionButton(
                text =
                    strings["series"],
                selected =
                    filter ==
                        "series",
            ) {
                filter = "series"
                onContentFocused()
            }
        }

        if (rows.isEmpty()) {
            Text(
                strings["empty"],
                color =
                    Color(
                        0xFF94A3B8,
                    ),
            )
        } else {
            val chunks =
                remember(
                    rows,
                ) {
                    rows.chunked(
                        5,
                    )
                }

            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(
                        14.dp,
                    ),
            ) {
                items(chunks) {
                    chunk ->

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                14.dp,
                            ),
                    ) {
                        chunk.forEach {
                            item ->

                            val local =
                                index
                                    .findByTitle(
                                        provider,
                                        item.kind,
                                        item.name,
                                    )

                            Column(
                                modifier =
                                    Modifier
                                        .weight(
                                            1f,
                                        ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp,
                                    ),
                            ) {
                                MediaCard(
                                    title =
                                        item.name,
                                    artwork =
                                        item.artwork
                                            ?: local
                                                ?.artwork,
                                    subtitle =
                                        when {
                                            store
                                                .isWatched(
                                                    item.id,
                                                ) ->
                                                "✓ İzlendi"

                                            (
                                                store
                                                    .progressFor(
                                                        item.id,
                                                    )
                                                    ?.positionMs
                                                    ?: 0L
                                            ) > 0L ->
                                                "Devam Et"

                                            else ->
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
                                                }
                                        },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(),
                                    onFocus = {
                                        onContentFocused()
                                    },
                                ) {
                                    target =
                                        ContentTarget(
                                            kind =
                                                item.kind,
                                            name =
                                                item.name,
                                            poster =
                                                item.artwork
                                                    ?: local
                                                        ?.artwork,
                                            local =
                                                local,
                                        )
                                }

                                ActionButton(
                                    text =
                                        "★ Listemden Çıkar",
                                ) {
                                    store
                                        .removeFavorite(
                                            item.id,
                                        )
                                    refresh += 1
                                }
                            }
                        }

                        repeat(
                            5 - chunk.size,
                        ) {
                            Box(
                                Modifier
                                    .weight(
                                        1f,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvHistoryScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context =
        LocalContext.current
    val strings =
        remember(locale) {
            TvStrings(locale)
        }
    val store =
        remember {
            TvContentStore(
                context,
            )
        }
    val index =
        remember {
            TvLibraryIndex(
                context,
            )
        }

    var filter by
        remember {
            mutableStateOf(
                "all",
            )
        }
    var refresh by
        remember {
            mutableIntStateOf(
                0,
            )
        }
    var target by
        remember {
            mutableStateOf<ContentTarget?>(
                null,
            )
        }
    var playing by
        remember {
            mutableStateOf<TvSavedItem?>(
                null,
            )
        }

    val rows =
        remember(
            filter,
            refresh,
        ) {
            store.viewingHistory()
                .filter {
                    item ->

                    when (filter) {
                        "movie" ->
                            item.kind ==
                                "movie"

                        "series" ->
                            item.kind ==
                                "series" ||
                                item.kind ==
                                "episode"

                        "watched" ->
                            store.isWatched(
                                item.id,
                            )

                        else ->
                            true
                    }
                }
        }

    if (playing != null) {
        val saved =
            playing!!

        TvNativePlayer(
            saved = saved,
            request =
                PlaybackRequest(
                    sessionId =
                        "tv-history-${saved.id}",
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
                            ?: saved.positionMs,
                    preferences =
                        PlaybackPreferences(
                            showInfo = true,
                        ),
                ),
            playerFor =
                playerFor,
            releasePlayer =
                releasePlayer,
            store =
                store,
            locale =
                locale,
            onFullscreenStateChanged =
                onFullscreenStateChanged,
            onClose = {
                playing = null
                refresh += 1
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
                refresh += 1
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
                    Color(
                        0xFF0D111B,
                    ),
                )
                .padding(24.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp,
            ),
    ) {
        Text(
            "İzleme Geçmişi",
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
                Arrangement.spacedBy(
                    10.dp,
                ),
        ) {
            ActionButton(
                text = "Tümü",
                selected =
                    filter == "all",
            ) {
                filter = "all"
                onContentFocused()
            }

            ActionButton(
                text =
                    strings["movies"],
                selected =
                    filter ==
                        "movie",
            ) {
                filter = "movie"
                onContentFocused()
            }

            ActionButton(
                text =
                    strings["series"],
                selected =
                    filter ==
                        "series",
            ) {
                filter = "series"
                onContentFocused()
            }

            ActionButton(
                text = "İzlenenler",
                selected =
                    filter ==
                        "watched",
            ) {
                filter = "watched"
                onContentFocused()
            }

            ActionButton(
                text =
                    "Geçmişi Temizle",
            ) {
                store.clearViewingHistory()
                refresh += 1
            }
        }

        if (rows.isEmpty()) {
            Text(
                "İzleme geçmişi boş.",
                color =
                    Color(
                        0xFF94A3B8,
                    ),
            )
        } else {
            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                items(
                    rows,
                    key = {
                        it.id
                    },
                ) {
                    item ->

                    val local =
                        index.findByTitle(
                            provider,
                            if (
                                item.kind ==
                                "episode"
                            ) {
                                "series"
                            } else {
                                item.kind
                            },
                            item.name,
                        )

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(
                                        0xFF151C28,
                                    ),
                                    RoundedCornerShape(
                                        10.dp,
                                    ),
                                )
                                .padding(
                                    10.dp,
                                ),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp,
                            ),
                    ) {
                        MediaCard(
                            title =
                                item.name,
                            artwork =
                                item.artwork
                                    ?: local
                                        ?.artwork,
                            subtitle =
                                when {
                                    store.isWatched(
                                        item.id,
                                    ) ->
                                        "✓ İzlendi"

                                    item.positionMs >
                                        0L ->
                                        "Kaldığın yerden devam"

                                    else ->
                                        item.kind
                                },
                            modifier =
                                Modifier.width(
                                    190.dp,
                                ),
                            onFocus = {
                                onContentFocused()
                            },
                        ) {
                            if (
                                item.kind ==
                                "episode" ||
                                local == null
                            ) {
                                playing =
                                    item
                            } else {
                                target =
                                    ContentTarget(
                                        kind =
                                            item.kind,
                                        name =
                                            item.name,
                                        poster =
                                            item.artwork
                                                ?: local
                                                    .artwork,
                                        local =
                                            local,
                                    )
                            }
                        }

                        ActionButton(
                            text = "Devam Et",
                        ) {
                            playing =
                                item
                        }

                        ActionButton(
                            text =
                                "Baştan Başlat",
                        ) {
                            store.restart(
                                item.id,
                            )
                            playing =
                                item.copy(
                                    positionMs =
                                        0L,
                                )
                            refresh += 1
                        }

                        ActionButton(
                            text =
                                "Geçmişten Kaldır",
                        ) {
                            store.removeFromHistory(
                                item.id,
                            )
                            refresh += 1
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
    entryFocusRequester: FocusRequester,
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

    val scope =
        androidx.compose.runtime
            .rememberCoroutineScope()

    val requesters =
        remember {
            mutableMapOf<
                String,
                FocusRequester,
            >()
        }

    fun requesterFor(
        media: TvIndexedMedia,
    ): FocusRequester =
        requesters.getOrPut(
            media.localId,
        ) {
            FocusRequester()
        }

    fun moveTo(
        targetIndex: Int,
    ): Boolean {
        if (
            targetIndex !in
            rows.indices
        ) {
            return false
        }

        val target =
            rows[targetIndex]
        val requester =
            requesterFor(target)
        val targetRow =
            targetIndex / 5

        scope.launch {
            runCatching {
                listState.scrollToItem(
                    targetRow,
                )
            }

            delay(60)

            runCatching {
                requester.requestFocus()
            }
        }

        return true
    }

    LazyColumn(
        state =
            listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp,
            ),
    ) {
        itemsIndexed(
            items =
                chunks,
        ) {
            rowIndex,
            row ->

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        14.dp,
                    ),
            ) {
                row.forEachIndexed {
                    itemIndex,
                    media ->

                    val globalIndex =
                        rowIndex * 5 +
                            itemIndex

                    val ownRequester =
                        requesterFor(
                            media,
                        )

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
                                    when {
                                        media.localId ==
                                            restoreId ->
                                            Modifier
                                                .focusRequester(
                                                    restoreRequester,
                                                )

                                        globalIndex ==
                                            0 ->
                                            Modifier
                                                .focusRequester(
                                                    entryFocusRequester,
                                                )

                                        else ->
                                            Modifier
                                                .focusRequester(
                                                    ownRequester,
                                                )
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
                        onDirection = {
                            key ->

                            when (key) {
                                Key.DirectionRight -> {
                                    if (
                                        itemIndex <
                                        row.size - 1
                                    ) {
                                        moveTo(
                                            globalIndex +
                                                1,
                                        )
                                    } else {
                                        false
                                    }
                                }

                                Key.DirectionLeft -> {
                                    if (
                                        itemIndex >
                                        0
                                    ) {
                                        moveTo(
                                            globalIndex -
                                                1,
                                        )
                                    } else {
                                        // Let Compose leave the grid
                                        // and return to categories.
                                        false
                                    }
                                }

                                Key.DirectionDown -> {
                                    val nextRowStart =
                                        (rowIndex + 1) *
                                            5

                                    if (
                                        nextRowStart >=
                                        rows.size
                                    ) {
                                        false
                                    } else {
                                        val target =
                                            minOf(
                                                nextRowStart +
                                                    itemIndex,
                                                rows.size -
                                                    1,
                                            )

                                        moveTo(target)
                                    }
                                }

                                Key.DirectionUp -> {
                                    if (
                                        rowIndex <=
                                        0
                                    ) {
                                        false
                                    } else {
                                        val target =
                                            (rowIndex - 1) *
                                                5 +
                                                itemIndex

                                        moveTo(
                                            target.coerceAtMost(
                                                rows.size -
                                                    1,
                                            ),
                                        )
                                    }
                                }

                                else ->
                                    false
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
                        Modifier.weight(
                            1f,
                        ),
                    )
                }
            }
        }

        if (
            loadingMore &&
            rows.isNotEmpty()
        ) {
            item(
                key =
                    "loading-more",
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
    onDirection: (Key) -> Boolean = { false },
    onClick: () -> Unit,
) {
    var focused by
        remember(title) {
            mutableStateOf(false)
        }

    Column(
        modifier =
            modifier
                .background(
                    if (focused) {
                        Color(0xFF2563EB)
                    } else {
                        Color(0xFF151C28)
                    },
                    RoundedCornerShape(10.dp),
                )
                .onFocusChanged { state ->
                    focused =
                        state.isFocused
                    if (
                        state.isFocused
                    ) {
                        onFocus()
                    }
                }
                .onKeyEvent { event ->
                    when {
                        event.type ==
                            KeyEventType.KeyUp &&
                            (
                                event.key ==
                                Key.DirectionCenter ||
                                event.key ==
                                Key.Enter ||
                                event.key ==
                                Key.NumPadEnter
                            ) -> {
                            onClick()
                            true
                        }

                        event.type ==
                            KeyEventType.KeyDown &&
                            (
                                event.key ==
                                Key.DirectionLeft ||
                                event.key ==
                                Key.DirectionRight ||
                                event.key ==
                                Key.DirectionUp ||
                                event.key ==
                                Key.DirectionDown
                            ) ->
                            onDirection(
                                event.key,
                            )

                        else ->
                            false
                    }
                }
                .focusable()
                .padding(7.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                6.dp,
            ),
    ) {
        TvPosterImage(
            artwork,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        2f / 3f,
                    ),
        )

        Text(
            title,
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
        )

        Text(
            subtitle,
            color =
                if (focused) {
                    Color.White
                } else {
                    Color(
                        0xFF94A3B8,
                    )
                },
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
        )
    }
}

@Composable
private fun FocusRow(
    title: String,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onRight: (() -> Unit)? = null,
) {
    var focused by
        remember(title) {
            mutableStateOf(false)
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        focused ->
                            Color(
                                0xFF2563EB,
                            )

                        selected ->
                            Color(
                                0xFF172554,
                            )

                        else ->
                            Color(
                                0xFF151C28,
                            )
                    },
                    RoundedCornerShape(
                        8.dp,
                    ),
                )
                .onFocusChanged { state ->
                    focused =
                        state.isFocused
                    if (
                        state.isFocused
                    ) {
                        onFocus()
                    }
                }
                .onKeyEvent { event ->
                    when {
                        event.type ==
                            KeyEventType.KeyUp &&
                            (
                                event.key ==
                                Key.DirectionCenter ||
                                event.key ==
                                Key.Enter ||
                                event.key ==
                                Key.NumPadEnter
                            ) -> {
                            onClick()
                            true
                        }

                        event.type ==
                            KeyEventType.KeyDown &&
                            event.key ==
                            Key.DirectionRight &&
                            onRight !=
                            null -> {
                            onRight()
                            true
                        }

                        else ->
                            false
                    }
                }
                .focusable()
                .padding(
                    horizontal =
                        12.dp,
                    vertical =
                        11.dp,
                ),
    ) {
        Text(
            title,
            color =
                Color.White,
            fontWeight =
                if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.SemiBold
                },
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
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
    var focused by
        remember(text) {
            mutableStateOf(false)
        }

    Box(
        modifier =
            modifier
                .background(
                    when {
                        focused ->
                            Color(
                                0xFF2563EB,
                            )

                        selected ->
                            Color(
                                0xFF1D4ED8,
                            )

                        else ->
                            Color(
                                0xFF1E293B,
                            )
                    },
                    RoundedCornerShape(
                        9.dp,
                    ),
                )
                .onFocusChanged { state ->
                    focused =
                        state.isFocused
                }
                .onKeyEvent { event ->
                    if (
                        event.type ==
                        KeyEventType.KeyUp &&
                        (
                            event.key ==
                            Key.DirectionCenter ||
                            event.key ==
                            Key.Enter ||
                            event.key ==
                            Key.NumPadEnter
                        )
                    ) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                .focusable()
                .padding(
                    horizontal =
                        16.dp,
                    vertical =
                        11.dp,
                ),
    ) {
        Text(
            text =
                text,
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
        )
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
