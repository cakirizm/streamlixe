package com.streamlivex.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineSubtitleResult(val fileId: String, val language: String, val release: String)

// VLC benzeri: bu icerik icin yerlesik altyazi yoksa (ya da kullanici baska bir dil ariyorsa)
// OpenSubtitles.com uzerinden arama yapar. API anahtari sunucuda (.env / Worker secret) tutulur,
// uygulama yalnizca kendi backend'imize (BuildConfig.WEB_APP_URL) istek atar, anahtari hic gormez.
object SubtitleSearchClient {
    suspend fun search(query: String, langs: String = "tr,en"): List<OnlineSubtitleResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val encodedLangs = URLEncoder.encode(langs, "UTF-8")
        val url = "${BuildConfig.WEB_APP_URL}/api/subtitles?mode=search&query=$encodedQuery&langs=$encodedLangs"
        val json = getJson(url) ?: return@withContext emptyList()
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until results.length()) {
                val row = results.optJSONObject(index) ?: continue
                val fileId = row.optString("fileId")
                if (fileId.isBlank()) continue
                add(
                    OnlineSubtitleResult(
                        fileId = fileId,
                        language = row.optString("language", "und"),
                        release = row.optString("release", "Altyazı"),
                    ),
                )
            }
        }
    }

    suspend fun resolveDownloadUrl(fileId: String): String? = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(fileId, "UTF-8")
        val url = "${BuildConfig.WEB_APP_URL}/api/subtitles?mode=download&fileId=$encodedId"
        val json = getJson(url) ?: return@withContext null
        json.optString("url").ifBlank { null }
    }

    private fun getJson(url: String): JSONObject? {
        return try {
            (URL(url).openConnection() as HttpURLConnection).run {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                try {
                    if (responseCode !in 200..299) return null
                    val body = inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body)
                } finally {
                    disconnect()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
