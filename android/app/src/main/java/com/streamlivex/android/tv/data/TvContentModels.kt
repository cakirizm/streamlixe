package com.streamlivex.android.tv.data

data class NativeVodCategory(
    val id: String,
    val name: String,
)

data class NativeVodItem(
    val id: String,
    val streamId: String,
    val categoryId: String,
    val name: String,
    val poster: String?,
    val plot: String?,
    val rating: Double?,
    val year: String?,
    val genre: String?,
    val extension: String,
    val streamUrl: String,
)

data class NativeSeriesCategory(
    val id: String,
    val name: String,
)

data class NativeSeriesItem(
    val id: String,
    val seriesId: String,
    val categoryId: String,
    val name: String,
    val cover: String?,
    val plot: String?,
    val rating: Double?,
    val year: String?,
    val genre: String?,
)

data class NativeSeriesEpisode(
    val id: String,
    val episodeId: String,
    val season: Int,
    val episode: Int,
    val name: String,
    val extension: String,
    val streamUrl: String,
)

data class NativeSeriesInfo(
    val series: NativeSeriesItem,
    val episodes: List<NativeSeriesEpisode>,
)
