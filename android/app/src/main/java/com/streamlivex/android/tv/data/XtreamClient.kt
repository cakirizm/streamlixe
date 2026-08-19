package com.streamlivex.android.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

object TvLiveLibraryCache {
    var library: XtreamLiveLibrary? = null
    fun clear() { library = null }
}

class XtreamClient {

    fun loadLiveLibrary(provider: TvProviderConfig): Result<XtreamLiveLibrary> = runCatching {
        val p = normalized(provider)
        validateAccount(p)

        val categoriesJson = requestArray(p, "get_live_categories")
        val streamsJson = requestArray(p, "get_live_streams")
        val channels = buildLiveChannels(streamsJson, p)

        if (channels.isEmpty()) error("Hesap doğrulandı ancak canlı kanal bulunamadı.")

        val counts = channels.groupingBy { it.categoryId }.eachCount()
        val categories = buildLiveCategories(categoriesJson, counts, channels.size)

        XtreamLiveLibrary(categories, channels).also {
            TvLiveLibraryCache.library = it
        }
    }

    fun loadVodLibrary(provider: TvProviderConfig): Result<XtreamVodLibrary> = runCatching {
        TvVodLibraryCache.library?.let { return@runCatching it }

        val p = normalized(provider)
        validateAccount(p)

        val categoriesJson = requestArray(p, "get_vod_categories")
        val streamsJson = requestArray(p, "get_vod_streams")
        val items = buildVodItems(streamsJson, p)

        val counts = items.groupingBy { it.categoryId }.eachCount()
        val categories = buildVodCategories(categoriesJson, counts, items.size)

        XtreamVodLibrary(categories, items).also {
            TvVodLibraryCache.library = it
        }
    }

    fun loadSeriesLibrary(provider: TvProviderConfig): Result<XtreamSeriesLibrary> = runCatching {
        TvSeriesLibraryCache.library?.let { return@runCatching it }

        val p = normalized(provider)
        validateAccount(p)

        val categoriesJson = requestArray(p, "get_series_categories")
        val seriesJson = requestArray(p, "get_series")
        val items = buildSeriesItems(seriesJson)

        val counts = items.groupingBy { it.categoryId }.eachCount()
        val categories = buildSeriesCategories(categoriesJson, counts, items.size)

        XtreamSeriesLibrary(categories, items).also {
            TvSeriesLibraryCache.library = it
        }
    }

    fun loadSeriesInfo(
        provider: TvProviderConfig,
        series: NativeSeriesItem,
    ): Result<NativeSeriesInfo> = runCatching {
        TvSeriesLibraryCache.details[series.seriesId]?.let { return@runCatching it }

        val p = normalized(provider)
        val root = requestObject(
            p,
            action = "get_series_info",
            extra = mapOf("series_id" to series.seriesId),
        )

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

                    val extension = row.optString("container_extension").trim().ifBlank { "mp4" }
                    val episodeNumber =
                        row.optInt("episode_num", i + 1).takeIf { it > 0 } ?: (i + 1)
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
            episodes = episodes.sortedWith(compareBy<NativeSeriesEpisode> { it.season }.thenBy { it.episode }),
        ).also {
            TvSeriesLibraryCache.details[series.seriesId] = it
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
        val response = requestObject(provider)
        val userInfo = response.optJSONObject("user_info")
            ?: throw IllegalStateException("Xtream hesap bilgisi alınamadı.")

        if (userInfo.optString("auth").trim() != "1") {
            throw IllegalStateException(
                userInfo.optString("message").trim()
                    .ifBlank { "Kullanıcı adı, şifre veya abonelik bilgisi geçersiz." },
            )
        }

        val status = userInfo.optString("status").trim()
        if (status.isNotBlank() && !status.equals("Active", true)) {
            throw IllegalStateException("Xtream hesabı aktif değil: $status")
        }
    }

