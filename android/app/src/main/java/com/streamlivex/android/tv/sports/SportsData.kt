package com.streamlivex.android.tv.sports

import android.content.Context
import android.util.Log
import com.streamlivex.android.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SportsKind(val label: String) {
    ALL("Tümü"), FOOTBALL("Futbol"), BASKETBALL("Basketbol"), TENNIS("Tenis"), MOTORSPORT("F1 / Motor"),
}

data class SportsBroadcast(
    val id: String,
    val eventId: String,
    val country: String,
    val channelName: String,
    val channelLogo: String?,
)

data class SportsEvent(
    val id: String,
    val sport: String,
    val leagueId: String?,
    val league: String,
    val leagueBadge: String?,
    val home: String,
    val away: String,
    val homeBadge: String?,
    val awayBadge: String?,
    val startMs: Long,
    val status: String?,
    val homeScore: String?,
    val awayScore: String?,
    val venue: String?,
    val season: String?,
    val round: String?,
    val artwork: String?,
    val broadcasts: List<SportsBroadcast>,
    val country: String? = null,
    val liveMinute: Int? = null,
    val apiFootballId: String? = null,
    val footballDataId: String? = null,
    val theSportsDbId: String? = null,
    val dataSources: Set<String> = setOf("THESPORTSDB"),
) {
    val kind: SportsKind
        get() = when (sport.lowercase(Locale.ROOT)) {
            "soccer", "football" -> SportsKind.FOOTBALL
            "basketball" -> SportsKind.BASKETBALL
            "tennis" -> SportsKind.TENNIS
            "motorsport", "formula 1" -> SportsKind.MOTORSPORT
            else -> SportsKind.ALL
        }
}

class TheSportsDbProvider {
    private val base = "https://www.thesportsdb.com/api/v1/json/123"

