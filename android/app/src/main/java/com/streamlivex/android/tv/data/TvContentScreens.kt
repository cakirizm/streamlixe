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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.streamlivex.android.NativePlayerSurface
import com.streamlivex.android.PlaybackItem
import com.streamlivex.android.PlaybackPreferences
import com.streamlivex.android.PlaybackRequest
import com.streamlivex.android.tv.data.NativeSeriesCategory
import com.streamlivex.android.tv.data.NativeSeriesEpisode
import com.streamlivex.android.tv.data.NativeSeriesInfo
import com.streamlivex.android.tv.data.NativeSeriesItem
import com.streamlivex.android.tv.data.NativeVodCategory
import com.streamlivex.android.tv.data.NativeVodItem
import com.streamlivex.android.tv.data.TvContentStore
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.TvSavedItem
import com.streamlivex.android.tv.data.XtreamClient
import com.streamlivex.android.tv.data.XtreamSeriesLibrary
import com.streamlivex.android.tv.data.XtreamVodLibrary

@Composable
fun TvHomeScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }

    var vod by remember { mutableStateOf<XtreamVodLibrary?>(null) }
    var series by remember { mutableStateOf<XtreamSeriesLibrary?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(provider.server, provider.username) {
        Thread {
            val vodResult = client.loadVodLibrary(provider).getOrNull()
            val seriesResult = client.loadSeriesLibrary(provider).getOrNull()
            Handler(Looper.getMainLooper()).post {
                vod = vodResult
                series = seriesResult
                loading = false
            }
        }.start()
    }

    if (loading) {
        LoadingState("Ana Sayfa hazırlanıyor")
        return
    }

    var playing by remember { mutableStateOf<TvSavedItem?>(null) }
    if (playing != null) {
        SavedItemPlayer(
            item = playing!!,
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            externalPlayerKeyEvent = externalPlayerKeyEvent,
            onFullscreenStateChanged = onFullscreenStateChanged,
            store = store,
            onClose = { playing = null },
        )
        return
    }

    val continueItems = store.continueWatching()
    val topMovies =
        vod?.items.orEmpty()
            .sortedByDescending { it.rating ?: 0.0 }
            .take(12)
    val topSeries =
        series?.items.orEmpty()
            .sortedByDescending { it.rating ?: 0.0 }
            .take(12)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D111B))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "StreamLiveX",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
        )

        if (continueItems.isNotEmpty()) {
            HomeSection(
                title = "Devam Et",
                items = continueItems.map { it.name to it.subtitle.orEmpty() },
                onClick = { index -> playing = continueItems[index] },
            )
        }

        HomeSection(
            title = "Öne Çıkan Filmler",
            items = topMovies.map {
                it.name to listOfNotNull(it.year, it.rating?.let { r -> "★ $r" }).joinToString(" • ")
            },
            onClick = { index ->
                val movie = topMovies[index]
                playing = movie.toSaved()
            },
        )

        HomeSection(
            title = "Öne Çıkan Diziler",
            items = topSeries.map {
                it.name to listOfNotNull(it.year, it.rating?.let { r -> "★ $r" }).joinToString(" • ")
            },
            onClick = { },
        )
    }
}

