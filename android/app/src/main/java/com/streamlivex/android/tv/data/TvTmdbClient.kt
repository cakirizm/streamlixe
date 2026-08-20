package com.streamlivex.android.tv.data

import com.streamlivex.android.tv.i18n.TvLocale
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TvTmdbMedia(
    val id: Long,
    val name: String,
    val kind: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String?,
    val rating: Double?,
    val year: String?,
)

data class TvTmdbPerson(
    val id: Long,
    val name: String,
    val character: String? = null,
)

data class TvTmdbDetail(
    val media: TvTmdbMedia,
    val directors: List<TvTmdbPerson>,
    val cast: List<TvTmdbPerson>,
    val recommendations: List<TvTmdbMedia>,
    val trailerUrl: String? = null,
)

data class TvTmdbEpisode(
    val episodeNumber: Int,
    val name: String,
    val overview: String?,
    val still: String?,
    val airDate: String?,
    val runtime: Int?,
)

class TvTmdbClient {
    private val base = "https://streamlivex.com/api/tmdb"

    fun trending(kind: String, locale: TvLocale): Result<List<TvTmdbMedia>> = runCatching {
        val root = request(
            "$base?mode=trending&kind=${enc(kind)}&language=${enc(locale.tmdbLanguage)}",
        )
        parseMediaArray(root.optJSONArray("results"), kind)
    }

    fun detail(
        name: String,
        kind: String,
        locale: TvLocale,
        tmdbId: Long? = null,
    ): Result<TvTmdbDetail?> = runCatching {
        val locator =
            if (tmdbId != null && tmdbId > 0L) "id=$tmdbId"
            else "query=${enc(name)}"

        val root = request(
            "$base?$locator&kind=${enc(kind)}&detail=3&language=${enc(locale.tmdbLanguage)}",
        )
        val row = root.optJSONObject("result") ?: return@runCatching null

        var trailerUrl =
            parseTrailerUrl(row)

        if (trailerUrl == null) {
            runCatching {
                val richer =
                    request(
                        "$base?$locator&kind=${enc(kind)}&detail=4&language=${enc(locale.tmdbLanguage)}",
                    )
                trailerUrl =
                    richer
                        .optJSONObject("result")
                        ?.let {
                            parseTrailerUrl(it)
                        }
            }
        }

        TvTmdbDetail(
            media = parseMedia(row, kind),
            directors = parsePeople(row.optJSONArray("directors")),
            cast = parsePeople(row.optJSONArray("cast")).take(16),
            recommendations = parseMediaArray(row.optJSONArray("recommendations"), kind).take(16),
            trailerUrl = trailerUrl,
        )
    }

    fun person(personId: Long, locale: TvLocale): Result<List<TvTmdbMedia>> = runCatching {
        val root = request(
            "$base?person=$personId&language=${enc(locale.tmdbLanguage)}",
        )
        val array = root.optJSONArray("results") ?: JSONArray()

        buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val kind = if (row.optString("media_type") == "tv") "series" else "movie"
                add(parseMedia(row, kind))
            }
        }
    }

    fun season(
        title: String,
        season: Int,
        locale: TvLocale,
        tmdbId: Long? = null,
    ): Result<List<TvTmdbEpisode>> = runCatching {
        val locator =
            if (tmdbId != null && tmdbId > 0L) "id=$tmdbId"
            else "query=${enc(title)}"

        val root = request(
            "$base?mode=season&$locator&season=$season&language=${enc(locale.tmdbLanguage)}",
        )
        val array = root.optJSONArray("episodes") ?: JSONArray()

        buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val number = row.optInt("episode_number", i + 1)
                val stillPath = row.optString("still_path").takeIf { it.isNotBlank() }
                add(
                    TvTmdbEpisode(
                        episodeNumber = number,
                        name = row.optString("name").ifBlank { "$number. Bölüm" },
                        overview = row.optString("overview").takeIf { it.isNotBlank() },
                        still = stillPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                        airDate = row.optString("air_date").takeIf { it.isNotBlank() },
                        runtime = row.optInt("runtime").takeIf { it > 0 },
                    ),
                )
            }
        }
    }

    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "StreamLiveX-TV/1.0")
            connection.connect()

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) error("TMDB servisi HTTP $status hatası verdi.")
            if (text.isBlank()) error("TMDB servisi boş yanıt döndürdü.")
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTrailerUrl(row: JSONObject): String? {
        row.optString("trailer_url")
            .takeIf { it.startsWith("http") }
            ?.let { return it }

        val videos =
            row.optJSONObject("videos")?.optJSONArray("results")
                ?: row.optJSONArray("videos")

        if (videos != null) {
            for (i in 0 until videos.length()) {
                val video = videos.optJSONObject(i) ?: continue
                val site = video.optString("site")
                val type = video.optString("type")
                val key = video.optString("key").trim()
                if (
                    site.equals("YouTube", ignoreCase = true) &&
                    key.isNotBlank() &&
                    (
                        type.equals("Trailer", ignoreCase = true) ||
                            type.equals("Teaser", ignoreCase = true)
                    )
                ) {
                    return "https://www.youtube.com/watch?v=$key"
                }
            }
        }
        return null
    }

    private fun parseMediaArray(array: JSONArray?, fallbackKind: String): List<TvTmdbMedia> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val kind = when (row.optString("media_type")) {
                    "tv" -> "series"
                    "movie" -> "movie"
                    else -> fallbackKind
                }
                add(parseMedia(row, kind))
            }
        }
    }

    private fun parseMedia(row: JSONObject, kind: String): TvTmdbMedia {
        val posterPath = row.optString("poster_path").takeIf { it.isNotBlank() }
        val backdropPath = row.optString("backdrop_path").takeIf { it.isNotBlank() }
        val date = row.optString("release_date").ifBlank { row.optString("first_air_date") }

        return TvTmdbMedia(
            id = row.optLong("id", 0L),
            name = row.optString("title").ifBlank { row.optString("name") }.ifBlank { "İsimsiz" },
            kind = kind,
            poster = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdrop = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
            overview = row.optString("overview").takeIf { it.isNotBlank() },
            rating = row.optDouble("vote_average").takeIf { it > 0.0 },
            year = date.take(4).takeIf { it.length == 4 },
        )
    }

    private fun parsePeople(array: JSONArray?): List<TvTmdbPerson> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = row.optLong("id", 0L)
                val name = row.optString("name").trim()
                if (id <= 0L || name.isBlank()) continue
                add(
                    TvTmdbPerson(
                        id = id,
                        name = name,
                        character = row.optString("character").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
