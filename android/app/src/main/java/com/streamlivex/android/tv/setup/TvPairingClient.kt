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

            val text = readResponse(connection)
            val root = JSONObject(text)

            val code =
                root.optString("code")
                    .trim()

            if (code.isBlank()) {
                throw IllegalStateException(
                    root.optString("error")
                        .trim()
                        .ifBlank {
                            "Eşleştirme kodu alınamadı."
                        },
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

    fun checkStatus(
        code: String,
    ): Result<TvPairStatus> = runCatching {
        val encodedCode =
            URLEncoder.encode(
                code,
                "UTF-8",
            )

        val connection =
            openConnection(
                url = "$PAIR_BASE_URL/api/pair?code=$encodedCode",
                method = "GET",
            )

        try {
            val text = readResponse(connection)
            val root = JSONObject(text)

            when (
                root.optString("status")
                    .trim()
                    .lowercase()
            ) {
                "ready" -> {
                    val providerJson =
                        root.optJSONObject("provider")
                            ?: throw IllegalStateException(
                                "Eşleştirme tamamlandı ancak sağlayıcı bilgisi alınamadı.",
                            )

                    val method =
                        providerJson
                            .optString("method")
                            .trim()
                            .lowercase()

                    if (
                        method.isNotBlank() &&
                        method != "xtream"
                    ) {
                        throw IllegalStateException(
                            "TV uygulamasında QR bağlantısı şu anda Xtream Codes hesaplarını destekliyor.",
                        )
                    }

                    val server =
                        providerJson
                            .optString("server")
                            .trim()
                            .trimEnd('/')

                    val username =
                        providerJson
                            .optString("username")
                            .trim()

                    val password =
                        providerJson
                            .optString("password")

                    if (
                        server.isBlank() ||
                        username.isBlank() ||
                        password.isBlank()
                    ) {
                        throw IllegalStateException(
                            "Telefondan gelen Xtream bilgileri eksik.",
                        )
                    }

                    TvPairStatus.Ready(
                        TvProviderConfig(
                            name = providerJson
                                .optString("name")
                                .trim()
                                .ifBlank {
                                    "Oynatma Listem"
                                },
                            server = server,
                            username = username,
                            password = password,
                        ),
                    )
                }

                "expired" -> {
                    TvPairStatus.Expired
                }

                else -> {
                    TvPairStatus.Waiting
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        method: String,
    ): HttpURLConnection {
        return (
            URL(url)
                .openConnection()
                as HttpURLConnection
            ).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true

                setRequestProperty(
                    "Accept",
                    "application/json",
                )

                setRequestProperty(
                    "User-Agent",
                    "StreamLiveX-TV/1.0",
                )
            }
    }

    private fun readResponse(
        connection: HttpURLConnection,
    ): String {
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
                ?.bufferedReader(
                    Charsets.UTF_8,
                )
                ?.use {
                    it.readText()
                }
                .orEmpty()

        if (status !in 200..299) {
            throw IllegalStateException(
                "Eşleştirme servisi HTTP $status hatası verdi.",
            )
        }

        if (text.isBlank()) {
            throw IllegalStateException(
                "Eşleştirme servisi boş yanıt döndürdü.",
            )
        }

        return text
    }
}
