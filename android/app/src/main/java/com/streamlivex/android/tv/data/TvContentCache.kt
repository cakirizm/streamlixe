package com.streamlivex.android.tv.data

object TvContentCache {
    var vodCategories: List<NativeVodCategory>? = null
    var seriesCategories: List<NativeSeriesCategory>? = null

    val vodByCategory = LinkedHashMap<String, List<NativeVodItem>>()
    val seriesByCategory = LinkedHashMap<String, List<NativeSeriesItem>>()
    val seriesDetails = LinkedHashMap<String, NativeSeriesInfo>()

    var movieCategoryId: String? = null
    var movieFocusedId: String? = null
    val movieFocusedByCategory = LinkedHashMap<String, String>()
    var movieDetailId: String? = null

    var seriesCategoryId: String? = null
    var seriesFocusedId: String? = null
    val seriesFocusedByCategory = LinkedHashMap<String, String>()
    var seriesDetailId: String? = null

    fun clear() {
        vodCategories = null
        seriesCategories = null
        vodByCategory.clear()
        seriesByCategory.clear()
        seriesDetails.clear()

        movieCategoryId = null
        movieFocusedId = null
        movieFocusedByCategory.clear()
        movieDetailId = null
        seriesCategoryId = null
        seriesFocusedId = null
        seriesFocusedByCategory.clear()
        seriesDetailId = null
    }
}
