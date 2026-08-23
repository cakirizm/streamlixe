@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamlivex.android.tv.content

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
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
import com.streamlivex.android.BuildConfig
import com.streamlivex.android.PlaybackPreferences
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.NativeSeriesEpisode
import com.streamlivex.android.tv.data.NativeSeriesInfo
import com.streamlivex.android.tv.data.NativeSeriesItem
import com.streamlivex.android.tv.data.NativeVodItem
import com.streamlivex.android.tv.data.NativeLiveChannel
import com.streamlivex.android.tv.data.TvContentCache
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvIndexedMedia
import com.streamlivex.android.tv.data.TvLibraryIndex
import com.streamlivex.android.tv.data.TvLiveLibraryCache
import com.streamlivex.android.tv.data.TvLiveProfileStore
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
import com.streamlivex.android.tv.TvDesignTokens
import com.streamlivex.android.tv.TvPerformanceTrace
import com.streamlivex.android.tv.profile.TvActiveScope
import com.streamlivex.android.tv.profile.TvProfilePolicy
import com.streamlivex.android.tv.sports.SportsBroadcastResolver
import com.streamlivex.android.tv.sports.SportsChannelIndex
import com.streamlivex.android.tv.sports.SportsEvent
import com.streamlivex.android.tv.sports.SportsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

private object TvHomeMemoryCache {
    var key: String = ""
    var movies: List<TvTmdbMedia> = emptyList()
    var series: List<TvTmdbMedia> = emptyList()
    var suggestions: List<TvIndexedMedia> = emptyList()
    var newMovies: List<TvIndexedMedia> = emptyList()
    var newSeries: List<TvIndexedMedia> = emptyList()
    var channels: List<NativeLiveChannel> = emptyList()
    var epg: Map<String, Pair<String, String>> = emptyMap()
    var epgUpdatedAtMs: Long = 0L
}

