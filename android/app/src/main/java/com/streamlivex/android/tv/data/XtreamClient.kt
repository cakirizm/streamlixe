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

    fun clear() {
        library = null
    }
}

class XtreamClient {

    fun loadLiveLibrary(
        provider: TvProviderConfig,
    ): Result<XtreamLiveLibrary> {
        return runCatching {
            val server =
                provider.server
                    .trim()
                    .trimEnd('/')

            val username =
                provider.username.trim()

            val password =
                provider.password

            if (
                server.isBlank() ||
                username.isBlank() ||
                password.isBlank()
            ) {
                throw IllegalArgumentException(
                    "Sunucu, kullanıcı adı ve şifre gerekli.",
                )
            }

            /*
             * ÖNEMLİ:
             *
             * /api/import kullanmıyoruz.
             *
             * Çünkü /api/import aynı anda:
             * - live
             * - vod
             * - series
             *
             * verilerinin tamamını indiriyor.
             *
             * Android TV'de bu yüzlerce MB RAM tüketebiliyor.
             *
             * Canlı TV ekranı için yalnızca:
             * - hesap doğrulama
             * - canlı kategoriler
             * - canlı kanallar
             *
             * çekiyoruz.
             */

            validateAccount(
                server = server,
                username = username,
                password = password,
            )

            val categoryArray =
                requestArray(
                    server = server,
                    username = username,
                    password = password,
                    action = "get_live_categories",
                )

            val liveArray =
                requestArray(
                    server = server,
                    username = username,
                    password = password,
                    action = "get_live_streams",
                )

            val channels =
                buildChannels(
                    liveArray = liveArray,
                    server = server,
                    username = username,
                    password = password,
                )

            if (channels.isEmpty()) {
                throw IllegalStateException(
                    "Hesap doğrulandı ancak canlı kanal bulunamadı.",
                )
            }

            val categoryCounts =
                channels
                    .groupingBy {
                        it.categoryId
                    }
                    .eachCount()

            val categories =
                buildCategories(
                    categoryArray = categoryArray,
                    categoryCounts = categoryCounts,
                    totalChannelCount = channels.size,
                )

            val library =
                XtreamLiveLibrary(
                    categories = categories,
                    channels = channels,
                )

            TvLiveLibraryCache.library =
                library

            library
        }
    }

    private fun validateAccount(
        server: String,
        username: String,
        password: String,
    ) {
        val response =
            requestObject(
                server = server,
                username = username,
                password = password,
            )

        val userInfo =
            response.optJSONObject("user_info")
                ?: throw IllegalStateException(
                    "Xtream hesap bilgisi alınamadı.",
                )

        val auth =
            userInfo
                .optString("auth")
                .trim()

        if (auth != "1") {
            val message =
                userInfo
                    .optString("message")
                    .trim()

            throw IllegalStateException(
                message.ifBlank {
                    "Kullanıcı adı, şifre veya abonelik bilgisi geçersiz."
                },
            )
        }

        val status =
            userInfo
                .optString("status")
                .trim()

        if (
            status.isNotBlank() &&
            !status.equals(
                "Active",
                ignoreCase = true,
            )
        ) {
            throw IllegalStateException(
                "Xtream hesabı aktif değil: $status",
            )
        }
    }

    private fun requestObject(
        server: String,
        username: String,
        password: String,
    ): JSONObject {
        val url =
            buildApiUrl(
                server = server,
                username = username,
                password = password,
                action = null,
            )

        val text =
            requestText(url)

        return try {
            JSONObject(text)
        } catch (_: Exception) {
            throw IllegalStateException(
                "Xtream sunucusu geçerli hesap bilgisi döndürmedi.",
            )
        }
    }

    private fun requestArray(
        server: String,
        username: String,
        password: String,
        action: String,
    ): JSONArray {
        val url =
            buildApiUrl(
                server = server,
                username = username,
                password = password,
                action = action,
            )

        val text =
            requestText(url)

        return try {
            JSONArray(text)
        } catch (_: Exception) {
            throw IllegalStateException(
                "$action verisi okunamadı.",
            )
        }
    }

