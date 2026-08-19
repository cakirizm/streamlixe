package com.streamlivex.android.tv.data

import com.streamlivex.android.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

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

class XtreamClient {

    fun loadLiveLibrary(
        provider: TvProviderConfig,
    ): Result<XtreamLiveLibrary> {
        return runCatching {
            val apiUrl = streamLiveXApiUrl()

            val payload = JSONObject().apply {
                put("method", "xtream")
                put(
                    "server",
                    provider.server.trim().trimEnd('/'),
                )
                put(
                    "username",
                    provider.username.trim(),
                )
                put(
                    "password",
                    provider.password,
                )
            }

            val connection =
                URL(apiUrl).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 45_000
                connection.readTimeout = 60_000
                connection.doInput = true
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json",
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/json",
                )

                connection.setRequestProperty(
                    "User-Agent",
                    "StreamLiveX-TV/${BuildConfig.VERSION_NAME}",
                )

                connection.outputStream.use { output ->
                    output.write(
                        payload
                            .toString()
                            .toByteArray(Charsets.UTF_8),
                    )
                    output.flush()
                }

                val status = connection.responseCode

                val stream =
                    if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val responseText =
                    stream
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()

                if (status !in 200..299) {
                    val serverMessage =
                        runCatching {
                            JSONObject(responseText)
                                .optString("error")
                                .trim()
                                .takeIf { it.isNotEmpty() }
                        }.getOrNull()

                    throw IllegalStateException(
                        serverMessage
                            ?: "Liste alınamadı. HTTP $status",
                    )
                }

                if (responseText.isBlank()) {
                    throw IllegalStateException(
                        "Sunucu boş yanıt döndürdü.",
                    )
                }

                val response = JSONObject(responseText)

                val liveArray =
                    response.optJSONArray("live")
                        ?: JSONArray()

                val categoryArray =
                    response.optJSONArray("liveCategories")
                        ?: JSONArray()

                val server =
                    provider.server
                        .trim()
                        .trimEnd('/')

                val username =
                    provider.username.trim()

                val password =
                    provider.password

                val channels =
                    buildChannels(
                        liveArray = liveArray,
                        server = server,
                        username = username,
                        password = password,
                    )

                if (channels.isEmpty()) {
                    throw IllegalStateException(
                        "Hesapta canlı kanal bulunamadı.",
                    )
                }

                val categoryCounts =
                    channels
                        .groupingBy { it.categoryId }
                        .eachCount()

                val categories =
                    buildCategories(
                        categoryArray = categoryArray,
                        categoryCounts = categoryCounts,
                        totalChannelCount = channels.size,
                    )

                XtreamLiveLibrary(
                    categories = categories,
                    channels = channels,
                )
            } finally {
                connection.disconnect()
            }
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

        for (index in 0 until categoryArray.length()) {
            val item =
                categoryArray.optJSONObject(index)
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
                    .ifBlank { "Diğer" }

            result +=
                NativeLiveCategory(
                    id = categoryId,
                    name = categoryName,
                    count = categoryCounts[categoryId] ?: 0,
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

        for (index in 0 until liveArray.length()) {
            val item =
                liveArray.optJSONObject(index)
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
                    .ifBlank { "İsimsiz Kanal" }

            val categoryId =
                item
                    .optString("category_id")
                    .trim()

            val extension =
                item
                    .optString("container_extension")
                    .trim()
                    .ifBlank { "ts" }

            val logo =
                item
                    .optString("stream_icon")
                    .trim()
                    .takeIf { it.isNotEmpty() }

            val epgId =
                item
                    .optString("epg_channel_id")
                    .trim()
                    .takeIf { it.isNotEmpty() }

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

    private fun streamLiveXApiUrl(): String {
        val configured =
            BuildConfig.WEB_APP_URL

        val uri =
            URI(configured)

        val scheme =
            uri.scheme ?: "https"

        val host =
            uri.host ?: "streamlivex.com"

        val port =
            if (uri.port == -1) {
                ""
            } else {
                ":${uri.port}"
            }

        return "$scheme://$host$port/api/import"
    }
}