@Composable
fun TvHomeScreen(
    provider: TvProviderConfig,
    locale: TvLocale,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onOpenSportsEvent: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val store = remember { TvContentStore(context) }
    val tmdb = remember { TvTmdbClient() }
    val index = remember { TvLibraryIndex(context) }
    val homeCacheKey = "${provider.server}|${provider.username}|${locale.name}"
    val hasHomeCache = TvHomeMemoryCache.key == homeCacheKey

    var trendingMovies by remember(homeCacheKey) { mutableStateOf(if (hasHomeCache) TvHomeMemoryCache.movies else emptyList()) }
    var trendingSeries by remember(homeCacheKey) { mutableStateOf(if (hasHomeCache) TvHomeMemoryCache.series else emptyList()) }
    var forYou by remember(homeCacheKey) { mutableStateOf(if (hasHomeCache) TvHomeMemoryCache.suggestions else emptyList()) }
    var newMovies by remember(homeCacheKey) { mutableStateOf(if (hasHomeCache) TvHomeMemoryCache.newMovies else emptyList()) }
    var newSeries by remember(homeCacheKey) { mutableStateOf(if (hasHomeCache) TvHomeMemoryCache.newSeries else emptyList()) }
    var favoriteChannels by remember(homeCacheKey) { mutableStateOf(if (hasHomeCache) TvHomeMemoryCache.channels else emptyList()) }
    var liveEpgLabels by remember(homeCacheKey) { mutableStateOf(if (hasHomeCache) TvHomeMemoryCache.epg else emptyMap()) }
    var sportsEvents by remember(homeCacheKey) { mutableStateOf<List<SportsEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf<ContentTarget?>(null) }
    var playing by remember { mutableStateOf<TvSavedItem?>(null) }
    var currentPlaylist by remember {
        mutableStateOf<List<Pair<TvSavedItem, PlaybackItem>>>(emptyList())
    }
    var playlistIndex by remember { mutableIntStateOf(0) }
    var homeRefresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(provider.server, provider.username, locale) {
        val startedAt = SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) {
            val profileAt = TvPerformanceTrace.profileSelectedAtMs
            Log.d("StreamLiveXHome", "first-frame=${if (profileAt > 0L) SystemClock.elapsedRealtime() - profileAt else 0L}ms cached=$hasHomeCache")
        }
        Thread {
            val sports = SportsRepository(context).today()
            Handler(Looper.getMainLooper()).post {
                sportsEvents = sports.take(12)
                Log.i("SportsPipeline", "home state fixture count=${sportsEvents.size}")
                Log.i("SportsPipeline", "home rendered fixtures=${sportsEvents.joinToString(" | ") { "${it.home}-${it.away}" }}")
            }
            val enriched = SportsRepository(context).enrichTeamBadges(sports.take(12))
            Handler(Looper.getMainLooper()).post { sportsEvents = enriched }
        }.start()
        Thread {
            val liveProfile = TvLiveProfileStore(context)
            val favoriteIds = liveProfile.favoriteChannelIds()
            val lastChannel = liveProfile.lastChannelId()
            val libraryChannels = XtreamClient().loadLiveLibrary(provider).getOrNull()?.channels.orEmpty()
            SportsChannelIndex.forChannels(libraryChannels)
            val rankedChannels = libraryChannels
                .filter { liveChannelPriority(it.name) < 100 }
                .sortedWith(
                    compareBy<NativeLiveChannel> { liveChannelPriority(it.name) }
                        .thenByDescending { liveProfile.channelViewCount(it.id) }
                        .thenByDescending { it.id in favoriteIds }
                        .thenByDescending { it.id == lastChannel },
                )
                .distinctBy { liveChannelGroup(it.name) }
                .take(6)

            Handler(Looper.getMainLooper()).post {
                favoriteChannels = rankedChannels
                TvHomeMemoryCache.channels = rankedChannels
                if (BuildConfig.DEBUG) Log.d("StreamLiveXHome", "live=${SystemClock.elapsedRealtime() - startedAt}ms")
            }
        }.start()
        Thread {
            val movies = tmdb.trending("movie", locale).getOrDefault(emptyList()).take(14)
            val series = tmdb.trending("series", locale).getOrDefault(emptyList()).take(14)
            Handler(Looper.getMainLooper()).post {
                trendingMovies = movies
                trendingSeries = series
                TvHomeMemoryCache.key = homeCacheKey
                TvHomeMemoryCache.movies = movies
                TvHomeMemoryCache.series = series
                if (BuildConfig.DEBUG) Log.d("StreamLiveXHome", "hero=${SystemClock.elapsedRealtime() - startedAt}ms")
            }
            val history =
                store.viewingHistory()
                    .take(12)
            val affinityRows =
                history.mapNotNull { watched ->
                    val lookupKind =
                        if (watched.kind == "episode") "series"
                        else watched.kind
                    val lookupTitle =
                        if (watched.kind == "episode") {
                            watched.subtitle ?: watched.name
                        } else {
                            watched.name
                        }
                    index.findByTitle(
                        provider,
                        lookupKind,
                        lookupTitle,
                    )
                }
            val favoriteCategories =
                affinityRows
                    .mapNotNull {
                        row ->

                        row.categoryId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { row.kind to it }
                    }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(4)
            val seenIds = affinityRows.map { it.localId }.toSet()
            val suggestionPools =
                favoriteCategories
                    .map { preference ->
                        index.categoryPage(
                            provider = provider,
                            kind = preference.key.first,
                            categoryId = preference.key.second,
                            limit = 8,
                            offset = 0,
                        )
                    }
            val personalized =
                (0 until 8)
                    .flatMap { position ->
                        suggestionPools.mapNotNull {
                            it.getOrNull(position)
                        }
                    }
                    .filterNot { it.localId in seenIds }
                    .distinctBy { it.localId }
                    .take(14)
            val suggestions =
                personalized.ifEmpty {
                    index.suggestions(provider, limit = 14)
                }
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
                forYou = suggestions
                newMovies = addedMovies
                newSeries = addedSeries
                TvHomeMemoryCache.suggestions = suggestions
                TvHomeMemoryCache.newMovies = addedMovies
                TvHomeMemoryCache.newSeries = addedSeries
                loading = false
                if (BuildConfig.DEBUG) Log.d("StreamLiveXHome", "catalogue=${SystemClock.elapsedRealtime() - startedAt}ms")
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

    LaunchedEffect(favoriteChannels) {
        if (favoriteChannels.isEmpty()) return@LaunchedEffect
        if (liveEpgLabels.isNotEmpty() && System.currentTimeMillis() - TvHomeMemoryCache.epgUpdatedAtMs < 15 * 60_000L) return@LaunchedEffect
        Thread {
            val now = System.currentTimeMillis() / 1000L
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val labels = favoriteChannels.take(6).mapNotNull { channel ->
                val current = XtreamClient().loadChannelEpg(provider, channel, 8)
                    .getOrNull()?.firstOrNull { it.isCurrent(now) } ?: return@mapNotNull null
                channel.id to (current.title to "${formatter.format(Date(current.startTimestamp * 1000))} - ${formatter.format(Date(current.stopTimestamp * 1000))}")
            }.toMap()
            Handler(Looper.getMainLooper()).post {
                liveEpgLabels = labels
                TvHomeMemoryCache.epg = labels
                TvHomeMemoryCache.epgUpdatedAtMs = System.currentTimeMillis()
            }
        }.start()
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

    val continueRows = remember(homeRefresh) { store.continueWatching() }
    val featuredRows = (trendingSeries + trendingMovies).mapNotNull { media ->
        if (!TvProfilePolicy.allow(media.name)) null
        else index.findByTitle(provider, media.kind, media.name)?.let { media to it }
    }.take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesignTokens.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (featuredRows.isNotEmpty()) {
                HomeFeaturedHero(mediaRows = featuredRows.map { it.first }) { media ->
                    featuredRows.firstOrNull { it.first.id == media.id }?.second?.let { local ->
                        target = ContentTarget(media.kind, media.name, media.id, media.poster, local)
                    }
                }
            }
        }

        if (continueRows.isNotEmpty()) {
            item {
                ContinueHomeRail(
                    rows = continueRows.take(10),
                    onClick = { playing = it },
                    onRemoveContinue = {
                        store.removeFromContinue(it.id)
                        homeRefresh += 1
                    },
                    onRemoveHistory = {
                        store.removeFromHistory(it.id)
                        homeRefresh += 1
                    },
                )
            }
        }

        if (favoriteChannels.isNotEmpty()) {
            item {
                LiveHomeRail(favoriteChannels, liveEpgLabels) { channel ->
                    TvLiveProfileStore(context).recordChannelView(channel.id)
                    playing = TvSavedItem("live-${channel.id}", "live", channel.name, channel.streamUrl, channel.logo)
                }
            }
        }

        if (sportsEvents.isNotEmpty()) {
            item {
                HomeSportsRail(
                    events = sportsEvents,
                    channels = TvLiveLibraryCache.library?.channels.orEmpty(),
                    onClick = { onOpenSportsEvent(it.id) },
                )
            }
        }

        if (false && newMovies.isNotEmpty()) {
            item {
                PosterHomeRail(
                    title = "Popüler Filmler",
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

        if (false && newSeries.isNotEmpty()) {
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
                PosterHomeRail(
                    title = "Popüler Filmler",
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
                PosterHomeRail(
                    title = "Popüler Diziler",
                    cards = localTrendingSeries,
                    onClick = { card ->
                        card.target?.let {
                            target = it
                        }
                    },
                )
            }
        }

        if (forYou.isNotEmpty()) item {
            PosterHomeRail(
                title = "Sizin İçin",
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
    menuFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }
    val index = remember { TvLibraryIndex(context) }
    val restoreRequester = remember { FocusRequester() }
    val movieGridEntryRequester = remember { FocusRequester() }
    val movieCategoryRequester = remember { FocusRequester() }
    val movieGridState = rememberLazyListState()
    val movieBrowseScope = androidx.compose.runtime.rememberCoroutineScope()

    var categories by remember { mutableStateOf(TvContentCache.vodCategories.orEmpty()) }
    var selectedCategoryId by remember { mutableStateOf(TvContentCache.movieCategoryId) }
    var movies by remember { mutableStateOf<List<TvIndexedMedia>>(emptyList()) }
    var previewMovie by remember { mutableStateOf<TvIndexedMedia?>(null) }
    var totalCount by remember { mutableIntStateOf(0) }
    var loadingCategories by remember { mutableStateOf(categories.isEmpty()) }
    var loadingPage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var categoryRailExpanded by remember { mutableStateOf(true) }
    var pendingMovieGridFocus by remember { mutableStateOf(false) }

    var target by remember { mutableStateOf<ContentTarget?>(null) }
    var playing by remember { mutableStateOf<TvSavedItem?>(null) }
    var returnToMovieGrid by remember { mutableStateOf(false) }

    val pageSize = remember { TvPerformanceManager.pageSize(context) }

    fun loadPage(
        categoryId: String,
        reset: Boolean,
    ) {
        if (loadingPage && !reset) return

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
                    if (reset) previewMovie = rows.firstOrNull()
                    loadingPage = false
                }
            }
        }.start()
    }

    fun selectCategory(categoryId: String) {
        TvContentCache.movieCategoryId = categoryId
        TvContentCache.movieFocusedId = TvContentCache.movieFocusedByCategory[categoryId]
        selectedCategoryId = categoryId
        loadPage(categoryId, reset = true)
    }

    LaunchedEffect(movies, pendingMovieGridFocus, selectedCategoryId) {
        if (!pendingMovieGridFocus || movies.isEmpty()) return@LaunchedEffect
        val rememberedIndex = movies.indexOfFirst { it.localId == TvContentCache.movieFocusedId }
        movieGridState.scrollToItem(rememberedIndex.coerceAtLeast(0) / 4)
        categoryRailExpanded = false
        delay(70)
        runCatching {
            if (rememberedIndex >= 0) restoreRequester.requestFocus()
            else movieGridEntryRequester.requestFocus()
        }
        pendingMovieGridFocus = false
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
                returnToMovieGrid = true
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
            categoryRailExpanded = false
            val focusedIndex =
                movies.indexOfFirst { it.localId == TvContentCache.movieFocusedId }
            if (focusedIndex >= 0) {
                movieGridState.scrollToItem(focusedIndex / 4)
            }
            delay(if (returnToMovieGrid) 180 else 120)
            runCatching {
                restoreRequester.requestFocus()
            }
            returnToMovieGrid = false
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
                    .width(if (categoryRailExpanded) 220.dp else 0.dp)
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
                    modifier =
                        if (category.id == selectedCategoryId) {
                            Modifier.focusRequester(movieCategoryRequester)
                        } else {
                            Modifier
                        },
                    onFocus = {
                        onContentFocused()
                        categoryRailExpanded = true
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
                        pendingMovieGridFocus = true
                    },
                    onLeft = {
                        runCatching { menuFocusRequester.requestFocus() }
                    },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(0.72f)
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
                            categoryRailExpanded = false
                            previewMovie = movie
                            TvContentCache
                                .movieFocusedId =
                                movie.localId
                            selectedCategoryId?.let {
                                TvContentCache.movieFocusedByCategory[it] = movie.localId
                            }
                            onContentFocused()
                        },
                        onExitLeft = {
                            categoryRailExpanded = true
                            movieBrowseScope.launch {
                                delay(70)
                                runCatching { movieCategoryRequester.requestFocus() }
                            }
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

        previewMovie?.let { movie ->
            BrowsePreviewPanel(
                item = movie,
                kind = "movie",
                locale = locale,
                isFavorite = store.isFavorite("movie-${movie.localId}"),
                modifier = Modifier.weight(0.28f).fillMaxHeight(),
                onOpen = {
                    target = ContentTarget("movie", movie.name, poster = movie.artwork, local = movie)
                },
                onToggleFavorite = {
                    store.toggleFavorite(
                        TvSavedItem("movie-${movie.localId}", "movie", movie.name, movie.streamUrl, movie.artwork),
                    )
                },
            )
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
    menuFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    val strings = remember(locale) { TvStrings(locale) }
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }
    val index = remember { TvLibraryIndex(context) }
    val restoreRequester = remember { FocusRequester() }
    val seriesGridEntryRequester = remember { FocusRequester() }
    val seriesCategoryRequester = remember { FocusRequester() }
    val seriesGridState = rememberLazyListState()
    val seriesBrowseScope = androidx.compose.runtime.rememberCoroutineScope()

    var categories by remember { mutableStateOf(TvContentCache.seriesCategories.orEmpty()) }
    var selectedCategoryId by remember { mutableStateOf(TvContentCache.seriesCategoryId) }
    var shows by remember { mutableStateOf<List<TvIndexedMedia>>(emptyList()) }
    var previewShow by remember { mutableStateOf<TvIndexedMedia?>(null) }
    var totalCount by remember { mutableIntStateOf(0) }
    var loadingCategories by remember { mutableStateOf(categories.isEmpty()) }
    var loadingPage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var categoryRailExpanded by remember { mutableStateOf(true) }

    var target by remember { mutableStateOf<ContentTarget?>(null) }
    var playingEpisode by remember { mutableStateOf<TvSavedItem?>(null) }
    var pendingSeriesGridFocus by remember { mutableStateOf(false) }
    var returnToSeriesGrid by remember { mutableStateOf(false) }
    var currentPlaylist by remember {
        mutableStateOf<List<Pair<TvSavedItem, PlaybackItem>>>(emptyList())
    }
    var playlistIndex by remember { mutableIntStateOf(0) }

    val pageSize = remember { TvPerformanceManager.pageSize(context) }

    fun loadPage(
        categoryId: String,
        reset: Boolean,
    ) {
        if (loadingPage && !reset) return

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
                    if (reset) previewShow = rows.firstOrNull()
                    loadingPage = false
                }
            }
        }.start()
    }

    fun selectCategory(categoryId: String) {
        TvContentCache.seriesCategoryId =
            categoryId
        TvContentCache.seriesFocusedId = TvContentCache.seriesFocusedByCategory[categoryId]
        selectedCategoryId = categoryId
        loadPage(
            categoryId,
            reset = true,
        )
    }

    LaunchedEffect(shows, pendingSeriesGridFocus, selectedCategoryId) {
        if (!pendingSeriesGridFocus || shows.isEmpty()) return@LaunchedEffect
        val rememberedIndex = shows.indexOfFirst { it.localId == TvContentCache.seriesFocusedId }
        seriesGridState.scrollToItem(rememberedIndex.coerceAtLeast(0) / 4)
        categoryRailExpanded = false
        delay(70)
        runCatching {
            if (rememberedIndex >= 0) restoreRequester.requestFocus()
            else seriesGridEntryRequester.requestFocus()
        }
        pendingSeriesGridFocus = false
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
                returnToSeriesGrid = true
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
            categoryRailExpanded = false
            val focusedIndex =
                shows.indexOfFirst { it.localId == TvContentCache.seriesFocusedId }
            if (focusedIndex >= 0) {
                seriesGridState.scrollToItem(focusedIndex / 4)
            }
            delay(if (returnToSeriesGrid) 180 else 120)
            runCatching {
                restoreRequester.requestFocus()
            }
            returnToSeriesGrid = false
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
                    .width(if (categoryRailExpanded) 220.dp else 0.dp)
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
                    modifier =
                        if (category.id == selectedCategoryId) {
                            Modifier.focusRequester(seriesCategoryRequester)
                        } else {
                            Modifier
                        },
                    onFocus = {
                        onContentFocused()
                        categoryRailExpanded = true

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
                        pendingSeriesGridFocus = true
                    },
                    onLeft = {
                        runCatching { menuFocusRequester.requestFocus() }
                    },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(0.70f)
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
                            categoryRailExpanded = false
                            previewShow = show
                            TvContentCache
                                .seriesFocusedId =
                                show.localId
                            selectedCategoryId?.let {
                                TvContentCache.seriesFocusedByCategory[it] = show.localId
                            }
                            onContentFocused()
                        },
                        onExitLeft = {
                            categoryRailExpanded = true
                            seriesBrowseScope.launch {
                                delay(70)
                                runCatching { seriesCategoryRequester.requestFocus() }
                            }
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

        previewShow?.let { show ->
            BrowsePreviewPanel(
                item = show,
                kind = "series",
                locale = locale,
                isFavorite = store.isFavorite("series-${show.localId}"),
                modifier = Modifier.weight(0.30f).fillMaxHeight(),
                onOpen = {
                    target = ContentTarget("series", show.name, poster = show.artwork, local = show)
                },
                onToggleFavorite = {
                    store.toggleFavorite(
                        TvSavedItem("series-${show.localId}", "series", show.name, show.streamUrl, show.artwork),
                    )
                },
            )
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

    DisposableEffect(target.kind, target.name) {
        onFullscreenStateChanged(true)
        onDispose { onFullscreenStateChanged(false) }
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

    val movieProgress =
        if (target.kind == "movie") {
            store.progressFor(favoriteId)
        } else {
            null
        }

    val seriesResumeProgress =
        if (target.kind == "series") {
            val prefix =
                "series-${seriesInfo?.series?.seriesId}-"
            store.continueWatching()
                .firstOrNull {
                    it.kind == "episode" &&
                        it.id.startsWith(prefix)
                }
        } else {
            null
        }
    val seriesResumeEpisode =
        seriesResumeProgress?.let { progress ->
            seriesInfo?.episodes?.firstOrNull {
                progress.id.endsWith("-${it.episodeId}")
            }
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
                        media?.name
                            ?: local.name,
                    url =
                        local.streamUrl,
                    artwork =
                        displayPoster,
                    subtitle = null,
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

    Box(Modifier.fillMaxSize().background(Color(0xFF05090F))) {
        media?.backdrop?.let { backdrop ->
            TvPosterImage(backdrop, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xF205090F), Color(0xCC05090F), Color(0x6605090F)),
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x2205090F), Color(0xFF05090F)),
                        ),
                    ),
            )
        }
        LazyColumn(
            state = detailListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                    horizontal = 24.dp,
                        vertical = 12.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                16.dp,
                    ),
                verticalAlignment =
                    Alignment.Top,
            ) {
                TvPosterImage(
                    displayPoster,
                    modifier =
                        Modifier
                            .width(
                                116.dp,
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
                            6.dp,
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
                                .headlineSmall,
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

                    movieProgress
                        ?.takeIf {
                            it.positionMs >= 15_000L
                        }
                        ?.let {
                            progress ->

                            PlaybackProgressSummary(
                                positionMs = progress.positionMs,
                                durationMs = progress.durationMs,
                            )
                        }

                    description?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            overview,
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

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
                                    if (
                                        seriesResumeEpisode != null &&
                                        seriesResumeProgress != null
                                    ) {
                                        "▶ S${seriesResumeEpisode.season} B${seriesResumeEpisode.episode} · " +
                                            "${(seriesResumeProgress.positionMs / 60_000L).coerceAtLeast(1L)} dk'dan devam"
                                    } else {
                                        "↓ Bölümler"
                                    },
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
                                if (
                                    seriesResumeEpisode != null &&
                                    seriesResumeProgress != null
                                ) {
                                    onPlay(
                                        seriesResumeProgress.copy(
                                            url = seriesResumeEpisode.streamUrl,
                                            artwork = displayPoster,
                                        ),
                                    )
                                } else {
                                    openEpisodes()
                                }
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
                                            "${media?.name ?: seriesInfo?.series?.name ?: target.name} • S${ep.season} E${ep.episode}",
                                        url =
                                            ep.streamUrl,
                                        artwork =
                                            displayPoster,
                                        subtitle =
                                            media?.name ?: target.name,
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

                        playlist
                            .take(selectedIndex.coerceAtLeast(0))
                            .forEach {
                                store.setWatched(
                                    it.first.id,
                                    true,
                                )
                            }

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

    LaunchedEffect(person) {
        if (person == null) {
            delay(140)
            runCatching { primaryFocusRequester.requestFocus() }
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
    val backFocusRequester = remember { FocusRequester() }
    BackHandler { onBack() }

    LaunchedEffect(person.id) {
        delay(100)
        runCatching { backFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ActionButton(
            text = "← ${strings["back"]}",
            modifier = Modifier.focusRequester(backFocusRequester),
            onClick = onBack,
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
            Arrangement.spacedBy(7.dp),
    ) {
        Text(
            strings["seasons_episodes"],
            color = Color.White,
            fontWeight =
                FontWeight.Bold,
            style =
                MaterialTheme
                    .typography
                    .titleSmall,
        )

        LazyRow(
            horizontalArrangement =
                Arrangement.spacedBy(7.dp),
        ) {
            itemsIndexed(
                seasons,
            ) {
                index,
                season ->

                SeasonTab(
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

            val progress =
                store.progressFor(savedId)

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
                    tmdb?.still,
                watched =
                    watched,
                progress =
                    progress,
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
private fun SeasonTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember(text) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                if (selected || focused) Color(0x2216C7FF) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (selected || focused) TvDesignTokens.Focus else TvDesignTokens.CardBorder,
                RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            color = if (selected || focused) Color.White else TvDesignTokens.TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium,
        )
    }

}

@Composable
private fun EpisodeCard(
    title: String,
    subtitle: String,
    description: String?,
    image: String?,
    watched: Boolean,
    progress: TvSavedItem?,
    onToggleWatched: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF151C28),
                    RoundedCornerShape(7.dp),
                )
                    .padding(5.dp),
        horizontalArrangement =
            Arrangement.spacedBy(
                7.dp,
            ),
    ) {
        TvPosterImage(
            image,
            modifier =
                Modifier
                    .width(96.dp)
                    .aspectRatio(
                        16f / 9f,
                    ),
        )

        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    2.dp,
                ),
        ) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                subtitle,
                color =
                    Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                description.orEmpty(),
                color =
                    Color(0xFFCBD5E1),
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )

            progress
                ?.takeIf {
                    it.positionMs >= 15_000L
                }
                ?.let {
                    PlaybackProgressSummary(
                        positionMs = it.positionMs,
                        durationMs = it.durationMs,
                    )
                }

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
private fun PlaybackProgressSummary(
    positionMs: Long,
    durationMs: Long,
) {
    val watchedMinutes =
        (positionMs / 60_000L)
            .coerceAtLeast(1L)
    val progress =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat())
                .coerceIn(0f, 1f)
        } else {
            0f
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$watchedMinutes dk izlendi · kaldığın yer",
            color = Color(0xFF60A5FA),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF22D3EE),
            trackColor = Color(0xFF263449),
        )
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
        Text(title, color = TvDesignTokens.TextSecondary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
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
            style = MaterialTheme.typography.titleMedium,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(rows, key = { "${it.kind}-${it.id}" }) { media ->
                val local = index.findByTitle(provider, media.kind, media.name)
                MediaCard(
                    title = media.name,
                    artwork = media.poster,
                    subtitle = availabilityText(strings, local != null),
                    modifier = Modifier.width(132.dp),
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

    // Çocuk profilinde aramayı da yalnızca çocuk kategorilerine kısıtla.
    val kidsMovieCategoryIds =
        remember(provider.server, provider.username) {
            if (!TvActiveScope.kidsMode) {
                emptySet()
            } else {
                index.loadVodCategories(provider)
                    .filter { TvProfilePolicy.isKidsCategory(it.name) }
                    .map { it.id }
                    .toSet()
            }
        }
    val kidsSeriesCategoryIds =
        remember(provider.server, provider.username) {
            if (!TvActiveScope.kidsMode) {
                emptySet()
            } else {
                index.loadSeriesCategories(provider)
                    .filter { TvProfilePolicy.isKidsCategory(it.name) }
                    .map { it.id }
                    .toSet()
            }
        }

    fun kidsAllowsResult(media: TvIndexedMedia): Boolean {
        if (!TvActiveScope.kidsMode) return true
        val allowed =
            if (media.kind == "series") {
                kidsSeriesCategoryIds
            } else {
                kidsMovieCategoryIds
            }
        return media.categoryId != null && allowed.contains(media.categoryId)
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
    var searchPlaylist by remember {
        mutableStateOf<List<Pair<TvSavedItem, PlaybackItem>>>(emptyList())
    }
    var searchPlaylistIndex by remember { mutableIntStateOf(0) }
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
                            ) &&
                                kidsAllowsResult(it.media)
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
            playlist = searchPlaylist,
            playlistStartIndex = searchPlaylistIndex,
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
                searchPlaylist = emptyList()
                searchPlaylistIndex = 0
                playing = it
            },
            onEpisodePlaylist = {
                playlist,
                indexPosition ->

                searchPlaylist = playlist
                searchPlaylistIndex = indexPosition
                playing =
                    playlist
                        .getOrNull(indexPosition)
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
                .onKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyUp &&
                        (
                            event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.NumPadEnter
                        )
                    ) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                .focusable()
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
                        6,
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
                            6 - chunk.size,
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
private fun HomeFeaturedHero(
    mediaRows: List<TvTmdbMedia>,
    onOpen: (TvTmdbMedia) -> Unit,
) {
    var selectedIndex by remember(mediaRows) { mutableIntStateOf(0) }
    val media = mediaRows[selectedIndex.coerceIn(0, mediaRows.lastIndex)]
    var clock by remember { mutableStateOf("") }
    var buttonFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(TvDesignTokens.HeroHeight)
            .clip(RoundedCornerShape(7.dp)).background(TvDesignTokens.Background),
    ) {
        TvPosterImage(media.backdrop ?: media.poster, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF020B12), Color(0xF0020B12), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3020B12)))))
        Column(
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.57f).padding(start = 24.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(media.name, color = TvDesignTokens.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(media.kind.takeIf { it.isNotBlank() }?.replaceFirstChar { c -> c.uppercase() }, media.year, media.rating?.let { "★ %.1f".format(it) }).joinToString("  •  "), color = TvDesignTokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
            media.overview?.let { Text(it, color = TvDesignTokens.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
            Box(
                modifier = Modifier.width(132.dp).height(36.dp)
                    .background(if (buttonFocused) Color(0x3322D3EE) else Color(0x99101A22), RoundedCornerShape(5.dp))
                    .border(1.dp, if (buttonFocused) TvDesignTokens.Focus else TvDesignTokens.TextTertiary, RoundedCornerShape(5.dp))
                    .onFocusChanged { buttonFocused = it.isFocused }
                    .onKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> { selectedIndex = (selectedIndex - 1 + mediaRows.size) % mediaRows.size; true }
                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> { selectedIndex = (selectedIndex + 1) % mediaRows.size; true }
                            event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter) -> { onOpen(media); true }
                            else -> false
                        }
                    }.focusable(),
                contentAlignment = Alignment.Center,
            ) { Text("Detayları Gör", color = TvDesignTokens.TextPrimary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge) }
        }
        Text(clock, modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 22.dp), color = Color.White, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            repeat(mediaRows.size) { index -> Box(Modifier.size(if (index == selectedIndex) 7.dp else 5.dp).background(if (index == selectedIndex) Color.White else Color(0xFF64748B), RoundedCornerShape(50))) }
        }
    }
}

