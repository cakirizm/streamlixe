package com.streamlivex.android.tv.data

import android.content.Context

data class TvPlaybackSettings(
    val fitMode: String = "fit",
    val audioLanguage: String = "auto",
    val subtitleLanguage: String = "tr",
    val subtitlesEnabled: Boolean = true,
    val autoNextEpisode: Boolean = true,
)

class TvPlaybackSettingsStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "streamlivex_tv_playback",
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
            .putBoolean("auto_next_episode", settings.autoNextEpisode)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
