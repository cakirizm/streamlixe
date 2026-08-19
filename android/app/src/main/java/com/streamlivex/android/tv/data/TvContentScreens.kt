@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamlivex.android.tv.content

import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackPreferences
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.NativeSeriesCategory
import com.streamlivex.android.tv.data.NativeSeriesEpisode
import com.streamlivex.android.tv.data.NativeSeriesInfo
import com.streamlivex.android.tv.data.NativeSeriesItem
import com.streamlivex.android.tv.data.NativeVodCategory
import com.streamlivex.android.tv.data.NativeVodItem
import com.streamlivex.android.tv.data.TvContentCache
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.TvSavedItem
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.player.TvNativePlayer

@Composable
fun TvHomeScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }

    var moviePreview by remember { mutableStateOf<List<NativeVodItem>>(emptyList()) }
    var seriesPreview by remember { mutableStateOf<List<NativeSeriesItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf<TvSavedItem?>(null) }

    LaunchedEffect(provider.server, provider.username) {
        Thread {
            val movieCategories = client.loadVodCategories(provider).getOrDefault(emptyList())
            val seriesCategories = client.loadSeriesCategories(provider).getOrDefault(emptyList())

            val movies =
                movieCategories.firstOrNull()?.let {
                    client.loadVodCategory(provider, it.id, maxItems = 8).getOrDefault(emptyList())
                }.orEmpty()

            val series =
                seriesCategories.firstOrNull()?.let {
                    client.loadSeriesCategory(provider, it.id, maxItems = 8).getOrDefault(emptyList())
                }.orEmpty()

            Handler(Looper.getMainLooper()).post {
                moviePreview = movies
                seriesPreview = series
                loading = false
            }
        }.start()
    }

    if (playing != null) {
        val item = playing!!
        TvNativePlayer(
            saved = item,
            request = PlaybackRequest(
                sessionId = "tv-home-${item.id}",
                item = PlaybackItem(item.name, item.url, item.kind),
                resumeTimeMs = item.positionMs,
                preferences = PlaybackPreferences(showInfo = true),
            ),
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            store = store,
            onFullscreenStateChanged = onFullscreenStateChanged,
            onClose = { playing = null },
        )
        return
    }

    if (loading && moviePreview.isEmpty() && seriesPreview.isEmpty()) {
        LoadingState("Ana Sayfa hazırlanıyor")
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
                PosterRail(
                    title = "Devam Et",
                    cards = continueRows.take(8).map {
                        PosterCardModel(it.id, it.name, it.artwork, it.subtitle.orEmpty())
                    },
                    onClick = { model ->
                        playing = continueRows.firstOrNull { it.id == model.id }
                    },
                )
            }
        }

        if (moviePreview.isNotEmpty()) {
            item {
                PosterRail(
                    title = "Filmler",
                    cards = moviePreview.map {
                        PosterCardModel(
                            it.id,
                            it.name,
                            it.poster,
                            listOfNotNull(it.year, it.rating?.let { r -> "★ $r" }).joinToString(" • "),
                        )
                    },
                    onClick = { },
                )
            }
        }

        if (seriesPreview.isNotEmpty()) {
            item {
                PosterRail(
                    title = "Diziler",
                    cards = seriesPreview.map {
                        PosterCardModel(
                            it.id,
                            it.name,
                            it.cover,
                            listOfNotNull(it.year, it.rating?.let { r -> "★ $r" }).joinToString(" • "),
                        )
                    },
                    onClick = { },
                )
            }
        }
    }
}