    private fun buildApiUrl(
        server: String,
        username: String,
        password: String,
        action: String?,
    ): String {
        val encodedUsername =
            URLEncoder.encode(
                username,
                "UTF-8",
            )

        val encodedPassword =
            URLEncoder.encode(
                password,
                "UTF-8",
            )

        return buildString {
            append(server)
            append("/player_api.php")
            append("?username=")
            append(encodedUsername)
            append("&password=")
            append(encodedPassword)

            if (!action.isNullOrBlank()) {
                append("&action=")
                append(
                    URLEncoder.encode(
                        action,
                        "UTF-8",
                    ),
                )
            }
        }
    }

    private fun requestText(
        url: String,
    ): String {
        val connection =
            URL(url)
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                20_000

            connection.readTimeout =
                45_000

            connection.instanceFollowRedirects =
                true

            connection.setRequestProperty(
                "Accept",
                "application/json",
            )

            connection.setRequestProperty(
                "User-Agent",
                "VLC/3.0 StreamLiveX-TV",
            )

            val status =
                connection.responseCode

            val stream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                stream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()

            if (status !in 200..299) {
                throw IllegalStateException(
                    "Yayın sunucusu HTTP $status hatası verdi.",
                )
            }

            if (text.isBlank()) {
                throw IllegalStateException(
                    "Yayın sunucusu boş yanıt döndürdü.",
                )
            }

            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun buildCategories(
        categoryArray: JSONArray,
        categoryCounts: Map<String, Int>,
        totalChannelCount: Int,
    ): List<NativeLiveCategory> {
        val result =
            mutableListOf<NativeLiveCategory>()

        result +=
            NativeLiveCategory(
                id = "all",
                name = "Tümü",
                count = totalChannelCount,
            )

        for (
            index in
            0 until categoryArray.length()
        ) {
            val item =
                categoryArray
                    .optJSONObject(index)
                    ?: continue

            val categoryId =
                item
                    .optString("category_id")
                    .trim()

            if (categoryId.isBlank()) {
                continue
            }

            val categoryName =
                item
                    .optString("category_name")
                    .trim()
                    .ifBlank {
                        "Diğer"
                    }

            result +=
                NativeLiveCategory(
                    id = categoryId,
                    name = categoryName,
                    count =
                        categoryCounts[categoryId]
                            ?: 0,
                )
        }

        return result
    }

    private fun buildChannels(
        liveArray: JSONArray,
        server: String,
        username: String,
        password: String,
    ): List<NativeLiveChannel> {
        val result =
            mutableListOf<NativeLiveChannel>()

        for (
            index in
            0 until liveArray.length()
        ) {
            val item =
                liveArray
                    .optJSONObject(index)
                    ?: continue

            val streamId =
                item
                    .optString("stream_id")
                    .trim()

            if (streamId.isBlank()) {
                continue
            }

            val name =
                item
                    .optString("name")
                    .trim()
                    .ifBlank {
                        "İsimsiz Kanal"
                    }

            val categoryId =
                item
                    .optString("category_id")
                    .trim()

            val extension =
                item
                    .optString(
                        "container_extension",
                    )
                    .trim()
                    .ifBlank {
                        "ts"
                    }

            val logo =
                item
                    .optString("stream_icon")
                    .trim()
                    .takeIf {
                        it.isNotEmpty()
                    }

            val epgId =
                item
                    .optString(
                        "epg_channel_id",
                    )
                    .trim()
                    .takeIf {
                        it.isNotEmpty()
                    }

            val streamUrl =
                "$server/live/$username/$password/$streamId.$extension"

            result +=
                NativeLiveChannel(
                    id = "l$streamId",
                    streamId = streamId,
                    categoryId = categoryId,
                    name = name,
                    logo = logo,
                    epgId = epgId,
                    streamUrl = streamUrl,
                )
        }

        return result
    }
}
