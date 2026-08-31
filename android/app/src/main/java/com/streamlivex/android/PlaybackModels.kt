package com.streamlivex.android

import android.net.Uri
import org.json.JSONObject
import java.util.UUID

data class PlaybackRequest(
    val sessionId: String,
    val item: PlaybackItem,
    val resumeTimeMs: Long = 0,
    val preferences: PlaybackPreferences = PlaybackPreferences(),
    val fromWebBridge: Boolean = false,
)

data class PlaybackItem(
    val name: String,
    val url: String,
    val kind: String,
    val subtitles: List<PlaybackSubtitle> = emptyList(),
    // Web tarafi, aktif bolumun sezon listesinde bir sonraki bolum oldugunu bildirir; oynaticida
    // "Sonraki bolum" butonu buna gore gorunur.
    val hasNext: Boolean = false,
) {
    val isLive: Boolean get() = kind.equals("live", ignoreCase = true)
}

data class PlaybackSubtitle(
    val src: String,
    val label: String,
    val language: String,
)

data class PlaybackPreferences(
    val audioLanguage: String = "auto",
    val subtitleMode: String = "auto",
    val subtitleLanguage: String = "tr",
    val subtitleSize: Int = 100,
    val subtitleColor: String = "#ffffff",
    val subtitleBackground: String = "shadow",
    val playbackRate: Float = 1f,
    val quality: String = "Otomatik",
    val showInfo: Boolean = true,
)

data class PlaybackProgress(val currentSeconds: Double = 0.0, val durationSeconds: Double = 0.0)

data class PlaybackBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val visible: Boolean,
)

data class InlinePlaybackSession(
    val request: PlaybackRequest,
    val bounds: PlaybackBounds,
)

// Önizlemeden tam ekrana geçerken (aynı sessionId reuse edildiğinde) hangi format/kaynak
// denemesinin zaten çalıştığını hatırlar; tam ekran oynatıcı sıfırdan HLS/MPEG-TS/native
// sırasıyla denemek yerine direkt bilinen çalışan adayı kullanır, boylece "tekrar yükleniyor"
// hissi buyuk olcude azalir.
object PlaybackCandidateMemory {
    private val map = mutableMapOf<String, Int>()
    fun remember(sessionId: String, candidateIndex: Int) {
        map[sessionId] = candidateIndex
    }
    fun recall(sessionId: String): Int = map[sessionId] ?: 0
    fun forget(sessionId: String) {
        map.remove(sessionId)
    }
}

// Ayni (sessionId, candidateIndex, retry) kombinasyonu icin player.setMediaItem/prepare/play'in
// birden fazla kez cagrilmasini onler. On izleme ile tam ekran ayni paylasilan ExoPlayer'i
// kullandigi icin, on izleme zaten bu adayla basariyla baslatmissa, tam ekrana gecerken (ya da
// tam ekrandan geri donerken) BIR DAHA baslatilmiyor -- player.isPlaying/playbackState gibi
// zamanlamaya bagli (race'e acik) kontrollere guvenmek yerine kesin bir kayit tutuyoruz.
object PlaybackStartTracker {
    private val started = mutableSetOf<String>()
    fun hasStarted(key: String): Boolean = started.contains(key)
    fun markStarted(key: String) {
        started.add(key)
    }
    fun clearSession(sessionId: String) {
        started.removeAll { it.startsWith("$sessionId:") }
    }
}

sealed interface BridgeCommand {
    data class Play(val request: PlaybackRequest) : BridgeCommand
    data class Close(val sessionId: String?) : BridgeCommand
    data class Preview(val session: InlinePlaybackSession) : BridgeCommand
    data class PreviewLayout(val sessionId: String, val bounds: PlaybackBounds) : BridgeCommand
    data class ClosePreview(val sessionId: String?) : BridgeCommand
    data class PromotePreview(val sessionId: String) : BridgeCommand
    object ConfirmExit : BridgeCommand
}

