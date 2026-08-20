package com.streamlivex.android.tv.data

import android.content.Context
import com.streamlivex.android.tv.profile.TvActiveScope
import org.json.JSONArray
import org.json.JSONObject

data class TvSavedItem(
    val id: String,
    val kind: String,
    val name: String,
    val url: String,
    val artwork: String? = null,
    val subtitle: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

class TvContentStore(context: Context) {
    private val appContext =
        context.applicationContext
    private val scopeKey =
        TvActiveScope.storageKey()
    private val prefs =
        appContext.getSharedPreferences(
            "streamlivex_tv_content_v4_$scopeKey",
            Context.MODE_PRIVATE,
        )

    init {
        migrateLegacyOnce()
    }

    private fun migrateLegacyOnce() {
        val migrationPrefs =
            appContext.getSharedPreferences(
                "streamlivex_tv_content_migration",
                Context.MODE_PRIVATE,
            )

        if (
            migrationPrefs.getBoolean(
                "legacy_v3_done",
                false,
            )
        ) {
            return
        }

        val legacy =
            appContext.getSharedPreferences(
                "streamlivex_tv_content_v3",
                Context.MODE_PRIVATE,
            )

        val all =
            legacy.all

        if (all.isNotEmpty()) {
            val editor =
                prefs.edit()

            all.forEach {
                (key, value) ->

                when (value) {
                    is String ->
                        editor.putString(
                            key,
                            value,
                        )

                    is Boolean ->
                        editor.putBoolean(
                            key,
                            value,
                        )

                    is Int ->
                        editor.putInt(
                            key,
                            value,
                        )

                    is Long ->
                        editor.putLong(
                            key,
                            value,
                        )

                    is Float ->
                        editor.putFloat(
                            key,
                            value,
                        )

                    is Set<*> ->
                        editor.putStringSet(
                            key,
                            value
                                .filterIsInstance<String>()
                                .toSet(),
                        )
                }
            }
            editor.apply()
        }

        migrationPrefs.edit()
            .putBoolean(
                "legacy_v3_done",
                true,
            )
            .apply()
    }

    fun favorites(): List<TvSavedItem> = readArray("favorites")

    fun continueWatching(): List<TvSavedItem> =
        readArray("continue")
            .filter { it.positionMs > 0L }
            .take(30)

    fun isFavorite(id: String): Boolean = favorites().any { it.id == id }

    fun toggleFavorite(item: TvSavedItem): Boolean {
        val rows = favorites().toMutableList()
        val index = rows.indexOfFirst { it.id == item.id }
        val nowFavorite =
            if (index >= 0) {
                rows.removeAt(index)
                false
            } else {
                rows.add(0, item)
                true
            }
        writeArray("favorites", rows.take(100), synchronous = true)
        return nowFavorite
    }

    fun saveProgress(item: TvSavedItem) {
        val rows = readArray("continue").toMutableList()
        rows.removeAll { it.id == item.id }

        val finished =
            item.durationMs > 0L &&
                item.positionMs >= (item.durationMs * 0.95).toLong()

        if (!finished && item.positionMs >= 15_000L) {
            rows.add(0, item)
        }
        writeArray("continue", rows.take(50))
    }

    fun progressFor(id: String): TvSavedItem? =
        readArray("continue").firstOrNull { it.id == id }

    fun isWatched(id: String): Boolean =
        prefs.getStringSet("watched", emptySet())
            .orEmpty()
            .contains(id)

    fun setWatched(
        id: String,
        watched: Boolean,
    ) {
        val rows =
            prefs.getStringSet(
                "watched",
                emptySet(),
            ).orEmpty().toMutableSet()

        if (watched) {
            rows.add(id)
        } else {
            rows.remove(id)
        }

        prefs.edit()
            .putStringSet(
                "watched",
                rows,
            )
            .apply()
    }

    fun toggleWatched(id: String): Boolean {
        val next = !isWatched(id)
        setWatched(id, next)
        return next
    }

    fun removeFavorite(id: String) {
        val rows =
            favorites()
                .filterNot { it.id == id }
                .take(100)

        writeArray(
            "favorites",
            rows,
            synchronous = true,
        )
    }

    fun clearViewingHistory() {
        prefs.edit()
            .remove("continue")
            .remove("watched")
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun readArray(key: String): List<TvSavedItem> {
        val raw = prefs.getString(key, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        TvSavedItem(
                            id = o.optString("id"),
                            kind = o.optString("kind"),
                            name = o.optString("name"),
                            url = o.optString("url"),
                            artwork = o.optString("artwork").ifBlank { null },
                            subtitle = o.optString("subtitle").ifBlank { null },
                            positionMs = o.optLong("positionMs", 0L),
                            durationMs = o.optLong("durationMs", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeArray(
        key: String,
        rows: List<TvSavedItem>,
        synchronous: Boolean = false,
    ) {
        val array = JSONArray()
        rows.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("kind", item.kind)
                    .put("name", item.name)
                    .put("url", item.url)
                    .put("artwork", item.artwork ?: "")
                    .put("subtitle", item.subtitle ?: "")
                    .put("positionMs", item.positionMs)
                    .put("durationMs", item.durationMs),
            )
        }
        val editor = prefs.edit().putString(key, array.toString())
        if (synchronous) editor.commit() else editor.apply()
    }
}
