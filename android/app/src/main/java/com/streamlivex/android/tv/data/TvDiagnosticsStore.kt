package com.streamlivex.android.tv.data

import android.content.Context
import android.os.Build
import com.streamlivex.android.tv.setup.TvPlaylistRegistry
import org.json.JSONArray
import org.json.JSONObject

data class TvDiagnosticError(
    val timestamp: Long,
    val module: String,
    val message: String,
)

data class TvLibraryHealth(
    val liveOk: Boolean,
    val moviesOk: Boolean,
    val seriesOk: Boolean,
    val liveCount: Int,
    val movieCategoryCount: Int,
    val seriesCategoryCount: Int,
    val checkedAt: Long,
    val error: String?,
)

class TvDiagnosticsStore(
    context: Context,
) {
    private val appContext =
        context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            "streamlivex_tv_diagnostics_v1",
            Context.MODE_PRIVATE,
        )

    fun log(
        module: String,
        message: String,
    ) {
        val rows =
            errors().toMutableList()
        rows.add(
            0,
            TvDiagnosticError(
                timestamp =
                    System.currentTimeMillis(),
                module = module,
                message =
                    message.take(500),
            ),
        )

        val array =
            JSONArray()
        rows.take(20)
            .forEach {
                row ->

                array.put(
                    JSONObject()
                        .put(
                            "timestamp",
                            row.timestamp,
                        )
                        .put(
                            "module",
                            row.module,
                        )
                        .put(
                            "message",
                            row.message,
                        ),
                )
            }

        prefs.edit()
            .putString(
                "errors",
                array.toString(),
            )
            .apply()
    }

    fun errors(): List<TvDiagnosticError> {
        val raw =
            prefs.getString(
                "errors",
                "[]",
            ).orEmpty()

        return runCatching {
            val array =
                JSONArray(raw)
            buildList {
                for (
                    i in
                    0 until array.length()
                ) {
                    val row =
                        array.optJSONObject(i)
                            ?: continue
                    add(
                        TvDiagnosticError(
                            timestamp =
                                row.optLong(
                                    "timestamp",
                                ),
                            module =
                                row.optString(
                                    "module",
                                ),
                            message =
                                row.optString(
                                    "message",
                                ),
                        ),
                    )
                }
            }
        }.getOrDefault(
            emptyList(),
        )
    }

    fun saveHealth(
        provider: TvProviderConfig,
        health: TvLibraryHealth,
    ) {
        prefs.edit()
            .putString(
                "health_${
                    providerKey(provider)
                }",
                JSONObject()
                    .put(
                        "liveOk",
                        health.liveOk,
                    )
                    .put(
                        "moviesOk",
                        health.moviesOk,
                    )
                    .put(
                        "seriesOk",
                        health.seriesOk,
                    )
                    .put(
                        "liveCount",
                        health.liveCount,
                    )
                    .put(
                        "movieCategoryCount",
                        health.movieCategoryCount,
                    )
                    .put(
                        "seriesCategoryCount",
                        health.seriesCategoryCount,
                    )
                    .put(
                        "checkedAt",
                        health.checkedAt,
                    )
                    .put(
                        "error",
                        health.error
                            ?: "",
                    )
                    .toString(),
            )
            .apply()
    }

    fun health(
        provider: TvProviderConfig,
    ): TvLibraryHealth? {
        val raw =
            prefs.getString(
                "health_${
                    providerKey(provider)
                }",
                null,
            ) ?: return null

        return runCatching {
            val o =
                JSONObject(raw)
            TvLibraryHealth(
                liveOk =
                    o.optBoolean(
                        "liveOk",
                    ),
                moviesOk =
                    o.optBoolean(
                        "moviesOk",
                    ),
                seriesOk =
                    o.optBoolean(
                        "seriesOk",
                    ),
                liveCount =
                    o.optInt(
                        "liveCount",
                    ),
                movieCategoryCount =
                    o.optInt(
                        "movieCategoryCount",
                    ),
                seriesCategoryCount =
                    o.optInt(
                        "seriesCategoryCount",
                    ),
                checkedAt =
                    o.optLong(
                        "checkedAt",
                    ),
                error =
                    o.optString(
                        "error",
                    )
                        .ifBlank {
                            null
                        },
            )
        }.getOrNull()
    }

    fun deviceSummary(): String {
        val maxMemoryMb =
            Runtime.getRuntime()
                .maxMemory() /
                (1024L * 1024L)

        return "Android ${Build.VERSION.RELEASE} · ${Build.MODEL} · JVM RAM limiti ${maxMemoryMb} MB"
    }

    fun playlistCount(): Int =
        TvPlaylistRegistry(
            appContext,
        ).all().size

    private fun providerKey(
        provider: TvProviderConfig,
    ): String =
        "${provider.server.trimEnd('/')}|${provider.username}"
            .replace(
                Regex(
                    "[^A-Za-z0-9_-]",
                ),
                "_",
            )
}

class TvLibraryHealthChecker {
    fun check(
        provider: TvProviderConfig,
    ): TvLibraryHealth {
        val client =
            XtreamClient()

        val live =
            client.loadLiveLibrary(
                provider,
            )
        val movies =
            client.loadVodCategories(
                provider,
            )
        val series =
            client.loadSeriesCategories(
                provider,
            )

        val messages =
            listOfNotNull(
                live.exceptionOrNull()
                    ?.message,
                movies.exceptionOrNull()
                    ?.message,
                series.exceptionOrNull()
                    ?.message,
            )

        return TvLibraryHealth(
            liveOk =
                live.isSuccess,
            moviesOk =
                movies.isSuccess,
            seriesOk =
                series.isSuccess,
            liveCount =
                live.getOrNull()
                    ?.channels
                    ?.size
                    ?: 0,
            movieCategoryCount =
                movies.getOrNull()
                    ?.size
                    ?: 0,
            seriesCategoryCount =
                series.getOrNull()
                    ?.size
                    ?: 0,
            checkedAt =
                System.currentTimeMillis(),
            error =
                messages
                    .joinToString(
                        " | ",
                    )
                    .ifBlank {
                        null
                    },
        )
    }
}