@Composable
fun TvMoviesScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }

    var library by remember { mutableStateOf<XtreamVodLibrary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(provider.server, provider.username) {
        Thread {
            val result = client.loadVodLibrary(provider)
            Handler(Looper.getMainLooper()).post {
                result.onSuccess { library = it }.onFailure { error = it.message.orEmpty() }
                loading = false
            }
        }.start()
    }

    if (loading) {
        LoadingState("Filmler yükleniyor")
        return
    }
    if (error.isNotBlank() || library == null) {
        ErrorState(error.ifBlank { "Film listesi alınamadı." })
        return
    }

    val data = library!!
    var categoryId by remember { mutableStateOf("all") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<NativeVodItem?>(null) }

    if (playing != null) {
        VodPlayer(
            item = playing!!,
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            externalPlayerKeyEvent = externalPlayerKeyEvent,
            onFullscreenStateChanged = onFullscreenStateChanged,
            store = store,
            onClose = { playing = null },
        )
        return
    }

    val category = data.categories.firstOrNull { it.id == categoryId } ?: data.categories.first()
    val visible =
        if (category.id == "all") data.items
        else data.items.filter { it.categoryId == category.id }
    val selected = visible.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()

    Row(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)),
    ) {
        CategoryList(
            categories = data.categories,
            selectedId = category.id,
            onFocus = {
                onContentFocused()
                categoryId = it.id
                selectedId = null
            },
            modifier = Modifier.width(220.dp),
        )

        ContentList(
            rows = visible,
            selectedId = selected?.id,
            onFocus = {
                onContentFocused()
                selectedId = it.id
            },
            onClick = { playing = it },
            modifier = Modifier.width(360.dp),
        )

        VodDetail(
            item = selected,
            favorite = selected?.let { store.isFavorite(it.id) } ?: false,
            onFavorite = {
                selected?.let { store.toggleFavorite(it.toSaved()) }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun TvSeriesScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    onContentFocused: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }

    var library by remember { mutableStateOf<XtreamSeriesLibrary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(provider.server, provider.username) {
        Thread {
            val result = client.loadSeriesLibrary(provider)
            Handler(Looper.getMainLooper()).post {
                result.onSuccess { library = it }.onFailure { error = it.message.orEmpty() }
                loading = false
            }
        }.start()
    }

    if (loading) {
        LoadingState("Diziler yükleniyor")
        return
    }
    if (error.isNotBlank() || library == null) {
        ErrorState(error.ifBlank { "Dizi listesi alınamadı." })
        return
    }

    val data = library!!
    var categoryId by remember { mutableStateOf("all") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var openedSeries by remember { mutableStateOf<NativeSeriesInfo?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<Pair<NativeSeriesItem, NativeSeriesEpisode>?>(null) }

    if (playing != null) {
        val (seriesItem, episode) = playing!!
        EpisodePlayer(
            series = seriesItem,
            episode = episode,
            allEpisodes = openedSeries?.episodes.orEmpty(),
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            externalPlayerKeyEvent = externalPlayerKeyEvent,
            onFullscreenStateChanged = onFullscreenStateChanged,
            store = store,
            onClose = { playing = null },
            onNext = { next ->
                playing = seriesItem to next
            },
        )
        return
    }

    val category = data.categories.firstOrNull { it.id == categoryId } ?: data.categories.first()
    val visible =
        if (category.id == "all") data.items
        else data.items.filter { it.categoryId == category.id }
    val selected = visible.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B))) {
        SeriesCategoryList(
            categories = data.categories,
            selectedId = category.id,
            onFocus = {
                onContentFocused()
                categoryId = it.id
                selectedId = null
                openedSeries = null
            },
            modifier = Modifier.width(220.dp),
        )

        SeriesList(
            rows = visible,
            selectedId = selected?.id,
            onFocus = {
                onContentFocused()
                selectedId = it.id
                openedSeries = null
            },
            onClick = { seriesItem ->
                detailLoading = true
                Thread {
                    val result = client.loadSeriesInfo(provider, seriesItem)
                    Handler(Looper.getMainLooper()).post {
                        openedSeries = result.getOrNull()
                        detailLoading = false
                    }
                }.start()
            },
            modifier = Modifier.width(340.dp),
        )

        SeriesDetail(
            series = selected,
            info = openedSeries,
            loading = detailLoading,
            favorite = selected?.let { store.isFavorite(it.id) } ?: false,
            onFavorite = {
                selected?.let {
                    store.toggleFavorite(
                        TvSavedItem(it.id, "series", it.name, "", it.genre),
                    )
                }
            },
            onEpisode = { episode ->
                selected?.let { playing = it to episode }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun TvSearchScreen(
    provider: TvProviderConfig,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    val client = remember { XtreamClient() }

    var query by remember { mutableStateOf("") }
    var vod by remember { mutableStateOf<XtreamVodLibrary?>(null) }
    var series by remember { mutableStateOf<XtreamSeriesLibrary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf<NativeVodItem?>(null) }

    LaunchedEffect(provider.server, provider.username) {
        Thread {
            val v = client.loadVodLibrary(provider).getOrNull()
            val s = client.loadSeriesLibrary(provider).getOrNull()
            Handler(Looper.getMainLooper()).post {
                vod = v
                series = s
                loading = false
            }
        }.start()
    }

    if (playing != null) {
        VodPlayer(
            item = playing!!,
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            externalPlayerKeyEvent = externalPlayerKeyEvent,
            onFullscreenStateChanged = onFullscreenStateChanged,
            store = store,
            onClose = { playing = null },
        )
        return
    }

    val q = query.trim()
    val movies =
        if (q.length < 2) emptyList()
        else vod?.items.orEmpty().filter { it.name.contains(q, ignoreCase = true) }.take(25)
    val shows =
        if (q.length < 2) emptyList()
        else series?.items.orEmpty().filter { it.name.contains(q, ignoreCase = true) }.take(25)

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ara", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Film veya dizi ara") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (loading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (movies.isNotEmpty()) {
                    item {
                        Text("Filmler", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                    }
                    items(movies, key = { it.id }) { movie ->
                        SearchRow(
                            title = movie.name,
                            subtitle = listOfNotNull(movie.year, movie.genre).joinToString(" • "),
                            onClick = { playing = movie },
                        )
                    }
                }

                if (shows.isNotEmpty()) {
                    item {
                        Text("Diziler", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                    }
                    items(shows, key = { it.id }) { show ->
                        SearchRow(
                            title = show.name,
                            subtitle = listOfNotNull(show.year, show.genre).joinToString(" • "),
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvMyListScreen(
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TvContentStore(context) }
    var refresh by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf<TvSavedItem?>(null) }
    val favorites = remember(refresh) { store.favorites() }

    if (playing != null) {
        SavedItemPlayer(
            item = playing!!,
            playerFor = playerFor,
            releasePlayer = releasePlayer,
            externalPlayerKeyEvent = externalPlayerKeyEvent,
            onFullscreenStateChanged = onFullscreenStateChanged,
            store = store,
            onClose = { playing = null },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Listem", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)

        if (favorites.isEmpty()) {
            Text("Henüz favori eklenmedi.", color = Color(0xFF94A3B8))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favorites, key = { it.id }) { item ->
                    SearchRow(
                        title = item.name,
                        subtitle = item.kind.uppercase(),
                        onClick = {
                            if (item.url.isNotBlank()) playing = item
                        },
                        onLongAction = {
                            store.toggleFavorite(item)
                            refresh += 1
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    items: List<Pair<String, String>>,
    onClick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.take(6).forEachIndexed { index, item ->
                FocusCard(
                    title = item.first,
                    subtitle = item.second,
                    onClick = { onClick(index) },
                    modifier = Modifier.width(180.dp),
                )
            }
        }
    }
}

@Composable
private fun FocusCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .background(
                if (focused) Color(0xFF2563EB) else Color(0xFF151C28),
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = if (focused) Color.White else Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryList(
    categories: List<NativeVodCategory>,
    selectedId: String,
    onFocus: (NativeVodCategory) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight().background(Color(0xFF101722)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            FocusRow(
                title = category.name,
                subtitle = category.count.toString(),
                selected = category.id == selectedId,
                onFocus = { onFocus(category) },
                onClick = { onFocus(category) },
            )
        }
    }
}

@Composable
private fun ContentList(
    rows: List<NativeVodItem>,
    selectedId: String?,
    onFocus: (NativeVodItem) -> Unit,
    onClick: (NativeVodItem) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight().background(Color(0xFF0F141E)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(rows, key = { it.id }) { item ->
            FocusRow(
                title = item.name,
                subtitle = listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString(" • "),
                selected = item.id == selectedId,
                onFocus = { onFocus(item) },
                onClick = { onClick(item) },
            )
        }
    }
}

@Composable
private fun VodDetail(
    item: NativeVodItem?,
    favorite: Boolean,
    onFavorite: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight().background(Color(0xFF0B1018)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item == null) {
            Text("Film seç", color = Color(0xFF94A3B8))
            return
        }

        Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Text(
            listOfNotNull(item.year, item.genre, item.rating?.let { "★ $it" }).joinToString(" • "),
            color = Color(0xFF94A3B8),
        )
        Text(item.plot ?: "Açıklama bulunamadı.", color = Color(0xFFCBD5E1))
        FocusCard(
            title = if (favorite) "★ Listemden çıkar" else "☆ Listeme ekle",
            subtitle = "Favoriler",
            onClick = onFavorite,
            modifier = Modifier.width(220.dp),
        )
        Text("OK: Oynat", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SeriesCategoryList(
    categories: List<NativeSeriesCategory>,
    selectedId: String,
    onFocus: (NativeSeriesCategory) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight().background(Color(0xFF101722)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            FocusRow(
                title = category.name,
                subtitle = category.count.toString(),
                selected = category.id == selectedId,
                onFocus = { onFocus(category) },
                onClick = { onFocus(category) },
            )
        }
    }
}

@Composable
private fun SeriesList(
    rows: List<NativeSeriesItem>,
    selectedId: String?,
    onFocus: (NativeSeriesItem) -> Unit,
    onClick: (NativeSeriesItem) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight().background(Color(0xFF0F141E)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(rows, key = { it.id }) { item ->
            FocusRow(
                title = item.name,
                subtitle = listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString(" • "),
                selected = item.id == selectedId,
                onFocus = { onFocus(item) },
                onClick = { onClick(item) },
            )
        }
    }
}

@Composable
private fun SeriesDetail(
    series: NativeSeriesItem?,
    info: NativeSeriesInfo?,
    loading: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onEpisode: (NativeSeriesEpisode) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight().background(Color(0xFF0B1018)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (series == null) {
            Text("Dizi seç", color = Color(0xFF94A3B8))
            return
        }

        Text(series.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Text(listOfNotNull(series.year, series.genre, series.rating?.let { "★ $it" }).joinToString(" • "), color = Color(0xFF94A3B8))
        Text(series.plot ?: "Açıklama bulunamadı.", color = Color(0xFFCBD5E1), maxLines = 4, overflow = TextOverflow.Ellipsis)
        FocusCard(
            title = if (favorite) "★ Listemden çıkar" else "☆ Listeme ekle",
            subtitle = "Favoriler",
            onClick = onFavorite,
            modifier = Modifier.width(220.dp),
        )

        if (loading) {
            CircularProgressIndicator()
            Text("Bölümler yükleniyor...", color = Color(0xFF94A3B8))
        } else if (info == null) {
            Text("OK ile sezon ve bölümleri aç", color = Color(0xFF60A5FA))
        } else {
            Text("Bölümler", color = Color.White, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(info.episodes, key = { it.id }) { episode ->
                    SearchRow(
                        title = "S${episode.season} E${episode.episode} • ${episode.name}",
                        subtitle = "Oynat",
                        onClick = { onEpisode(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = if (focused) Color.White else Color(0xFF94A3B8), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SearchRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongAction: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color(0xFF2563EB) else Color(0xFF151C28), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = if (focused) Color.White else Color(0xFF94A3B8), style = MaterialTheme.typography.labelMedium)
        }
        if (onLongAction != null) {
            Text(
                "Kaldır",
                color = Color(0xFFF87171),
                modifier = Modifier.clickable { onLongAction() }.padding(8.dp),
            )
        }
    }
}

@Composable
private fun VodPlayer(
    item: NativeVodItem,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    store: TvContentStore,
    onClose: () -> Unit,
) {
    val saved = item.toSaved()
    val resume = store.progressFor(item.id)?.positionMs ?: 0L
    PlayerHost(
        saved = saved,
        request = PlaybackRequest(
            sessionId = "tv-movie-${item.streamId}",
            item = PlaybackItem(item.name, item.streamUrl, "movie"),
            resumeTimeMs = resume,
            preferences = PlaybackPreferences(showInfo = true),
        ),
        playerFor = playerFor,
        releasePlayer = releasePlayer,
        externalPlayerKeyEvent = externalPlayerKeyEvent,
        onFullscreenStateChanged = onFullscreenStateChanged,
        store = store,
        onClose = onClose,
    )
}

@Composable
private fun SavedItemPlayer(
    item: TvSavedItem,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    store: TvContentStore,
    onClose: () -> Unit,
) {
    PlayerHost(
        saved = item,
        request = PlaybackRequest(
            sessionId = "tv-saved-${item.id}",
            item = PlaybackItem(item.name, item.url, item.kind),
            resumeTimeMs = item.positionMs,
            preferences = PlaybackPreferences(showInfo = true),
        ),
        playerFor = playerFor,
        releasePlayer = releasePlayer,
        externalPlayerKeyEvent = externalPlayerKeyEvent,
        onFullscreenStateChanged = onFullscreenStateChanged,
        store = store,
        onClose = onClose,
    )
}

@Composable
private fun EpisodePlayer(
    series: NativeSeriesItem,
    episode: NativeSeriesEpisode,
    allEpisodes: List<NativeSeriesEpisode>,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    store: TvContentStore,
    onClose: () -> Unit,
    onNext: (NativeSeriesEpisode) -> Unit,
) {
    val index = allEpisodes.indexOfFirst { it.id == episode.id }
    val next = allEpisodes.getOrNull(index + 1)
    val saved = TvSavedItem(
        id = "series-${series.seriesId}-${episode.episodeId}",
        kind = "episode",
        name = "${series.name} • S${episode.season} E${episode.episode}",
        url = episode.streamUrl,
        subtitle = series.name,
    )

    PlayerHost(
        saved = saved,
        request = PlaybackRequest(
            sessionId = "tv-series-${series.seriesId}-${episode.episodeId}",
            item = PlaybackItem(
                name = saved.name,
                url = episode.streamUrl,
                kind = "episode",
                hasNext = next != null,
            ),
            resumeTimeMs = store.progressFor(saved.id)?.positionMs ?: 0L,
            preferences = PlaybackPreferences(showInfo = true),
        ),
        playerFor = playerFor,
        releasePlayer = releasePlayer,
        externalPlayerKeyEvent = externalPlayerKeyEvent,
        onFullscreenStateChanged = onFullscreenStateChanged,
        store = store,
        onClose = onClose,
        onNext = {
            if (next != null) onNext(next)
        },
    )
}

@Composable
private fun PlayerHost(
    saved: TvSavedItem,
    request: PlaybackRequest,
    playerFor: (PlaybackRequest) -> ExoPlayer,
    releasePlayer: (String) -> Unit,
    externalPlayerKeyEvent: Triple<Int, Int, Long>?,
    onFullscreenStateChanged: (Boolean) -> Unit,
    store: TvContentStore,
    onClose: () -> Unit,
    onNext: () -> Unit = {},
) {
    val player = remember(request.sessionId) { playerFor(request) }

    DisposableEffect(request.sessionId) {
        onFullscreenStateChanged(true)
        onDispose {
            onFullscreenStateChanged(false)
            releasePlayer(request.sessionId)
        }
    }

    BackHandler {
        onClose()
    }

    NativePlayerSurface(
        request = request,
        player = player,
        onClose = { progress ->
            store.saveProgress(
                saved.copy(
                    positionMs = (progress.currentSeconds * 1000).toLong(),
                    durationMs = (progress.durationSeconds * 1000).toLong(),
                ),
            )
            onClose()
        },
        onProgress = { progress ->
            store.saveProgress(
                saved.copy(
                    positionMs = (progress.currentSeconds * 1000).toLong(),
                    durationMs = (progress.durationSeconds * 1000).toLong(),
                ),
            )
        },
        onFailure = {},
        onNext = onNext,
        externalKeyEvent = externalPlayerKeyEvent,
    )
}

private fun NativeVodItem.toSaved() =
    TvSavedItem(
        id = id,
        kind = "movie",
        name = name,
        url = streamUrl,
        subtitle = listOfNotNull(year, genre).joinToString(" • "),
    )

@Composable
private fun LoadingState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D111B)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Text(text, color = Color(0xFFF87171), style = MaterialTheme.typography.titleMedium)
    }
}
