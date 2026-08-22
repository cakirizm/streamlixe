package com.streamlivex.android.tv.data

import com.streamlivex.android.BuildConfig
import android.util.JsonReader
import android.util.JsonToken
import android.util.Base64
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

data class TvProviderConfig(
    val name: String = "Oynatma Listem",
    val server: String,
    val username: String,
    val password: String,
)

data class NativeLiveCategory(
    val id: String,
    val name: String,
    val count: Int,
)

data class NativeLiveChannel(
    val id: String,
    val streamId: String,
    val categoryId: String,
    val name: String,
    val logo: String?,
    val epgId: String?,
    val streamUrl: String,
)

data class XtreamLiveLibrary(
    val categories: List<NativeLiveCategory>,
    val channels: List<NativeLiveChannel>,
)

data class NativeLiveEpgProgram(
    val title: String,
    val description: String?,
    val startTimestamp: Long,
    val stopTimestamp: Long,
) {
    fun isCurrent(nowSeconds: Long): Boolean =
        nowSeconds in startTimestamp until stopTimestamp
}

object TvLiveLibraryCache {
    var library: XtreamLiveLibrary? = null
    fun clear() { library = null }
}

class XtreamClient {

    fun loadChannelEpg(
        provider: TvProviderConfig,
        channel: NativeLiveChannel,
        limit: Int = 6,
    ): Result<List<NativeLiveEpgProgram>> = runCatching {
        val now = System.currentTimeMillis() / 1000L
        val providerRows = loadShortEpg(provider, channel.streamId, limit)
            .getOrDefault(emptyList())
            .filter { it.stopTimestamp > now - 60L }
            .sortedBy { it.startTimestamp }
        // Bazı Xtream sağlayıcıları dolu ama tamamen geçmiş bir short_epg listesi
        // döndürüyor. Bu satırlar fallback'i engellememeli.
        if (providerRows.any { it.isCurrent(now) }) {
            return@runCatching providerRows.take(limit.coerceIn(1, 12))
        }

        val query = URLEncoder.encode(channel.name, "UTF-8")
        val epgId = URLEncoder.encode(channel.epgId.orEmpty(), "UTF-8")
        val base = BuildConfig.WEB_APP_URL.trimEnd('/').removeSuffix("/app")
        val root = JSONObject(requestSmallText("$base/api/epg?channel=$query&id=$epgId", 512 * 1024))
        val programmes = root.optJSONArray("programmes") ?: return@runCatching emptyList()
        val parsed = buildList {
            for (index in 0 until programmes.length()) {
                val row = programmes.optJSONObject(index) ?: continue
                val start = parseXmlTvTimestamp(row.optString("start")) ?: continue
                val stop = parseXmlTvTimestamp(row.optString("stop")) ?: continue
                if (stop <= start) continue
                add(
                    NativeLiveEpgProgram(
                        title = row.optString("title", "Program bilgisi").ifBlank { "Program bilgisi" },
                        description = row.optString("description").takeIf { it.isNotBlank() },
                        startTimestamp = start,
                        stopTimestamp = stop,
                    ),
                )
            }
        }
        val fallbackRows = parsed.filter { it.stopTimestamp > now - 60L }
            .sortedBy { it.startTimestamp }
            .take(limit.coerceIn(1, 12))
        if (fallbackRows.isNotEmpty()) fallbackRows else providerRows.take(limit.coerceIn(1, 12))
    }

