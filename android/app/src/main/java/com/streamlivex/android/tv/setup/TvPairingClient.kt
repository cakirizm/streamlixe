package com.streamlivex.android.tv.setup

import com.streamlivex.android.tv.data.TvProviderConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val PAIR_BASE_URL = "https://streamlivex.com"

data class TvPairSession(
    val code: String,
    val pairUrl: String,
)

sealed interface TvPairStatus {
    data object Waiting : TvPairStatus
    data object Expired : TvPairStatus
    data class Ready(val provider: TvProviderConfig) : TvPairStatus
}

class TvPairingClient {

    fun createSession(): Result<TvPairSession> = runCatching {
        val connection =
            openConnection(
                url = "$PAIR_BASE_URL/api/pair",
                method = "POST",
            )

        try {
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8",
            )
            connection.doOutput = true

            connection.outputStream.use {
                it.write(
                    """{"action":"create"}"""
                        .toByteArray(Charsets.UTF_8),
                )
            }

            val root = JSONObject(readResponse(connection))
            val code = root.optString("code").trim()

            if (code.isBlank()) {
                error(
                    root.optString("error")
                        .ifBlank { "Eşleştirme kodu alınamadı." },
                )
            }

            TvPairSession(
                code = code,
                pairUrl = "$PAIR_BASE_URL/pair/$code",
            )
        } finally {
            connection.disconnect()
        }
    }

    fun checkStatus(code: String): Result<TvPairStatus> = runCatching {
        val connection =
            openConnection(
                url = "$PAIR_BASE_URL/api/pair?code=${
                    URLEncoder.encode(code, "UTF-8")
                }",
                method = "GET",
            )

        try {
            val root = JSONObject(readResponse(connection))

            when (root.optString("status").lowercase()) {
                "ready" -> {
                    val row =
                        root.optJSONObject("provider")
                            ?: error(
                                "Eşleştirme tamamlandı ancak sağlayıcı bilgisi alınamadı.",
                            )

                    val method = row.optString("method").trim().lowercase()

                    if (
                        method.isNotBlank() &&
                        method != "xtream"
                    ) {
                        error(
                            "TV QR bağlantısı şu anda Xtream Codes hesaplarını destekliyor.",
                        )
                    }

                    val server =
                        row.optString("server")
                            .trim()
                            .trimEnd('/')

                    val username =
                        row.optString("username")
                            .trim()

                    val password =
                        row.optString("password")

                    if (
                        server.isBlank() ||
                        username.isBlank() ||
                        password.isBlank()
                    ) {
                        error(
                            "Telefondan gelen Xtream bilgileri eksik.",
                        )
                    }

                    TvPairStatus.Ready(
                        TvProviderConfig(
                            name =
                                row.optString("name")
                                    .trim()
                                    .ifBlank { "Oynatma Listem" },
                            server = server,
                            username = username,
                            password = password,
                        ),
                    )
                }

                "expired" -> TvPairStatus.Expired
                else -> TvPairStatus.Waiting
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        method: String,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection)
            .apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "StreamLiveX-TV/1.0")
            }

    private fun readResponse(
        connection: HttpURLConnection,
    ): String {
        val status = connection.responseCode
        val stream =
            if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        val text =
            stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

        if (status !in 200..299) {
            error("Eşleştirme servisi HTTP $status hatası verdi.")
        }

        if (text.isBlank()) {
            error("Eşleştirme servisi boş yanıt döndürdü.")
        }

        return text
    }
}