    private fun requestObject(
        provider: TvProviderConfig,
        action: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): JSONObject {
        val text = requestText(buildApiUrl(provider, action, extra))
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            throw IllegalStateException("${action ?: "account"} verisi okunamadı.")
        }
    }

    private fun requestArray(
        provider: TvProviderConfig,
        action: String,
    ): JSONArray {
        val text = requestText(buildApiUrl(provider, action))
        return try {
            JSONArray(text)
        } catch (_: Exception) {
            throw IllegalStateException("$action verisi okunamadı.")
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

    private fun requestText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 35_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "VLC/3.0 StreamLiveX-TV")

            val status = connection.responseCode
            val stream =
                if (status in 200..299) connection.inputStream
                else connection.errorStream

            val text =
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                throw IllegalStateException("Yayın sunucusu HTTP $status hatası verdi.")
            }
            if (text.isBlank()) {
                throw IllegalStateException("Yayın sunucusu boş yanıt döndürdü.")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun buildLiveCategories(
        array: JSONArray,
        counts: Map<String, Int>,
        total: Int,
    ): List<NativeLiveCategory> {
        val result = mutableListOf(NativeLiveCategory("all", "Tümü", total))
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("category_id").trim()
            if (id.isBlank()) continue
            result += NativeLiveCategory(
                id = id,
                name = item.optString("category_name").trim().ifBlank { "Diğer" },
                count = counts[id] ?: 0,
            )
        }
        return result
    }

    private fun buildVodCategories(
        array: JSONArray,
        counts: Map<String, Int>,
        total: Int,
    ): List<NativeVodCategory> {
        val result = mutableListOf(NativeVodCategory("all", "Tümü", total))
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("category_id").trim()
            if (id.isBlank()) continue
            result += NativeVodCategory(
                id = id,
                name = item.optString("category_name").trim().ifBlank { "Diğer" },
                count = counts[id] ?: 0,
            )
        }
        return result
    }

    private fun buildSeriesCategories(
        array: JSONArray,
        counts: Map<String, Int>,
        total: Int,
    ): List<NativeSeriesCategory> {
        val result = mutableListOf(NativeSeriesCategory("all", "Tümü", total))
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("category_id").trim()
            if (id.isBlank()) continue
            result += NativeSeriesCategory(
                id = id,
                name = item.optString("category_name").trim().ifBlank { "Diğer" },
                count = counts[id] ?: 0,
            )
        }
        return result
    }

    private fun buildLiveChannels(
        array: JSONArray,
        provider: TvProviderConfig,
    ): List<NativeLiveChannel> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val streamId = item.optString("stream_id").trim()
            if (streamId.isBlank()) continue

            val extension = item.optString("container_extension").trim().ifBlank { "ts" }
            add(
                NativeLiveChannel(
                    id = "l$streamId",
                    streamId = streamId,
                    categoryId = item.optString("category_id").trim(),
                    name = item.optString("name").trim().ifBlank { "İsimsiz Kanal" },
                    logo = item.optString("stream_icon").trim().takeIf { it.isNotEmpty() },
                    epgId = item.optString("epg_channel_id").trim().takeIf { it.isNotEmpty() },
                    streamUrl = "${provider.server}/live/${provider.username}/${provider.password}/$streamId.$extension",
                ),
            )
        }
    }

    private fun buildVodItems(
        array: JSONArray,
        provider: TvProviderConfig,
    ): List<NativeVodItem> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val streamId = item.optString("stream_id").trim()
            if (streamId.isBlank()) continue

            val extension = item.optString("container_extension").trim().ifBlank { "mp4" }
            val rating = item.optString("rating").trim().toDoubleOrNull()

            add(
                NativeVodItem(
                    id = "m$streamId",
                    streamId = streamId,
                    categoryId = item.optString("category_id").trim(),
                    name = item.optString("name").trim().ifBlank { "İsimsiz Film" },
                    poster = item.optString("stream_icon").trim().takeIf { it.isNotEmpty() },
                    plot = item.optString("plot").trim().takeIf { it.isNotEmpty() },
                    rating = rating,
                    year = item.optString("year").trim().takeIf { it.isNotEmpty() },
                    genre = item.optString("genre").trim().takeIf { it.isNotEmpty() },
                    extension = extension,
                    streamUrl = "${provider.server}/movie/${provider.username}/${provider.password}/$streamId.$extension",
                ),
            )
        }
    }

    private fun buildSeriesItems(array: JSONArray): List<NativeSeriesItem> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val seriesId = item.optString("series_id").trim()
            if (seriesId.isBlank()) continue

            add(
                NativeSeriesItem(
                    id = "s$seriesId",
                    seriesId = seriesId,
                    categoryId = item.optString("category_id").trim(),
                    name = item.optString("name").trim().ifBlank { "İsimsiz Dizi" },
                    cover = item.optString("cover").trim().takeIf { it.isNotEmpty() },
                    plot = item.optString("plot").trim().takeIf { it.isNotEmpty() },
                    rating = item.optString("rating").trim().toDoubleOrNull(),
                    year =
                        item.optString("releaseDate").trim()
                            .takeIf { it.isNotEmpty() }
                            ?.take(4),
                    genre = item.optString("genre").trim().takeIf { it.isNotEmpty() },
                ),
            )
        }
    }
}
