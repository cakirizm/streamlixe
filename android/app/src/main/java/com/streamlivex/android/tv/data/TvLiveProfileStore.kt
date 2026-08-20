package com.streamlivex.android.tv.data

import android.content.Context
import com.streamlivex.android.tv.profile.TvActiveScope

class TvLiveProfileStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                "streamlivex_tv_live_${TvActiveScope.storageKey()}",
                Context.MODE_PRIVATE,
            )

    fun favoriteChannelIds(): Set<String> =
        prefs.getStringSet(
            "favorite_channels",
            emptySet(),
        )
            .orEmpty()
            .toSet()

    fun isFavorite(
        channelId: String,
    ): Boolean =
        favoriteChannelIds()
            .contains(
                channelId,
            )

    fun toggleFavorite(
        channelId: String,
    ): Boolean {
        val rows =
            favoriteChannelIds()
                .toMutableSet()

        val favorite =
            if (
                rows.contains(
                    channelId,
                )
            ) {
                rows.remove(
                    channelId,
                )
                false
            } else {
                rows.add(
                    channelId,
                )
                true
            }

        prefs.edit()
            .putStringSet(
                "favorite_channels",
                rows,
            )
            .apply()

        return favorite
    }

    fun selectedCategoryId(): String =
        prefs.getString(
            "selected_category_id",
            "all",
        )
            .orEmpty()
            .ifBlank {
                "all"
            }

    fun focusedChannelId(): String? =
        prefs.getString(
            "focused_channel_id",
            null,
        )

    fun playbackChannelId(): String? =
        prefs.getString(
            "playback_channel_id",
            null,
        )

    fun lastChannelId(): String? =
        prefs.getString(
            "last_channel_id",
            null,
        )

    fun saveUiState(
        selectedCategoryId: String,
        focusedChannelId: String?,
        playbackChannelId: String?,
    ) {
        prefs.edit()
            .putString(
                "selected_category_id",
                selectedCategoryId,
            )
            .putString(
                "focused_channel_id",
                focusedChannelId,
            )
            .putString(
                "playback_channel_id",
                playbackChannelId,
            )
            .apply()
    }

    fun setLastChannel(
        channelId: String,
    ) {
        prefs.edit()
            .putString(
                "last_channel_id",
                channelId,
            )
            .apply()
    }

    fun clearPlayback() {
        prefs.edit()
            .remove(
                "playback_channel_id",
            )
            .apply()
    }
}