@Composable
fun TvMoviesScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }

    var categories by remember {
        mutableStateOf(TvContentCache.vodCategories.orEmpty())
    }
    var selectedCategoryId by remember {
        mutableStateOf(TvContentCache.movieCategoryId)
    }
    var movies by remember {
        mutableStateOf(
            selectedCategoryId?.let { TvContentCache.vodByCategory[it] }.orEmpty(),
        )
    }
    var loadingCategories by remember { mutableStateOf(categories.isEmpty()) }
    var loadingMovies by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var detailItem by remember { mutableStateOf<NativeVodItem?>(null) }
    var playing by remember { mutableStateOf<NativeVodItem?>(null) }
    var refreshFavorite by remember { mutableIntStateOf(0) }

    fun loadCategory(categoryId: String) {
        TvContentCache.movieCategoryId = categoryId
        selectedCategoryId = categoryId
        TvContentCache.vodByCategory[categoryId]?.let {
            movies = it
            loadingMovies = false
            return
        }

        loadingMovies = true
        Thread {
            val result = client.loadVodCategory(provider, categoryId)
            Handler(Looper.getMainLooper()).post {
                result
                    .onSuccess { movies = it }
                    .onFailure { error = it.message.orEmpty() }
                loadingMovies = false
            }
        }.start()
    }

    LaunchedEffect(provider.server, provider.username) {
        if (categories.isNotEmpty()) {
            if (selectedCategoryId == null) {
                selectedCategoryId = categories.firstOrNull()?.id
            }
            selectedCategoryId?.let { loadCategory(it) }
            return@LaunchedEffect
        }

        Thread {
            val result = client.loadVodCategories(provider)
            Handler(Looper.getMainLooper()).post {
                result
                    .onSuccess { rows ->
                        categories = rows
                        loadingCategories = false
                        val target =
                            TvContentCache.movieCategoryId
                                ?: rows.firstOrNull()?.id
                        if (target != null) loadCategory(target)
                    }
                    .onFailure {
                        loadingCategories = false
                        error = it.message.orEmpty()
                    }
            }
        }.start()
    }

    if (playing != null) {
        val item = playing!!
        val saved = item.toSaved()
        TvNativePlayer(
            saved = saved,
            request = PlaybackRequest(
                sessionId = "tv-movie-${item.streamId}",
                item = PlaybackItem(item.name, item.streamUrl, "movie"),
                resumeTimeMs = store.progressFor(item.id)?.positionMs ?: 0L,
                preferences = PlaybackPreferences(showInfo = true),
            ),
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            store = store,
            onFullscreenStateChanged = onFullscreenStateChanged,
            onClose = { playing = null },
        )
        return
    }

    if (detailItem != null) {
        MovieDetailScreen(
            item = detailItem!!,
            favorite = store.isFavorite(detailItem!!.id),
            onPlay = { playing = detailItem },
            onFavorite = {
                store.toggleFavorite(detailItem!!.toSaved())
                refreshFavorite += 1
            },
            onBack = { detailItem = null },
        )
        return
    }

    if (loadingCategories && categories.isEmpty()) {
        LoadingState("Film kategorileri yükleniyor")
        return
    }

    Row(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)),
    ) {
        LazyColumn(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0xFF101722))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                FocusRow(
                    title = category.name,
                    selected = category.id == selectedCategoryId,
                    onFocus = { onContentFocused() },
                    onClick = {
                        onContentFocused()
                        if (category.id != selectedCategoryId) loadCategory(category.id)
                    },
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            when {
                error.isNotBlank() -> ErrorState(error)
                loadingMovies && movies.isEmpty() -> LoadingState("Filmler yükleniyor")
                else -> {
                    PosterGrid(
                        movieCards = movies,
                        onFocus = { movie ->
                            TvContentCache.movieFocusedId = movie.id
                            onContentFocused()
                        },
                        onClick = { movie ->
                            TvContentCache.movieDetailId = movie.id
                            detailItem = movie
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun TvSeriesScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }

    var categories by remember {
        mutableStateOf(TvContentCache.seriesCategories.orEmpty())
    }
    var selectedCategoryId by remember {
        mutableStateOf(TvContentCache.seriesCategoryId)
    }
    var shows by remember {
        mutableStateOf(
            selectedCategoryId?.let { TvContentCache.seriesByCategory[it] }.orEmpty(),
        )
    }
    var loadingCategories by remember { mutableStateOf(categories.isEmpty()) }
    var loadingShows by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var detailItem by remember { mutableStateOf<NativeSeriesItem?>(null) }
    var seriesInfo by remember { mutableStateOf<NativeSeriesInfo?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var playingEpisode by remember {
        mutableStateOf<Pair<NativeSeriesItem, NativeSeriesEpisode>?>(null)
    }
    var refreshFavorite by remember { mutableIntStateOf(0) }

    fun loadCategory(categoryId: String) {
        TvContentCache.seriesCategoryId = categoryId
        selectedCategoryId = categoryId

        TvContentCache.seriesByCategory[categoryId]?.let {
            shows = it
            loadingShows = false
            return
        }

        loadingShows = true
        Thread {
            val result = client.loadSeriesCategory(provider, categoryId)
            Handler(Looper.getMainLooper()).post {
                result
                    .onSuccess { shows = it }
                    .onFailure { error = it.message.orEmpty() }
                loadingShows = false
            }
        }.start()
    }

    fun openSeries(series: NativeSeriesItem) {
        detailItem = series
        TvContentCache.seriesDetailId = series.id
        TvContentCache.seriesDetails[series.seriesId]?.let {
            seriesInfo = it
            return
        }

        detailLoading = true
        Thread {
            val result = client.loadSeriesInfo(provider, series)
            Handler(Looper.getMainLooper()).post {
                result
                    .onSuccess { seriesInfo = it }
                    .onFailure { error = it.message.orEmpty() }
                detailLoading = false
            }
        }.start()
    }

    LaunchedEffect(provider.server, provider.username) {
        if (categories.isNotEmpty()) {
            if (selectedCategoryId == null) selectedCategoryId = categories.firstOrNull()?.id
            selectedCategoryId?.let { loadCategory(it) }
            return@LaunchedEffect
        }

        Thread {
            val result = client.loadSeriesCategories(provider)
            Handler(Looper.getMainLooper()).post {
                result
                    .onSuccess { rows ->
                        categories = rows
                        loadingCategories = false
                        val target =
                            TvContentCache.seriesCategoryId
                                ?: rows.firstOrNull()?.id
                        if (target != null) loadCategory(target)
                    }
                    .onFailure {
                        loadingCategories = false
                        error = it.message.orEmpty()
                    }
            }
        }.start()
    }

    if (playingEpisode != null) {
        val pair = playingEpisode!!
        val series = pair.first
        val episode = pair.second
        val allEpisodes = seriesInfo?.episodes.orEmpty()
        val startIndex = allEpisodes.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)

        val playlist = allEpisodes.map { ep ->
            val saved = TvSavedItem(
                id = "series-${series.seriesId}-${ep.episodeId}",
                kind = "episode",
                name = "${series.name} • S${ep.season} E${ep.episode}",
                url = ep.streamUrl,
                artwork = series.cover,
                subtitle = series.name,
            )
            saved to PlaybackItem(
                name = saved.name,
                url = ep.streamUrl,
                kind = "episode",
                hasNext = ep != allEpisodes.lastOrNull(),
            )
        }

        val saved = playlist.getOrNull(startIndex)?.first ?: TvSavedItem(
            id = "series-${series.seriesId}-${episode.episodeId}",
            kind = "episode",
            name = "${series.name} • S${episode.season} E${episode.episode}",
            url = episode.streamUrl,
            artwork = series.cover,
            subtitle = series.name,
        )

        TvNativePlayer(
            saved = saved,
            request = PlaybackRequest(
                sessionId = "tv-series-${series.seriesId}",
                item = PlaybackItem(saved.name, saved.url, "episode"),
                resumeTimeMs = store.progressFor(saved.id)?.positionMs ?: 0L,
                preferences = PlaybackPreferences(showInfo = true),
            ),
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            store = store,
            onFullscreenStateChanged = onFullscreenStateChanged,
            onClose = { playingEpisode = null },
            playlist = playlist,
            playlistStartIndex = startIndex,
        )
        return
    }

    if (detailItem != null) {
        SeriesDetailScreen(
            series = detailItem!!,
            info = seriesInfo,
            loading = detailLoading,
            favorite = store.isFavorite(detailItem!!.id),
            onFavorite = {
                store.toggleFavorite(
                    TvSavedItem(
                        id = detailItem!!.id,
                        kind = "series",
                        name = detailItem!!.name,
                        url = "",
                        artwork = detailItem!!.cover,
                        subtitle = detailItem!!.genre,
                    ),
                )
                refreshFavorite += 1
            },
            onEpisode = { playingEpisode = detailItem!! to it },
            onBack = {
                detailItem = null
                seriesInfo = null
            },
        )
        return
    }

    if (loadingCategories && categories.isEmpty()) {
        LoadingState("Dizi kategorileri yükleniyor")
        return
    }

    Row(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)),
    ) {
        LazyColumn(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0xFF101722))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                FocusRow(
                    title = category.name,
                    selected = category.id == selectedCategoryId,
                    onFocus = { onContentFocused() },
                    onClick = {
                        onContentFocused()
                        if (category.id != selectedCategoryId) loadCategory(category.id)
                    },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when {
                error.isNotBlank() -> ErrorState(error)
                loadingShows && shows.isEmpty() -> LoadingState("Diziler yükleniyor")
                else -> {
                    SeriesPosterGrid(
                        rows = shows,
                        onFocus = {
                            TvContentCache.seriesFocusedId = it.id
                            onContentFocused()
                        },
                        onClick = { openSeries(it) },
                    )
                }
            }
        }
    }
}

