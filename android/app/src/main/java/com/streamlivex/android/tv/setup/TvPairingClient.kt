package com.streamlivex.android.tv.setup

import com.streamlivex.android.tv.data.TvProviderConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

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
    private var server: ServerSocket? = null
    private var worker: Thread? = null
    private val readyProvider = AtomicReference<TvProviderConfig?>(null)
    private var expiresAt: Long = 0L

    fun createSession(): Result<TvPairSession> = runCatching {
        close()
        readyProvider.set(null)

        val socket = ServerSocket(0)
        socket.reuseAddress = true
        server = socket
        expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L

        val host = localIpv4()
            ?: error("TV'nin yerel IP adresi bulunamadı.")
        val code = (100000..999999).random().toString()
        val url = "http://$host:${socket.localPort}/pair?code=$code"

        worker = Thread {
            while (!socket.isClosed && System.currentTimeMillis() < expiresAt) {
                runCatching {
                    val client = socket.accept()
                    client.use { connection ->
                        connection.soTimeout = 10_000
                        val reader = BufferedReader(
                            InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8,
                            ),
                        )

                        val requestLine = reader.readLine().orEmpty()
                        if (requestLine.isBlank()) return@use

                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) break
                            val split = line.indexOf(':')
                            if (split > 0) {
                                headers[line.substring(0, split).trim().lowercase()] =
                                    line.substring(split + 1).trim()
                            }
                        }

                        val method = requestLine.substringBefore(' ').uppercase()
                        val path = requestLine.substringAfter(' ').substringBefore(' ')

                        if (method == "GET" && path.startsWith("/pair")) {
                            writeHtml(
                                connection.getOutputStream(),
                                pairingPage(code),
                            )
                            return@use
                        }

                        if (method == "POST" && path.startsWith("/pair")) {
                            val length = headers["content-length"]?.toIntOrNull() ?: 0
                            val chars = CharArray(length.coerceAtMost(16_384))
                            var total = 0
                            while (total < chars.size) {
                                val read = reader.read(chars, total, chars.size - total)
                                if (read <= 0) break
                                total += read
                            }
                            val body = String(chars, 0, total)
                            val form = parseForm(body)
                            val submittedCode = form["code"].orEmpty()

                            if (submittedCode != code) {
                                writeHtml(
                                    connection.getOutputStream(),
                                    resultPage("Kod eşleşmedi.", false),
                                    status = "400 Bad Request",
                                )
                                return@use
                            }

                            val serverValue =
                                form["server"]
                                    .orEmpty()
                                    .trim()
                                    .trimEnd('/')
                            val username = form["username"].orEmpty().trim()
                            val password = form["password"].orEmpty()
                            val name =
                                form["name"]
                                    .orEmpty()
                                    .trim()
                                    .ifBlank { "Oynatma Listem" }

                            if (
                                serverValue.isBlank() ||
                                username.isBlank() ||
                                password.isBlank()
                            ) {
                                writeHtml(
                                    connection.getOutputStream(),
                                    resultPage("Sunucu, kullanıcı adı ve şifre zorunlu.", false),
                                    status = "400 Bad Request",
                                )
                                return@use
                            }

                            readyProvider.set(
                                TvProviderConfig(
                                    name = name,
                                    server = serverValue,
                                    username = username,
                                    password = password,
                                ),
                            )
                            writeHtml(
                                connection.getOutputStream(),
                                resultPage("TV bağlantısı tamamlandı. Bu sayfayı kapatabilirsin.", true),
                            )
                            return@use
                        }

                        writeHtml(
                            connection.getOutputStream(),
                            resultPage("Sayfa bulunamadı.", false),
                            status = "404 Not Found",
                        )
                    }
                }
            }
        }.apply {
            name = "StreamLiveX-LocalPairing"
            isDaemon = true
            start()
        }

        TvPairSession(
            code = code,
            pairUrl = url,
        )
    }

    fun checkStatus(code: String): Result<TvPairStatus> = runCatching {
        if (System.currentTimeMillis() >= expiresAt) {
            return@runCatching TvPairStatus.Expired
        }

        val provider = readyProvider.get()
        if (provider != null) {
            TvPairStatus.Ready(provider)
        } else {
            TvPairStatus.Waiting
        }
    }

    fun close() {
        runCatching { server?.close() }
        server = null
        worker = null
        readyProvider.set(null)
    }

    private fun localIpv4(): String? {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        return interfaces
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .mapNotNull { address ->
                val host = address.hostAddress ?: return@mapNotNull null
                if (
                    host.contains(':') ||
                    host.startsWith("127.") ||
                    address.isLoopbackAddress
                ) {
                    null
                } else {
                    host
                }
            }
            .firstOrNull()
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) return@mapNotNull null
                val key =
                    URLDecoder.decode(
                        pair.substring(0, idx),
                        StandardCharsets.UTF_8.name(),
                    )
                val value =
                    URLDecoder.decode(
                        pair.substring(idx + 1),
                        StandardCharsets.UTF_8.name(),
                    )
                key to value
            }
            .toMap()

    private fun pairingPage(code: String): String =
        """
        <!doctype html>
        <html lang="tr">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>StreamLiveX TV Bağlantısı</title>
          <style>
            body{font-family:Arial,sans-serif;background:#080b12;color:#fff;margin:0;padding:24px}
            .card{max-width:520px;margin:30px auto;background:#111827;padding:24px;border-radius:18px}
            input{box-sizing:border-box;width:100%;padding:13px;margin:7px 0 14px;border-radius:9px;border:1px solid #334155;background:#0f172a;color:#fff}
            button{width:100%;padding:14px;border:0;border-radius:9px;background:#2563eb;color:#fff;font-weight:700}
            small{color:#94a3b8}
          </style>
        </head>
        <body>
          <div class="card">
            <h1>StreamLiveX</h1>
            <p>TV ile aynı Wi‑Fi / yerel ağda kal.</p>
            <form method="post" action="/pair">
              <input type="hidden" name="code" value="${html(code)}">
              <label>Liste adı</label>
              <input name="name" value="Oynatma Listem">
              <label>Sunucu</label>
              <input name="server" placeholder="http://sunucu.com:8080" required>
              <label>Kullanıcı adı</label>
              <input name="username" required>
              <label>Şifre</label>
              <input name="password" type="password" required>
              <button type="submit">TV'ye Bağla</button>
            </form>
            <small>Bilgiler bu yerel ağ üzerinden doğrudan TV'ye gönderilir.</small>
          </div>
        </body>
        </html>
        """.trimIndent()

    private fun resultPage(message: String, success: Boolean): String =
        """
        <!doctype html>
        <html lang="tr">
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="font-family:Arial;background:#080b12;color:#fff;padding:40px;text-align:center">
          <h2 style="color:${if (success) "#60a5fa" else "#f87171"}">${html(message)}</h2>
        </body>
        </html>
        """.trimIndent()

    private fun writeHtml(
        output: java.io.OutputStream,
        html: String,
        status: String = "200 OK",
    ) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val headers =
            "HTTP/1.1 $status\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        output.write(
            headers.toByteArray(
                StandardCharsets.UTF_8,
            ),
        )
        output.write(bytes)
        output.flush()
    }

    private fun html(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
