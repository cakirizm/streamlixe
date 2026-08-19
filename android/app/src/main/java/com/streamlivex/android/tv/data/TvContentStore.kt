package com.streamlivex.android.tv.data

import android.content.Context
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
    private val prefs =
        context.getSharedPreferences("streamlivex_tv_content_v2", Context.MODE_PRIVATE)

    fun favorites(): List<TvSavedItem> = readArray("favorites")

    fun continueWatching(): List<TvSavedItem> =
        readArray("continue").filter { it.positionMs > 0L }.take(30)

    fun isFavorite(id: String): Boolean = favorites().any { it.id == id }

    fun toggleFavorite(item: TvSavedItem) {
        val rows = favorites().toMutableList()
        val index = rows.indexOfFirst { it.id == item.id }
        if (index >= 0) rows.removeAt(index) else rows.add(0, item)
        writeArray("favorites", rows.take(100))
    }

    fun saveProgress(item: TvSavedItem) {
        val rows = continueWatching().toMutableList()
        rows.removeAll { it.id == item.id }

        val finished =
            item.durationMs > 0L &&
                item.positionMs >= (item.durationMs * 0.92).toLong()

        if (!finished && item.positionMs >= 15_000L) {
            rows.add(0, item)
        }
        writeArray("continue", rows.take(50))
    }

    fun progressFor(id: String): TvSavedItem? =
        continueWatching().firstOrNull { it.id == id }

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

    private fun writeArray(key: String, rows: List<TvSavedItem>) {
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
        prefs.edit().putString(key, array.toString()).apply()
    }
}