@Composable
fun TvSearchScreen(
    provider: TvProviderConfig,
    onContentFocused: () -> Unit,
) {
    // Search now searches only already cached category data. This is intentional:
    // never load every VOD + Series row just to build a search index.
    var query by remember { mutableStateOf("") }

    val cachedMovies = remember(TvContentCache.vodByCategory.size) {
        TvContentCache.vodByCategory.values.flatten().distinctBy { it.id }
    }
    val cachedSeries = remember(TvContentCache.seriesByCategory.size) {
        TvContentCache.seriesByCategory.values.flatten().distinctBy { it.id }
    }

    val q = query.trim()
    val movieResults =
        if (q.length < 2) emptyList()
        else cachedMovies.filter { it.name.contains(q, true) }.take(30)
    val seriesResults =
        if (q.length < 2) emptyList()
        else cachedSeries.filter { it.name.contains(q, true) }.take(30)

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Ara",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Yüklenen film ve dizilerde ara") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text(
            "Arama belleği korumak için yalnızca açılmış kategorilerde çalışır.",
            color = Color(0xFF64748B),
            style = MaterialTheme.typography.bodySmall,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (movieResults.isNotEmpty()) {
                item {
                    Text("Filmler", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                }
                items(movieResults, key = { it.id }) {
                    SimpleResultRow(it.name, listOfNotNull(it.year, it.genre).joinToString(" • "))
                }
            }
            if (seriesResults.isNotEmpty()) {
                item {
                    Text("Diziler", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                }
                items(seriesResults, key = { it.id }) {
                    SimpleResultRow(it.name, listOfNotNull(it.year, it.genre).joinToString(" • "))
                }
            }
        }
    }
}