object BridgeMessageParser {
    fun parse(payload: String): BridgeCommand? {
        val root = JSONObject(payload)
        return when (root.optString("type").lowercase()) {
            "confirm-exit" -> BridgeCommand.ConfirmExit
            "close" -> BridgeCommand.Close(root.optString("sessionId").ifBlank { null })
            "play" -> parsePlay(root)?.let(BridgeCommand::Play)
            "preview" -> {
                val request = parsePlay(root)
                val bounds = parseBounds(root)
                if (request != null && bounds != null) {
                    BridgeCommand.Preview(InlinePlaybackSession(request, bounds))
                } else {
                    null
                }
            }
            "preview-layout" -> {
                val sessionId = root.optString("sessionId")
                val bounds = parseBounds(root)
                if (sessionId.isNotBlank() && bounds != null) {
                    BridgeCommand.PreviewLayout(sessionId, bounds)
                } else {
                    null
                }
            }
            "close-preview" -> BridgeCommand.ClosePreview(root.optString("sessionId").ifBlank { null })
            "promote-preview" -> root.optString("sessionId").ifBlank { null }?.let(BridgeCommand::PromotePreview)
            else -> null
        }
    }

    private fun parseBounds(root: JSONObject): PlaybackBounds? {
        val bounds = root.optJSONObject("bounds") ?: return null
        val width = bounds.optDouble("width", 0.0).toFloat()
        val height = bounds.optDouble("height", 0.0).toFloat()
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
        return PlaybackBounds(
            left = bounds.optDouble("left", 0.0).toFloat().takeIf { it.isFinite() } ?: 0f,
            top = bounds.optDouble("top", 0.0).toFloat().takeIf { it.isFinite() } ?: 0f,
            width = width,
            height = height,
            visible = bounds.optBoolean("visible", true),
        )
    }

    private fun parsePlay(root: JSONObject): PlaybackRequest? {
        val itemJson = root.optJSONObject("item") ?: return null
        val source = itemJson.optString("url").trim()
        val uri = Uri.parse(source)
        if (source.isBlank() || uri.scheme?.lowercase() !in setOf("http", "https")) return null

        val subtitles = buildList {
            val rows = itemJson.optJSONArray("subtitles") ?: return@buildList
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val src = row.optString("src").trim()
                val subtitleUri = Uri.parse(src)
                if (src.isNotBlank() && subtitleUri.scheme?.lowercase() in setOf("http", "https")) {
                    add(
                        PlaybackSubtitle(
                            src = src,
                            label = row.optString("label").ifBlank { "Subtitle ${index + 1}" },
                            language = row.optString("language").ifBlank { "und" },
                        ),
                    )
                }
            }
        }

        val preferencesJson = root.optJSONObject("preferences")
        val preferences = PlaybackPreferences(
            audioLanguage = preferencesJson?.optString("audioLanguage")?.ifBlank { "auto" } ?: "auto",
            subtitleMode = preferencesJson?.optString("subtitleMode")?.ifBlank { "auto" } ?: "auto",
            subtitleLanguage = preferencesJson?.optString("subtitleLanguage")?.ifBlank { "tr" } ?: "tr",
            subtitleSize = (preferencesJson?.optInt("subtitleSize", 100) ?: 100).coerceIn(70, 180),
            subtitleColor = preferencesJson?.optString("subtitleColor")?.ifBlank { "#ffffff" } ?: "#ffffff",
            subtitleBackground = preferencesJson?.optString("subtitleBackground")?.ifBlank { "shadow" } ?: "shadow",
            playbackRate = (preferencesJson?.optDouble("playbackRate", 1.0)?.toFloat() ?: 1f).coerceIn(0.5f, 2f),
            quality = preferencesJson?.optString("quality")?.ifBlank { "Otomatik" } ?: "Otomatik",
            showInfo = preferencesJson?.optBoolean("showInfo", true) ?: true,
        )

        return PlaybackRequest(
            sessionId = root.optString("sessionId").ifBlank { UUID.randomUUID().toString() },
            resumeTimeMs = root.optLong("resumeTime", 0).coerceAtLeast(0),
            fromWebBridge = true,
            item = PlaybackItem(
                name = itemJson.optString("name").ifBlank { "StreamLiveX" },
                url = source,
                kind = itemJson.optString("kind").ifBlank { "movie" },
                subtitles = subtitles,
                hasNext = itemJson.optBoolean("hasNext", false),
            ),
            preferences = preferences,
        )
    }
}