    private fun parseXmlTvTimestamp(value: String): Long? {
        val clean = value.trim()
        val patterns = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmssZ", "yyyyMMddHHmmss")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(clean)
            }.getOrNull()
            if (parsed != null) return parsed.time / 1000L
        }
        return null
    }

    /*
     * MEMORY RULE:
     * VOD/Series için provider'ın bütün kütüphanesini ASLA tek JSONArray/String olarak RAM'e almıyoruz.
     * Kategori listesini ayrı, seçilen kategorinin içeriklerini ayrı ve JsonReader ile stream ederek okuyoruz.
     */

    fun loadLiveLibrary(provider: TvProviderConfig): Result<XtreamLiveLibrary> = runCatching {
        TvLiveLibraryCache.library?.let { return@runCatching it }
        val p = normalized(provider)
        validateAccount(p)

        val categories = readLiveCategories(p)
        val channels = readLiveChannels(p)
        if (channels.isEmpty()) error("Canlı kanal bulunamadı.")

        val counts = channels.groupingBy { it.categoryId }.eachCount()
        val resultCategories = buildList {
            add(NativeLiveCategory("all", "Tümü", channels.size))
            categories.forEach { (id, name) ->
                add(NativeLiveCategory(id, name, counts[id] ?: 0))
            }
        }

        XtreamLiveLibrary(resultCategories, channels).also {
            TvLiveLibraryCache.library = it
        }
    }

    fun loadShortEpg(
        provider: TvProviderConfig,
        streamId: String,
        limit: Int = 6,
    ): Result<List<NativeLiveEpgProgram>> = runCatching {
        val p = normalized(provider)
        val actions =
            listOf(
                "get_short_epg",
                "get_simple_data_table",
            )

        actions.forEach { action ->
            val url =
                buildApiUrl(
                    p,
                    action,
                    mapOf(
                        "stream_id" to streamId,
                        "limit" to limit.coerceIn(1, 12).toString(),
                    ),
                )
            val root =
                JSONObject(
                    requestSmallText(
                        url,
                        1024 * 1024,
                    ),
                )
            val listings =
                root.optJSONArray("epg_listings")
                    ?: root.optJSONArray("listings")
                    ?: return@forEach

            val parsed = buildList {
            for (index in 0 until listings.length()) {
                val row =
                    listings.optJSONObject(index)
                        ?: continue

                val rawStart =
                    row.optString("start_timestamp")
                        .toLongOrNull()
                        ?: row.optLong("start_timestamp", 0L)

                val rawStop =
                    row.optString("stop_timestamp")
                        .toLongOrNull()
                        ?: row.optLong("stop_timestamp", 0L)

                val start =
                    if (rawStart > 10_000_000_000L) rawStart / 1000L
                    else rawStart
                val stop =
                    if (rawStop > 10_000_000_000L) rawStop / 1000L
                    else rawStop

                if (start <= 0L || stop <= start) continue

                add(
                    NativeLiveEpgProgram(
                        title =
                            decodeXtreamText(
                                row.optString("title"),
                            ).ifBlank {
                                "Program bilgisi yok"
                            },
                        description =
                            decodeXtreamText(
                                row.optString("description"),
                            ).takeIf {
                                it.isNotBlank()
                            },
                        startTimestamp = start,
                        stopTimestamp = stop,
                    ),
                )
            }
            }

            if (parsed.isNotEmpty()) {
                return@runCatching parsed
            }
        }

        emptyList()
    }

    /**
     * Full VOD scan used by the disk indexer.
     *
     * Important: this streams one JSON object at a time and invokes [onItem].
     * It does NOT materialize the provider's entire VOD catalogue in RAM.
     */
    fun scanVod(
        provider: TvProviderConfig,
        onItem: (NativeVodItem) -> Unit,
    ): Result<Int> = runCatching {
        var scannedCount = 0
        val p =
            normalized(provider)

        val url =
            buildApiUrl(
                p,
                "get_vod_streams",
            )

        scanArrayStream(
            url,
        ) {
            reader ->

            var streamId = ""
            var categoryId = ""
            var name = ""
            var icon: String? = null
            var plot: String? = null
            var rating: Double? = null
            var year: String? = null
            var genre: String? = null
            var extension = "mp4"

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "stream_id" ->
                        streamId =
                            reader.nextStringSafe()

                    "category_id" ->
                        categoryId =
                            reader.nextStringSafe()

                    "name" ->
                        name =
                            reader.nextStringSafe()

                    "stream_icon" ->
                        icon =
                            reader
                                .nextStringSafe()
                                .takeIf {
                                    it.isNotBlank()
                                }

                    "plot" ->
                        plot =
                            reader
                                .nextStringSafe()
                                .takeIf {
                                    it.isNotBlank()
                                }

                    "rating" ->
                        rating =
                            reader
                                .nextStringSafe()
                                .toDoubleOrNull()

                    "year" ->
                        year =
                            reader
                                .nextStringSafe()
                                .takeIf {
                                    it.isNotBlank()
                                }

                    "genre" ->
                        genre =
                            reader
                                .nextStringSafe()
                                .takeIf {
                                    it.isNotBlank()
                                }

                    "container_extension" ->
                        extension =
                            reader
                                .nextStringSafe()
                                .ifBlank {
                                    "mp4"
                                }

                    else ->
                        reader.skipValue()
                }
            }

            reader.endObject()

            if (streamId.isNotBlank()) {
                onItem(
                    NativeVodItem(
                        id =
                            "m$streamId",
                        streamId =
                            streamId,
                        categoryId =
                            categoryId,
                        name =
                            name.ifBlank {
                                "İsimsiz Film"
                            },
                        poster =
                            icon,
                        plot =
                            plot,
                        rating =
                            rating,
                        year =
                            year,
                        genre =
                            genre,
                        extension =
                            extension,
                        streamUrl =
                            "${p.server}/movie/${p.username}/${p.password}/$streamId.$extension",
                    ),
                )
                scannedCount += 1
            }
        }
        scannedCount
    }

    /**
     * Full Series scan used by the disk indexer.
     * Streams rows directly into [onItem] without holding the whole catalogue.
     */
    fun scanSeries(
        provider: TvProviderConfig,
        onItem: (NativeSeriesItem) -> Unit,
    ): Result<Int> = runCatching {
        var scannedCount = 0
        val p =
            normalized(provider)

        val url =
            buildApiUrl(
                p,
                "get_series",
            )

        scanArrayStream(
            url,
        ) {
            reader ->

            var seriesId = ""
            var categoryId = ""
            var name = ""
            var cover: String? = null
            var plot: String? = null
            var rating: Double? = null
            var year: String? = null
            var genre: String? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "series_id" ->
                        seriesId =
                            reader.nextStringSafe()

                    "category_id" ->
                        categoryId =
                            reader.nextStringSafe()

                    "name" ->
                        name =
                            reader.nextStringSafe()

                    "cover" ->
                        cover =
                            reader
                                .nextStringSafe()
                                .takeIf {
                                    it.isNotBlank()
                                }

                    "plot" ->
                        plot =
                            reader
                                .nextStringSafe()
                                .takeIf {
                                    it.isNotBlank()
                                }

                    "rating" ->
                        rating =
                            reader
                                .nextStringSafe()
                                .toDoubleOrNull()

                    "releaseDate",
                    "release_date",
                    "year",
                    ->
                        year =
                            reader
                                .nextStringSafe()
                                .takeIf {
                                    it.isNotBlank()
                                }
                                ?.take(4)

                    "genre" ->
                        genre =
                            reader
                                .nextStringSafe()
                                .takeIf {
                                    it.isNotBlank()
                                }

                    else ->
                        reader.skipValue()
                }
            }

            reader.endObject()

            if (seriesId.isNotBlank()) {
                onItem(
                    NativeSeriesItem(
                        id =
                            "s$seriesId",
                        seriesId =
                            seriesId,
                        categoryId =
                            categoryId,
                        name =
                            name.ifBlank {
                                "İsimsiz Dizi"
                            },
                        cover =
                            cover,
                        plot =
                            plot,
                        rating =
                            rating,
                        year =
                            year,
                        genre =
                            genre,
                    ),
                )
                scannedCount += 1
            }
        }
        scannedCount
    }

    fun loadVodCategories(provider: TvProviderConfig): Result<List<NativeVodCategory>> =
        runCatching {
            TvContentCache.vodCategories?.let { return@runCatching it }
            val p = normalized(provider)
            val rows = readSimpleCategories(p, "get_vod_categories")
                .map { NativeVodCategory(it.first, it.second) }
            TvContentCache.vodCategories = rows
            rows
        }

    fun loadSeriesCategories(provider: TvProviderConfig): Result<List<NativeSeriesCategory>> =
        runCatching {
            TvContentCache.seriesCategories?.let { return@runCatching it }
            val p = normalized(provider)
            val rows = readSimpleCategories(p, "get_series_categories")
                .map { NativeSeriesCategory(it.first, it.second) }
            TvContentCache.seriesCategories = rows
            rows
        }

    fun loadVodCategory(
        provider: TvProviderConfig,
        categoryId: String,
        maxItems: Int = Int.MAX_VALUE,
    ): Result<List<NativeVodItem>> = runCatching {
        if (maxItems == Int.MAX_VALUE) {
            TvContentCache.vodByCategory[categoryId]?.let { return@runCatching it }
        }

        val p = normalized(provider)
        val url = buildApiUrl(
            p,
            "get_vod_streams",
            mapOf("category_id" to categoryId),
        )

        val rows = readArrayStream(url, maxItems) { reader ->
            var streamId = ""
            var catId = categoryId
            var name = ""
            var icon: String? = null
            var plot: String? = null
            var rating: Double? = null
            var year: String? = null
            var genre: String? = null
            var extension = "mp4"

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "stream_id" -> streamId = reader.nextStringSafe()
                    "category_id" -> catId = reader.nextStringSafe().ifBlank { categoryId }
                    "name" -> name = reader.nextStringSafe()
                    "stream_icon" -> icon = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    "plot" -> plot = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    "rating" -> rating = reader.nextStringSafe().toDoubleOrNull()
                    "year" -> year = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    "genre" -> genre = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    "container_extension" -> {
                        extension = reader.nextStringSafe().ifBlank { "mp4" }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (streamId.isBlank()) {
                null
            } else {
                NativeVodItem(
                    id = "m$streamId",
                    streamId = streamId,
                    categoryId = catId,
                    name = name.ifBlank { "İsimsiz Film" },
                    poster = icon,
                    plot = plot,
                    rating = rating,
                    year = year,
                    genre = genre,
                    extension = extension,
                    streamUrl = "${p.server}/movie/${p.username}/${p.password}/$streamId.$extension",
                )
            }
        }

        if (maxItems == Int.MAX_VALUE) {
            TvContentCache.vodByCategory[categoryId] = rows
        }
        rows
    }

    fun loadSeriesCategory(
        provider: TvProviderConfig,
        categoryId: String,
        maxItems: Int = Int.MAX_VALUE,
    ): Result<List<NativeSeriesItem>> = runCatching {
        if (maxItems == Int.MAX_VALUE) {
            TvContentCache.seriesByCategory[categoryId]?.let { return@runCatching it }
        }

        val p = normalized(provider)
        val url = buildApiUrl(
            p,
            "get_series",
            mapOf("category_id" to categoryId),
        )

        val rows = readArrayStream(url, maxItems) { reader ->
            var seriesId = ""
            var catId = categoryId
            var name = ""
            var cover: String? = null
            var plot: String? = null
            var rating: Double? = null
            var year: String? = null
            var genre: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "series_id" -> seriesId = reader.nextStringSafe()
                    "category_id" -> catId = reader.nextStringSafe().ifBlank { categoryId }
                    "name" -> name = reader.nextStringSafe()
                    "cover" -> cover = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    "plot" -> plot = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    "rating" -> rating = reader.nextStringSafe().toDoubleOrNull()
                    "releaseDate", "release_date", "year" -> {
                        val raw = reader.nextStringSafe()
                        year = raw.takeIf { it.isNotBlank() }?.take(4)
                    }
                    "genre" -> genre = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (seriesId.isBlank()) {
                null
            } else {
                NativeSeriesItem(
                    id = "s$seriesId",
                    seriesId = seriesId,
                    categoryId = catId,
                    name = name.ifBlank { "İsimsiz Dizi" },
                    cover = cover,
                    plot = plot,
                    rating = rating,
                    year = year,
                    genre = genre,
                )
            }
        }

        if (maxItems == Int.MAX_VALUE) {
            TvContentCache.seriesByCategory[categoryId] = rows
        }
        rows
    }

    fun loadSeriesInfo(
        provider: TvProviderConfig,
        series: NativeSeriesItem,
    ): Result<NativeSeriesInfo> = runCatching {
        TvContentCache.seriesDetails[series.seriesId]?.let { return@runCatching it }

        val p = normalized(provider)
        val url = buildApiUrl(
            p,
            "get_series_info",
            mapOf("series_id" to series.seriesId),
        )
        val text = requestSmallText(url, 8 * 1024 * 1024)
        val root = JSONObject(text)
        val episodesObject = root.optJSONObject("episodes")
        val episodes = mutableListOf<NativeSeriesEpisode>()

        if (episodesObject != null) {
            val seasonKeys = episodesObject.keys()
            while (seasonKeys.hasNext()) {
                val seasonKey = seasonKeys.next()
                val seasonNumber = seasonKey.toIntOrNull() ?: 0
                val rows = episodesObject.optJSONArray(seasonKey) ?: continue

                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val episodeId = row.optString("id").trim()
                    if (episodeId.isBlank()) continue

                    val extension =
                        row.optString("container_extension").trim().ifBlank { "mp4" }
                    val episodeNumber =
                        row.optInt("episode_num", i + 1).takeIf { it > 0 } ?: i + 1
                    val title =
                        row.optString("title").trim()
                            .ifBlank { "S${seasonNumber} E${episodeNumber}" }

                    episodes += NativeSeriesEpisode(
                        id = "e$episodeId",
                        episodeId = episodeId,
                        season = seasonNumber,
                        episode = episodeNumber,
                        name = title,
                        extension = extension,
                        streamUrl = "${p.server}/series/${p.username}/${p.password}/$episodeId.$extension",
                    )
                }
            }
        }

        NativeSeriesInfo(
            series = series,
            episodes = episodes.sortedWith(
                compareBy<NativeSeriesEpisode> { it.season }.thenBy { it.episode },
            ),
        ).also {
            TvContentCache.seriesDetails[series.seriesId] = it
        }
    }

    private fun normalized(provider: TvProviderConfig): TvProviderConfig {
        val server = provider.server.trim().trimEnd('/')
        val username = provider.username.trim()
        val password = provider.password

        if (server.isBlank() || username.isBlank() || password.isBlank()) {
            throw IllegalArgumentException("Sunucu, kullanıcı adı ve şifre gerekli.")
        }
        return provider.copy(server = server, username = username)
    }

    private fun validateAccount(provider: TvProviderConfig) {
        val text = requestSmallText(buildApiUrl(provider), 2 * 1024 * 1024)
        val userInfo = JSONObject(text).optJSONObject("user_info")
            ?: error("Xtream hesap bilgisi alınamadı.")
        if (userInfo.optString("auth").trim() != "1") {
            error("Xtream hesabı doğrulanamadı.")
        }
    }

    private fun readSimpleCategories(
        provider: TvProviderConfig,
        action: String,
    ): List<Pair<String, String>> {
        val url = buildApiUrl(provider, action)
        return readArrayStream(url) { reader ->
            var id = ""
            var name = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "category_id" -> id = reader.nextStringSafe()
                    "category_name" -> name = reader.nextStringSafe()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (id.isBlank()) null else id to name.ifBlank { "Diğer" }
        }
    }

    private fun readLiveCategories(provider: TvProviderConfig): List<Pair<String, String>> =
        readSimpleCategories(provider, "get_live_categories")

    private fun readLiveChannels(provider: TvProviderConfig): List<NativeLiveChannel> {
        val url = buildApiUrl(provider, "get_live_streams")
        return readArrayStream(url) { reader ->
            var streamId = ""
            var categoryId = ""
            var name = ""
            var logo: String? = null
            var epgId: String? = null
            var extension = "ts"

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "stream_id" -> streamId = reader.nextStringSafe()
                    "category_id" -> categoryId = reader.nextStringSafe()
                    "name" -> name = reader.nextStringSafe()
                    "stream_icon" -> logo = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    "epg_channel_id" -> epgId = reader.nextStringSafe().takeIf { it.isNotBlank() }
                    "container_extension" -> {
                        extension = reader.nextStringSafe().ifBlank { "ts" }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (streamId.isBlank()) null else NativeLiveChannel(
                id = "l$streamId",
                streamId = streamId,
                categoryId = categoryId,
                name = name.ifBlank { "İsimsiz Kanal" },
                logo = logo,
                epgId = epgId,
                streamUrl = "${provider.server}/live/${provider.username}/${provider.password}/$streamId.$extension",
            )
        }
    }

    private fun buildApiUrl(
        provider: TvProviderConfig,
        action: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(provider.server)
        append("/player_api.php?username=")
        append(URLEncoder.encode(provider.username, "UTF-8"))
        append("&password=")
        append(URLEncoder.encode(provider.password, "UTF-8"))

        if (!action.isNullOrBlank()) {
            append("&action=")
            append(URLEncoder.encode(action, "UTF-8"))
        }
        extra.forEach { (key, value) ->
            append("&")
            append(URLEncoder.encode(key, "UTF-8"))
            append("=")
            append(URLEncoder.encode(value, "UTF-8"))
        }
    }

    private fun scanArrayStream(
        url: String,
        onObject: (JsonReader) -> Unit,
    ) {
        val connection =
            open(url)

        try {
            val reader =
                JsonReader(
                    InputStreamReader(
                        connection.inputStream,
                        Charsets.UTF_8,
                    ),
                )

            reader.use {
                it.beginArray()

                while (it.hasNext()) {
                    onObject(it)
                }

                it.endArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> readArrayStream(
        url: String,
        maxItems: Int = Int.MAX_VALUE,
        mapper: (JsonReader) -> T?,
    ): List<T> {
        val connection = open(url)
        try {
            val reader = JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val result = ArrayList<T>(if (maxItems == Int.MAX_VALUE) 64 else maxItems.coerceAtMost(64))
            reader.use {
                it.beginArray()
                while (it.hasNext()) {
                    if (result.size >= maxItems) break
                    val item = mapper(it)
                    if (item != null) result.add(item)
                }
                // We intentionally stop early for Home preview requests.
                // Closing reader/connection aborts the remaining response without allocating it.
            }
            return result
        } finally {
            connection.disconnect()
        }
    }

    private fun requestSmallText(url: String, maxBytes: Int): String {
        val connection = open(url)
        try {
            val input = connection.inputStream
            val buffer = ByteArray(8192)
            val out = StringBuilder()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) error("Sunucu yanıtı güvenli bellek sınırını aştı.")
                out.append(String(buffer, 0, read, Charsets.UTF_8))
            }
            return out.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "VLC/3.0 StreamLiveX-TV")
        connection.connect()

        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            error("Yayın sunucusu HTTP $status hatası verdi.")
        }
        return connection
    }

    private fun decodeXtreamText(value: String): String {
        val raw = value.trim()
        if (raw.isBlank()) return ""

        return runCatching {
            String(
                Base64.decode(
                    raw,
                    Base64.DEFAULT,
                ),
                Charsets.UTF_8,
            ).trim()
        }.getOrDefault(raw)
    }

    private fun JsonReader.nextStringSafe(): String {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                ""
            }
            JsonToken.STRING, JsonToken.NUMBER -> nextString()
            JsonToken.BOOLEAN -> nextBoolean().toString()
            else -> {
                skipValue()
                ""
            }
        }
    }
}