@Composable
fun TvMyListScreen() {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    val rows = remember { store.favorites() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Listem",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )

        if (rows.isEmpty()) {
            Text("Henüz favori eklenmedi.", color = Color(0xFF94A3B8))
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
                        TvPosterImage(
                            item.artwork,
                            modifier = Modifier.width(58.dp).height(84.dp),
                        )
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

private data class PosterCardModel(
    val id: String,
    val title: String,
    val artwork: String?,
    val subtitle: String,
)

@Composable
private fun PosterRail(
    title: String,
    cards: List<PosterCardModel>,
    onClick: (PosterCardModel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                PosterCard(
                    card = card,
                    onClick = { onClick(card) },
                )
            }
        }
    }
}

@Composable
private fun PosterCard(
    card: PosterCardModel,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(170.dp)
            .background(
                if (focused) Color(0xFF2563EB) else Color(0xFF151C28),
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TvPosterImage(
            url = card.artwork,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        Text(
            card.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            card.subtitle,
            color = if (focused) Color.White else Color(0xFF94A3B8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PosterGrid(
    movieCards: List<NativeVodItem>,
    onFocus: (NativeVodItem) -> Unit,
    onClick: (NativeVodItem) -> Unit,
) {
    // 5 columns by using rows of 5; LazyColumn only composes visible rows.
    val rows = remember(movieCards) { movieCards.chunked(5) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEach { movie ->
                    var focused by remember(movie.id) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (focused) Color(0xFF2563EB) else Color(0xFF151C28),
                                RoundedCornerShape(10.dp),
                            )
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) onFocus(movie)
                            }
                            .clickable { onClick(movie) }
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TvPosterImage(
                            movie.poster,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                        )
                        Text(
                            movie.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(movie.year, movie.rating?.let { "★ $it" }).joinToString(" • "),
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                repeat(5 - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SeriesPosterGrid(
    rows: List<NativeSeriesItem>,
    onFocus: (NativeSeriesItem) -> Unit,
    onClick: (NativeSeriesItem) -> Unit,
) {
    val chunks = remember(rows) { rows.chunked(5) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(chunks) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEach { show ->
                    var focused by remember(show.id) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (focused) Color(0xFF2563EB) else Color(0xFF151C28),
                                RoundedCornerShape(10.dp),
                            )
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) onFocus(show)
                            }
                            .clickable { onClick(show) }
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TvPosterImage(
                            show.cover,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                        )
                        Text(
                            show.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(show.year, show.rating?.let { "★ $it" }).joinToString(" • "),
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                repeat(5 - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MovieDetailScreen(
    item: NativeVodItem,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    Row(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        TvPosterImage(
            item.poster,
            modifier = Modifier.width(270.dp).aspectRatio(2f / 3f),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                item.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                listOfNotNull(item.year, item.genre, item.rating?.let { "★ $it" }).joinToString(" • "),
                color = Color(0xFF94A3B8),
            )
            Text(
                item.plot ?: "Açıklama bulunamadı.",
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodyLarge,
            )
            ActionButton("▶ Oynat", onPlay)
            ActionButton(
                if (favorite) "★ Listemden çıkar" else "☆ Listeme ekle",
                onFavorite,
            )
        }
    }
}

@Composable
private fun SeriesDetailScreen(
    series: NativeSeriesItem,
    info: NativeSeriesInfo?,
    loading: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onEpisode: (NativeSeriesEpisode) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    Row(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Column(
            modifier = Modifier.width(260.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvPosterImage(
                series.cover,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
            ActionButton(
                if (favorite) "★ Listemden çıkar" else "☆ Listeme ekle",
                onFavorite,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                series.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                listOfNotNull(series.year, series.genre, series.rating?.let { "★ $it" }).joinToString(" • "),
                color = Color(0xFF94A3B8),
            )
            Text(
                series.plot ?: "Açıklama bulunamadı.",
                color = Color(0xFFCBD5E1),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            if (loading) {
                CircularProgressIndicator()
            } else {
                val episodes = info?.episodes.orEmpty()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(episodes, key = { it.id }) { episode ->
                        FocusRow(
                            title = "S${episode.season} E${episode.episode} • ${episode.name}",
                            selected = false,
                            onFocus = {},
                            onClick = { onEpisode(episode) },
                        )
                    }
                }
            }
        }
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
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(240.dp)
            .background(
                if (focused) Color(0xFF2563EB) else Color(0xFF1E293B),
                RoundedCornerShape(9.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SimpleResultRow(
    title: String,
    subtitle: String,
) {
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

private fun NativeVodItem.toSaved() =
    TvSavedItem(
        id = id,
        kind = "movie",
        name = name,
        url = streamUrl,
        artwork = poster,
        subtitle = listOfNotNull(year, genre).joinToString(" • "),
    )

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