@Composable
private fun LiveHomeRail(
    channels: List<NativeLiveChannel>,
    epgLabels: Map<String, Pair<String, String>>,
    onClick: (NativeLiveChannel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(TvDesignTokens.Background), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Canlı Şimdi", color = TvDesignTokens.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(channels, key = { it.id }) { channel ->
                var focused by remember(channel.id) { mutableStateOf(false) }
                val epg = epgLabels[channel.id]
                Column(
                    modifier = Modifier.width(TvDesignTokens.LiveCardWidth).height(TvDesignTokens.LiveCardHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvDesignTokens.Card)
                        .border(1.dp, if (focused) TvDesignTokens.Focus else TvDesignTokens.CardBorder, RoundedCornerShape(6.dp))
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onClick(channel) }.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    TvLogoImage(channel.logo, Modifier.fillMaxWidth().height(40.dp))
                    Text(cleanChannelName(channel.name), color = TvDesignTokens.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    if (epg != null) {
                        Text(epg.first, color = TvDesignTokens.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        Text(epg.second, color = TvDesignTokens.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

    }
}

@Composable
private fun HomeSportsRail(
    events: List<SportsEvent>,
    channels: List<NativeLiveChannel>,
    onClick: (SportsEvent) -> Unit,
) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val channelIndex = remember(channels) { SportsChannelIndex.forChannels(channels) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Bugünün Sporları", color = TvDesignTokens.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events, key = { it.id }) { event ->
                var focused by remember(event.id) { mutableStateOf(false) }
                val matched = remember(event.id, event.broadcasts, channelIndex) {
                    channelIndex.resolve(SportsBroadcastResolver.options(event))
                }
                Column(
                    modifier = Modifier.width(242.dp).height(116.dp).clip(RoundedCornerShape(7.dp))
                        .background(TvDesignTokens.Card)
                        .border(1.dp, if (focused) TvDesignTokens.Focus else TvDesignTokens.CardBorder, RoundedCornerShape(7.dp))
                        .onFocusChanged { focused = it.isFocused }.clickable { onClick(event) }.padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("${sportsCountryFlag(event.country)} ${event.league}  •  ${clock.format(Date(event.startMs))}", color = TvDesignTokens.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        TvLogoImage(event.homeBadge, Modifier.size(30.dp))
                        Text(event.home, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(if (event.homeScore != null || event.awayScore != null) "${event.homeScore ?: "–"}:${event.awayScore ?: "–"}" else "vs", color = TvDesignTokens.Focus, fontWeight = FontWeight.Bold)
                        Text(event.away, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TvLogoImage(event.awayBadge, Modifier.size(30.dp))
                    }
                    Text(
                        matched.firstOrNull()?.let { "📺 ${it.canonicalName} · ${it.sources.size} kaynak${if (matched.size > 1) " +${matched.size - 1} kanal" else ""}" }
                            ?: if (event.broadcasts.isEmpty()) "Yayın seçeneği bulunamadı" else "Kanal eşleşmedi",
                        color = if (matched.isNotEmpty()) TvDesignTokens.Focus else TvDesignTokens.TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun sportsCountryFlag(country: String?): String = when (country?.lowercase(Locale.ROOT)) {
    "turkey", "türkiye" -> "🇹🇷"
    "england" -> "🇬🇧"
    "spain" -> "🇪🇸"
    "germany" -> "🇩🇪"
    "italy" -> "🇮🇹"
    "france" -> "🇫🇷"
    "portugal" -> "🇵🇹"
    "netherlands" -> "🇳🇱"
    "brazil" -> "🇧🇷"
    else -> "🌍"
}

@Composable
private fun PosterHomeRail(
    title: String,
    cards: List<HomeCard>,
    onClick: (HomeCard) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(TvDesignTokens.Background), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = TvDesignTokens.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(cards.take(10), key = { it.id }) { card ->
                var focused by remember(card.id) { mutableStateOf(false) }
                Box(
                    modifier = Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (focused) Color(0xFF009BFF) else Color.Transparent, RoundedCornerShape(7.dp))
                        .padding(if (focused) 3.dp else 0.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onClick(card) },
                ) { TvPosterImage(card.artwork, Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) }
            }
        }
    }
}

@Composable
private fun ContinueHomeRail(
    rows: List<TvSavedItem>,
    onClick: (TvSavedItem) -> Unit,
    onRemoveContinue: (TvSavedItem) -> Unit,
    onRemoveHistory: (TvSavedItem) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var menuItem by remember { mutableStateOf<TvSavedItem?>(null) }
    menuItem?.let { row ->
        AlertDialog(
            onDismissRequest = { menuItem = null },
            title = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text("Bu içerik için yapmak istediğiniz işlemi seçin.") },
            confirmButton = {
                TextButton(onClick = { onRemoveContinue(row); menuItem = null }) { Text("Bu listeden kaldır") }
            },
            dismissButton = {
                TextButton(onClick = { onRemoveHistory(row); menuItem = null }) { Text("Oynatma geçmişinden kaldır") }
            },
        )
    }
    Column(modifier = Modifier.fillMaxWidth().background(TvDesignTokens.Background), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Kaldığın Yerden Devam Et", color = TvDesignTokens.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows, key = { it.id }) { row ->
                var focused by remember(row.id) { mutableStateOf(false) }
                var longPressed by remember(row.id) { mutableStateOf(false) }
                var longPressJob by remember(row.id) { mutableStateOf<Job?>(null) }
                Column(
                    modifier = Modifier.width(154.dp).height(88.dp).clip(RoundedCornerShape(6.dp))
                        .background(TvDesignTokens.Card).border(1.dp, if (focused) TvDesignTokens.Focus else TvDesignTokens.CardBorder, RoundedCornerShape(6.dp))
                        .onFocusChanged {
                            focused = it.isFocused
                            if (!it.isFocused) {
                                longPressJob?.cancel()
                                longPressJob = null
                                longPressed = false
                            }
                        }
                        .onKeyEvent { event ->
                            val center = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                            when {
                                center && event.type == KeyEventType.KeyDown && longPressJob == null -> {
                                    longPressJob = scope.launch {
                                        delay(700)
                                        longPressed = true
                                        menuItem = row
                                    }
                                    true
                                }
                                center && event.type == KeyEventType.KeyUp -> {
                                    longPressJob?.cancel()
                                    longPressJob = null
                                    if (!longPressed) onClick(row)
                                    longPressed = false
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable(),
                ) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        TvPosterImage(row.artwork, Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        Text(row.subtitle ?: row.name, Modifier.align(Alignment.BottomStart).background(Color(0xB3020A10)).padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                    val progress = if (row.durationMs > 0L) (row.positionMs.toFloat() / row.durationMs).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = TvDesignTokens.Focus, trackColor = Color(0xFF26333D))
                }
            }
        }
    }
}

private fun cleanChannelName(name: String): String =
    name.replace(Regex("[\\*|_]+"), " ")
        .replace(Regex("^(TR|TURKEY|TURKIYE)\\s*[:.-]?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(UHD|FHD|FULL\\s*HD|HD|4K|SD|HEVC|H265|RAW)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ").trim()

private fun liveChannelPriority(name: String): Int {
    val normalized = cleanChannelName(name).uppercase(Locale.ROOT).replace(" ", "")
    return when {
        normalized.contains("TRT1") -> 0
        normalized.contains("SHOWTV") -> 1
        normalized.startsWith("ATV") -> 2
        normalized.contains("BEINSPORTS1") -> 3
        normalized.contains("TV8") -> 4
        normalized.contains("KANALD") -> 5
        else -> 100
    }
}

private fun liveChannelGroup(name: String): String {
    val normalized = cleanChannelName(name).uppercase(Locale.ROOT).replace(" ", "")
    return when {
        normalized.contains("TRT1") -> "trt1"
        normalized.contains("SHOWTV") -> "showtv"
        normalized.startsWith("ATV") -> "atv"
        normalized.contains("BEINSPORTS1") -> "beinsports1"
        normalized.contains("TV8") -> "tv8"
        normalized.contains("KANALD") -> "kanald"
        else -> normalized.replace(Regex("(UHD|FHD|HD|HEVC|H265|RAW|4K)$"), "")
    }
}

@Composable
private fun BrowsePreviewPanel(
    item: TvIndexedMedia,
    kind: String,
    locale: TvLocale,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var favorite by remember(item.localId, isFavorite) { mutableStateOf(isFavorite) }
    var detail by remember(item.localId) { mutableStateOf<TvTmdbDetail?>(null) }
    val tmdb = remember { TvTmdbClient() }
    LaunchedEffect(item.localId, locale) {
        delay(180)
        Thread {
            val loaded = tmdb.detail(item.name, kind, locale).getOrNull()
            Handler(Looper.getMainLooper()).post { detail = loaded }
        }.start()
    }
    val media = detail?.media
    Column(
        modifier = modifier
            .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
            .background(Color(0xFF0B1420), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvPosterImage(
            media?.poster ?: item.artwork,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(108.dp)
                    .aspectRatio(2f / 3f),
        )
        Text(media?.name ?: item.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val metadata = listOfNotNull(
            media?.year,
            media?.genres?.take(2)?.joinToString(" • ")?.takeIf { it.isNotBlank() },
            media?.rating?.let { "★ %.1f".format(it) },
            media?.seasonCount?.let { "$it Sezon" },
        ).joinToString(" • ")
        if (metadata.isNotBlank()) Text(metadata, color = Color(0xFF60A5FA), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        media?.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = Color(0xFFD1D8E3),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 9,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        ActionButton(text = "Detaya Git", selected = true, modifier = Modifier.fillMaxWidth(), onClick = onOpen)
        ActionButton(
            text = if (favorite) "✓  Listemde" else "♡  Listeme Ekle",
            modifier = Modifier.fillMaxWidth(),
        ) {
            onToggleFavorite()
            favorite = !favorite
        }
    }
}

@Composable
private fun HomeRail(
    title: String,
    cards: List<HomeCard>,
    onClick: (HomeCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(cards, key = { it.id }) { card ->
                MediaCard(
                    title = card.title,
                    artwork = card.artwork,
                    subtitle = card.subtitle,
                    modifier = Modifier.width(112.dp),
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
    onExitLeft: () -> Unit,
    onClick: (TvIndexedMedia) -> Unit,
) {
    val columnCount = 4
    val chunks =
        remember(rows) {
            rows.chunked(columnCount)
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
            targetIndex / columnCount

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
                        rowIndex * columnCount +
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
                                .focusRequester(ownRequester)
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
                                        onExitLeft()
                                        true
                                    }
                                }

                                Key.DirectionDown -> {
                                    val nextRowStart =
                                        (rowIndex + 1) *
                                            columnCount

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
                                            columnCount +
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
                    columnCount - row.size,
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
                .padding(5.dp),
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
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onRight: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
) {
    var focused by
        remember(title) {
            mutableStateOf(false)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    when {
                        focused ->
                            Color(
                                0xFF0E7490,
                            )

                        selected ->
                            Color(
                                0xFF134E4A,
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
                            event.key == Key.DirectionLeft &&
                            onLeft != null -> {
                            onLeft()
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
                                0xFF0E7490,
                            )

                        selected ->
                            Color(
                                0xFF0F766E,
                            )

                        else ->
                            Color(
                                0xFF1E293B,
                            )
                    },
                    RoundedCornerShape(6.dp),
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
                        12.dp,
                    vertical =
                        8.dp,
                ),
    ) {
        Text(
            text =
                text,
            color =
                Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall,
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
