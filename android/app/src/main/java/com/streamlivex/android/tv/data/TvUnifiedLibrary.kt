package com.streamlivex.android.tv.data

import android.content.Context
import com.streamlivex.android.tv.setup.TvPlaylistRegistry

data class TvUnifiedResult(
    val provider: TvProviderConfig,
    val media: TvIndexedMedia,
    val sourceCount: Int,
)

class TvUnifiedLibrary(
    context: Context,
) {
    private val appContext =
        context.applicationContext
    private val registry =
        TvPlaylistRegistry(appContext)
    private val index =
        TvLibraryIndex(appContext)

    fun search(
        query: String,
        kind: String,
        limitPerProvider: Int = 40,
        totalLimit: Int = 60,
    ): List<TvUnifiedResult> {
        val activeId =
            registry.activeId()
        val playlists =
            registry.all()
                .sortedByDescending {
                    if (it.id == activeId) {
                        1
                    } else {
                        0
                    }
                }

        data class Bucket(
            var provider: TvProviderConfig,
            var media: TvIndexedMedia,
            var count: Int,
            var active: Boolean,
        )

        val buckets =
            linkedMapOf<String, Bucket>()

        playlists.forEach {
            account ->

            index.search(
                provider =
                    account.provider,
                query = query,
                kind = kind,
                limit =
                    limitPerProvider,
            ).forEach {
                media ->

                val key =
                    "$kind|${
                        TvLibraryIndex
                            .titleKey(media.name)
                    }"

                val existing =
                    buckets[key]

                if (existing == null) {
                    buckets[key] =
                        Bucket(
                            provider =
                                account.provider,
                            media =
                                media,
                            count = 1,
                            active =
                                account.id ==
                                    activeId,
                        )
                } else {
                    existing.count += 1

                    if (
                        !existing.active &&
                        account.id ==
                        activeId
                    ) {
                        existing.provider =
                            account.provider
                        existing.media =
                            media
                        existing.active =
                            true
                    }
                }
            }
        }

        return buckets.values
            .take(totalLimit)
            .map {
                TvUnifiedResult(
                    provider =
                        it.provider,
                    media =
                        it.media,
                    sourceCount =
                        it.count,
                )
            }
    }

    fun allReadyProviders(): Int =
        registry.all()
            .count {
                index.isReady(
                    it.provider,
                )
            }
}