    fun eventsFor(date: String): List<SportsEvent> {
        // Free V1 günlük endpoint yalnız birkaç satır döndürür. Spor filtresiz çağrı
        // ilk sporun kotayı tüketmesine yol açtığı için desteklenen her capability
        // ayrı ve günde yalnız bir kez istenir.
        val eventArrays = listOf("Soccer", "Basketball", "Tennis", "Motorsport").mapNotNull { sport ->
            runCatching { request("$base/eventsday.php?d=$date&s=$sport").optJSONArray("events") }.getOrNull()
        }
        val tvRoot = runCatching { request("$base/eventstv.php?d=$date") }.getOrNull()
        val broadcasts = parseBroadcasts(tvRoot?.optJSONArray("tvevent"))
            .groupBy { it.eventId }
        return buildList {
            eventArrays.forEach { rows -> for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val sport = row.clean("strSport") ?: continue
                if (sport.lowercase(Locale.ROOT) !in setOf("soccer", "football", "basketball", "tennis", "motorsport", "formula 1")) continue
                val id = row.clean("idEvent") ?: continue
                val start = parseStart(row.clean("dateEvent") ?: date, row.clean("strTime") ?: "00:00:00") ?: continue
                add(
                    SportsEvent(
                        id = id,
                        sport = sport,
                        leagueId = row.clean("idLeague"),
                        league = row.clean("strLeague") ?: sport,
                        leagueBadge = row.clean("strLeagueBadge"),
                        home = row.clean("strHomeTeam") ?: row.clean("strEvent") ?: sport,
                        away = row.clean("strAwayTeam").orEmpty(),
                        homeBadge = row.clean("strHomeTeamBadge"),
                        awayBadge = row.clean("strAwayTeamBadge"),
                        startMs = start,
                        status = row.clean("strStatus"),
                        homeScore = row.clean("intHomeScore"),
                        awayScore = row.clean("intAwayScore"),
                        venue = row.clean("strVenue"),
                        season = row.clean("strSeason"),
                        round = row.clean("intRound"),
                        artwork = row.clean("strThumb") ?: row.clean("strBanner") ?: row.clean("strPoster"),
                        broadcasts = broadcasts[id].orEmpty(),
                        country = row.clean("strCountry"),
                        theSportsDbId = id,
                    ),
                )
            } }
        }.distinctBy { it.id }.sortedBy { it.startMs }
    }

    fun footballEventsFor(date: String): List<SportsEvent> {
        val rows = runCatching { request("$base/eventsday.php?d=$date&s=Soccer").optJSONArray("events") }.getOrNull() ?: return emptyList()
        return parseEvents(date, listOf(rows), emptyMap())
    }

    fun broadcastsFor(eventId: String): List<SportsBroadcast> =
        parseBroadcasts(request("$base/lookuptv.php?id=$eventId").optJSONArray("tvevent"))

    fun teamBadge(teamName: String): String? {
        val query = teamAliases[teamName] ?: teamName
        val rows = request("$base/searchteams.php?t=${java.net.URLEncoder.encode(query, "UTF-8")}").optJSONArray("teams") ?: return null
        val expected = teamIdentity(query)
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val names = listOfNotNull(row.clean("strTeam"), row.clean("strTeamAlternate"))
            if (names.any { candidate -> candidate.split(',').any { teamIdentity(it) == expected } }) {
                return row.clean("strBadge") ?: row.clean("strTeamBadge")
            }
        }
        return null
    }

    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 18_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "StreamLiveX-TV/1.0")
            check(connection.responseCode in 200..299) { "TheSportsDB HTTP ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun parseBroadcasts(rows: JSONArray?): List<SportsBroadcast> = buildList {
        if (rows == null) return@buildList
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val eventId = row.clean("idEvent") ?: continue
            val name = row.clean("strChannel") ?: continue
            add(SportsBroadcast(row.clean("id") ?: "$eventId-$i", eventId, row.clean("strCountry").orEmpty(), name, row.clean("strLogo")))
        }
    }

    private fun parseStart(date: String, time: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
            .parse("$date ${time.take(8).padEnd(8, '0')}")?.time
    }.getOrNull()

    private fun teamIdentity(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "").removeSuffix("fc")

    private val teamAliases = mapOf(
        "Man Utd" to "Manchester United", "Spurs" to "Tottenham Hotspur", "Nott'm Forest" to "Nottingham Forest",
        "Internazionale" to "Inter Milan", "Marítimo M." to "Maritimo", "Académico" to "Academico Viseu",
    )

    private fun parseEvents(date: String, arrays: List<JSONArray>, broadcasts: Map<String, List<SportsBroadcast>>): List<SportsEvent> = buildList {
        arrays.forEach { rows -> for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.clean("idEvent") ?: continue
            val sport = row.clean("strSport") ?: continue
            val start = parseStart(row.clean("dateEvent") ?: date, row.clean("strTime") ?: "00:00:00") ?: continue
            add(SportsEvent(
                id, sport, row.clean("idLeague"), row.clean("strLeague") ?: sport, row.clean("strLeagueBadge"),
                row.clean("strHomeTeam") ?: row.clean("strEvent") ?: sport, row.clean("strAwayTeam").orEmpty(),
                row.clean("strHomeTeamBadge"), row.clean("strAwayTeamBadge"), start, row.clean("strStatus"),
                row.clean("intHomeScore"), row.clean("intAwayScore"), row.clean("strVenue"), row.clean("strSeason"),
                row.clean("intRound"), row.clean("strThumb") ?: row.clean("strBanner") ?: row.clean("strPoster"), broadcasts[id].orEmpty(),
                country = row.clean("strCountry"), theSportsDbId = id,
            ))
        } }
    }.distinctBy { it.id }.sortedBy { it.startMs }
}

