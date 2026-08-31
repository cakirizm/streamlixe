package com.streamlivex.android.tv.data

import android.content.Context
import com.streamlivex.android.tv.profile.TvActiveScope

data class TvPlaybackSettings(
    val fitMode: String = "fit",
    val audioLanguage: String = "auto",
    val subtitleLanguage: String = "tr",
    val subtitlesEnabled: Boolean = true,
    val subtitleSizeSp: Int = 26,
    val subtitleColor: String = "white",
    val subtitleBackground: String = "shadow",
    val subtitleDelayMs: Int = 0,
    val autoNextEpisode: Boolean = true,
)

class TvPlaybackSettingsStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "streamlivex_tv_playback_${TvActiveScope.storageKey()}",
            Context.MODE_PRIVATE,
        )

    fun load(): TvPlaybackSettings =
        TvPlaybackSettings(
            fitMode =
                prefs.getString("fit_mode", "fit")
                    .orEmpty()
                    .ifBlank { "fit" },
            audioLanguage =
                prefs.getString("audio_language", "auto")
                    .orEmpty()
                    .ifBlank { "auto" },
            subtitleLanguage =
                prefs.getString("subtitle_language", "tr")
                    .orEmpty()
                    .ifBlank { "tr" },
            subtitlesEnabled =
                prefs.getBoolean(
                    "subtitles_enabled",
                    true,
                ),
            subtitleSizeSp =
                prefs.getInt("subtitle_size_sp", 26)
                    .coerceIn(18, 42),
            subtitleColor =
                prefs.getString("subtitle_color", "white")
                    .orEmpty()
                    .ifBlank { "white" },
            subtitleBackground =
                prefs.getString("subtitle_background", "shadow")
                    .orEmpty()
                    .ifBlank { "shadow" },
            subtitleDelayMs =
                prefs.getInt("subtitle_delay_ms", 0)
                    .coerceIn(0, 3_000),
            autoNextEpisode =
                prefs.getBoolean(
                    "auto_next_episode",
                    true,
                ),
        )

    fun save(settings: TvPlaybackSettings) {
        prefs.edit()
            .putString("fit_mode", settings.fitMode)
            .putString("audio_language", settings.audioLanguage)
            .putString("subtitle_language", settings.subtitleLanguage)
            .putBoolean("subtitles_enabled", settings.subtitlesEnabled)
            .putInt("subtitle_size_sp", settings.subtitleSizeSp)
            .putString("subtitle_color", settings.subtitleColor)
            .putString("subtitle_background", settings.subtitleBackground)
            .putInt("subtitle_delay_ms", settings.subtitleDelayMs)
            .putBoolean("auto_next_episode", settings.autoNextEpisode)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