class ApiFootballProvider {
    fun eventsFor(date: String): Result<List<SportsEvent>> = runCatching {
        val base = BuildConfig.WEB_APP_URL.trimEnd('/').removeSuffix("/app")
        val connection = URL("$base/api/sports?date=$date").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 18_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e("SportsProvider", "API-Football proxy HTTP ${connection.responseCode}: $errorBody")
                error("Sports proxy HTTP ${connection.responseCode}")
            }
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            root.optJSONArray("diagnostics")?.let { diagnostics ->
                for (i in 0 until diagnostics.length()) Log.i("SportsProvider", diagnostics.optJSONObject(i)?.toString().orEmpty())
            }
            if (!root.optBoolean("configured", false)) {
                Log.e("SportsProvider", "API_FOOTBALL_KEY yapılandırılmamış; TheSportsDB fallback kullanılacak")
                return@runCatching emptyList()
            }
            val rows = root.optJSONArray("fixtures") ?: return@runCatching emptyList()
            Log.i("SportsPipeline", "backend fixture count=${rows.length()}")
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val id = row.clean("id") ?: continue
                    val source = row.clean("source") ?: "API_FOOTBALL"
                    val timestamp = row.optLong("timestamp", 0L).takeIf { it > 0 }?.times(1000L) ?: continue
                    add(SportsEvent(
                        id = when (source) { "FOOTBALL_DATA" -> "fd-$id"; "TURKEY_FIXTURE_OPEN" -> "tr-$id"; "FIXTURE_DOWNLOAD" -> "fx-$id"; else -> "af-$id" }, sport = "Soccer", leagueId = row.clean("leagueId"), league = row.clean("league") ?: "Football",
                        leagueBadge = row.clean("leagueLogo"), home = row.clean("home") ?: continue, away = row.clean("away") ?: continue,
                        homeBadge = row.clean("homeLogo"), awayBadge = row.clean("awayLogo"), startMs = timestamp,
                        status = row.clean("status"), homeScore = row.clean("homeScore"), awayScore = row.clean("awayScore"),
                        venue = row.clean("venue"), season = row.clean("season"), round = row.clean("round"), artwork = null,
                        broadcasts = emptyList(), country = row.clean("country"), liveMinute = row.optInt("liveMinute", -1).takeIf { it >= 0 },
                        apiFootballId = id.takeIf { source == "API_FOOTBALL" }, footballDataId = id.takeIf { source == "FOOTBALL_DATA" }, dataSources = setOf(source),
                    ))
                }
            }.also { Log.i("SportsPipeline", "parsed fixture count=${it.size}") }
        } finally { connection.disconnect() }
    }
}

class SportsDataAggregator {
    private val apiFootball = ApiFootballProvider()
    private val theSportsDb = TheSportsDbProvider()

    fun eventsFor(date: String): List<SportsEvent> {
        val football = apiFootball.eventsFor(date).getOrDefault(emptyList())
        val fallback = runCatching { theSportsDb.eventsFor(date) }.getOrDefault(emptyList())
        val combined = if (football.isNotEmpty()) {
            football + fallback.filter { it.kind != SportsKind.FOOTBALL || isTurkishCompetition(it) }
        } else fallback
        return combined.distinctBy {
            "${it.startMs / 300_000L}|${eventIdentity(it.home)}|${eventIdentity(it.away)}"
        }.sortedWith(compareBy<SportsEvent> { competitionPriority(it) }.thenBy { it.startMs })
    }

    private fun isTurkishCompetition(event: SportsEvent): Boolean {
        val value = "${event.country.orEmpty()} ${event.league}".lowercase(Locale.ROOT)
        return "turkey" in value || "türkiye" in value || "super lig" in value || "süper lig" in value
    }

    private fun eventIdentity(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "")

    private fun competitionPriority(event: SportsEvent): Int {
        val key = "${event.country.orEmpty()} ${event.league}".lowercase(Locale.ROOT)
        return when {
            "turkey" in key || "türkiye" in key || "super lig" in key || "süper lig" in key -> 1
            "premier league" in key && "england" in key -> 2
            "champions league" in key -> 3
            "la liga" in key -> 4
            "bundesliga" in key -> 5
            "serie a" in key -> 6
            "ligue 1" in key -> 7
            "europa league" in key -> 8
            "conference league" in key -> 9
            "primeira" in key || "liga portugal" in key -> 10
            else -> 50
        }
    }
}

object CrossProviderEventMatcher {
    fun find(source: SportsEvent, candidates: List<SportsEvent>): Pair<SportsEvent, Int>? = candidates.mapNotNull { candidate ->
        if (kotlin.math.abs(source.startMs - candidate.startMs) > 90 * 60_000L) return@mapNotNull null
        val direct = teamScore(source.home, candidate.home) + teamScore(source.away, candidate.away)
        val reversed = teamScore(source.home, candidate.away) + teamScore(source.away, candidate.home)
        val teams = maxOf(direct, reversed)
        if (teams < 150) return@mapNotNull null
        val timeScore = (20 - (kotlin.math.abs(source.startMs - candidate.startMs) / 300_000L).toInt()).coerceAtLeast(0)
        val leagueScore = if (nameTokens(source.league).intersect(nameTokens(candidate.league)).isNotEmpty()) 10 else 0
        candidate to (teams / 2 + timeScore + leagueScore).coerceAtMost(100)
    }.maxByOrNull { it.second }?.takeIf { it.second >= 88 }

    private fun teamScore(left: String, right: String): Int {
        val a = nameTokens(left); val b = nameTokens(right)
        if (a.isEmpty() || b.isEmpty()) return 0
        if (a == b) return 100
        val overlap = a.intersect(b).size.toDouble() / maxOf(a.size, b.size)
        return (overlap * 100).toInt()
    }

    private fun nameTokens(value: String): Set<String> = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim().split(Regex("\\s+"))
        .filter { it.length > 1 && it !in setOf("fc", "cf", "sk", "club", "the") }.toSet()
}

class SportsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("streamlivex_sports_daily_v2", Context.MODE_PRIVATE)
    private val aggregator = SportsDataAggregator()
    private val theSportsDb = TheSportsDbProvider()

    fun today(force: Boolean = false): List<SportsEvent> {
        val date = localDate()
        synchronized(lock) {
            val storedSchema = prefs.getInt("schemaVersion", 0)
            if (storedSchema != CURRENT_SCHEMA_VERSION) {
                Log.i("SportsPipeline", "cache invalidated oldSchema=$storedSchema newSchema=$CURRENT_SCHEMA_VERSION")
                prefs.edit().clear().commit()
                memoryDate = ""
                memoryEvents = emptyList()
                memorySchemaVersion = CURRENT_SCHEMA_VERSION
            }
            if (!force && memorySchemaVersion == CURRENT_SCHEMA_VERSION && memoryDate == date && memoryEvents.isNotEmpty()) {
                Log.i("SportsPipeline", "cache fixture count=${memoryEvents.size} source=memory")
                return memoryEvents
            }
            if (!force && prefs.getInt("schemaVersion", 0) == CURRENT_SCHEMA_VERSION && prefs.getString("date", null) == date) {
                decode(prefs.getString("events", null)).takeIf { it.isNotEmpty() }?.let {
                    memoryDate = date
                    memoryEvents = it
                    memorySchemaVersion = CURRENT_SCHEMA_VERSION
                    Log.i("SportsPipeline", "cache fixture count=${it.size} source=persistent")
                    return it
                }
            }
            return runCatching { aggregator.eventsFor(date) }.getOrElse {
                decode(prefs.getString("events", null))
            }.also { events ->
                if (events.isNotEmpty()) {
                    memoryDate = date
                    memoryEvents = events
                    memorySchemaVersion = CURRENT_SCHEMA_VERSION
                    prefs.edit().putString("date", date).putLong("fetchedAt", System.currentTimeMillis())
                        .putString("provider", "FOOTBALL_DATA_V1").putInt("schemaVersion", CURRENT_SCHEMA_VERSION)
                        .putString("events", encode(events).toString()).apply()
                }
                Log.i("SportsPipeline", "cache fixture count=${events.size} source=network-write")
            }
        }
    }

    fun enrichBroadcasts(event: SportsEvent): SportsEvent {
        if (event.broadcasts.isNotEmpty()) return event
        val key = "tv_${localDate()}_${event.id}"
        prefs.getString(key, null)?.let { raw -> return event.copy(broadcasts = decodeBroadcasts(JSONArray(raw))) }
        val theSportsDbId = event.theSportsDbId ?: crossProviderEvent(event)?.theSportsDbId
        val rows = theSportsDbId?.let { runCatching { theSportsDb.broadcastsFor(it) }.getOrDefault(emptyList()) }.orEmpty()
        prefs.edit().putString(key, encodeBroadcasts(rows).toString()).apply()
        return event.copy(
            broadcasts = rows,
            theSportsDbId = theSportsDbId,
            dataSources = if (theSportsDbId != null) event.dataSources + setOf("THESPORTSDB") else event.dataSources,
        )
    }

    fun enrichTeamBadges(events: List<SportsEvent>, maxNetworkLookups: Int = 24): List<SportsEvent> {
        var networkLookups = 0
        fun badge(name: String, existing: String?): String? {
            if (!existing.isNullOrBlank()) return existing
            if (name.isBlank()) return null
            val key = "team_badge_${teamCacheKey(name)}"
            if (prefs.contains(key)) return prefs.getString(key, null)?.takeUnless { it == NO_BADGE }
            if (networkLookups >= maxNetworkLookups) return null
            networkLookups++
            val found = runCatching { theSportsDb.teamBadge(name) }.getOrNull()
            prefs.edit().putString(key, found ?: NO_BADGE).apply()
            return found
        }
        return events.map { event -> event.copy(homeBadge = badge(event.home, event.homeBadge), awayBadge = badge(event.away, event.awayBadge)) }
    }

    private fun teamCacheKey(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun crossProviderEvent(event: SportsEvent): SportsEvent? {
        if (event.kind != SportsKind.FOOTBALL) return null
        val key = "tsdb_cross_${localDate()}"
        val candidates = prefs.getString(key, null)?.let(::decode).orEmpty().ifEmpty {
            runCatching { theSportsDb.footballEventsFor(localDate()) }.getOrDefault(emptyList()).also { rows ->
                if (rows.isNotEmpty()) prefs.edit().putString(key, encode(rows).toString()).apply()
            }
        }
        return CrossProviderEventMatcher.find(event, candidates)?.first
    }

    private fun localDate() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun encode(events: List<SportsEvent>) = JSONArray().apply { events.forEach { event -> put(JSONObject().apply {
        put("id", event.id); put("sport", event.sport); put("leagueId", event.leagueId); put("league", event.league); put("leagueBadge", event.leagueBadge)
        put("home", event.home); put("away", event.away); put("homeBadge", event.homeBadge); put("awayBadge", event.awayBadge); put("startMs", event.startMs)
        put("status", event.status); put("homeScore", event.homeScore); put("awayScore", event.awayScore); put("venue", event.venue); put("season", event.season)
        put("round", event.round); put("artwork", event.artwork); put("broadcasts", encodeBroadcasts(event.broadcasts)); put("country", event.country)
        put("liveMinute", event.liveMinute); put("apiFootballId", event.apiFootballId); put("footballDataId", event.footballDataId); put("theSportsDbId", event.theSportsDbId); put("dataSources", JSONArray(event.dataSources.toList()))
    }) } }

    private fun decode(raw: String?): List<SportsEvent> = runCatching {
        val rows = JSONArray(raw ?: return emptyList())
        buildList { for (i in 0 until rows.length()) { val o = rows.getJSONObject(i); add(SportsEvent(
            o.getString("id"), o.getString("sport"), o.clean("leagueId"), o.getString("league"), o.clean("leagueBadge"), o.getString("home"), o.optString("away"),
            o.clean("homeBadge"), o.clean("awayBadge"), o.getLong("startMs"), o.clean("status"), o.clean("homeScore"), o.clean("awayScore"), o.clean("venue"),
            o.clean("season"), o.clean("round"), o.clean("artwork"), decodeBroadcasts(o.optJSONArray("broadcasts") ?: JSONArray()),
            o.clean("country"), o.optInt("liveMinute", -1).takeIf { it >= 0 }, o.clean("apiFootballId"), o.clean("footballDataId"), o.clean("theSportsDbId"),
            o.optJSONArray("dataSources")?.let { array -> buildSet { for (j in 0 until array.length()) add(array.optString(j)) } } ?: setOf("THESPORTSDB"),
        )) } }
    }.getOrDefault(emptyList())

    private fun encodeBroadcasts(rows: List<SportsBroadcast>) = JSONArray().apply { rows.forEach { put(JSONObject().apply {
        put("id", it.id); put("eventId", it.eventId); put("country", it.country); put("channelName", it.channelName); put("channelLogo", it.channelLogo)
    }) } }

    private fun decodeBroadcasts(rows: JSONArray) = buildList { for (i in 0 until rows.length()) { val o = rows.getJSONObject(i); add(SportsBroadcast(
        o.getString("id"), o.getString("eventId"), o.optString("country"), o.getString("channelName"), o.clean("channelLogo"),
    )) } }

    companion object {
        private const val NO_BADGE = "__none__"
        private const val CURRENT_SCHEMA_VERSION = 5
        private val lock = Any()
        private var memoryDate = ""
        private var memoryEvents = emptyList<SportsEvent>()
        private var memorySchemaVersion = CURRENT_SCHEMA_VERSION
    }
}

private fun JSONObject.clean(key: String): String? = optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
