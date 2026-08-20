package com.streamlivex.android.tv.setup

import android.content.Context
import com.streamlivex.android.tv.data.TvProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class TvPlaylistAccount(
    val id: String,
    val provider: TvProviderConfig,
    val addedAt: Long,
    val lastUsedAt: Long,
)

class TvPlaylistRegistry(
    private val context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "streamlivex_tv_playlists_v1",
            Context.MODE_PRIVATE,
        )

    fun all(): List<TvPlaylistAccount> {
        migrateLegacyIfNeeded()
        return readAll()
            .sortedByDescending { it.lastUsedAt }
    }

    fun activeId(): String? {
        migrateLegacyIfNeeded()
        return prefs.getString(
            "active_id",
            null,
        )
    }

    fun active(): TvPlaylistAccount? {
        val id = activeId()
        val rows = readAll()
        return rows.firstOrNull {
            it.id == id
        } ?: rows.firstOrNull()
    }

    fun addOrUpdate(
        provider: TvProviderConfig,
        makeActive: Boolean = true,
    ): TvPlaylistAccount {
        migrateLegacyIfNeeded()

        val now =
            System.currentTimeMillis()
        val rows =
            readAll().toMutableList()

        val existing =
            rows.firstOrNull {
                sameProvider(
                    it.provider,
                    provider,
                )
            }

        val account =
            if (existing != null) {
                existing.copy(
                    provider =
                        provider.copy(
                            name =
                                provider.name
                                    .ifBlank {
                                        existing
                                            .provider
                                            .name
                                    },
                        ),
                    lastUsedAt =
                        if (makeActive) {
                            now
                        } else {
                            existing
                                .lastUsedAt
                        },
                )
            } else {
                TvPlaylistAccount(
                    id =
                        UUID.randomUUID()
                            .toString(),
                    provider = provider,
                    addedAt = now,
                    lastUsedAt = now,
                )
            }

        rows.removeAll {
            it.id == account.id
        }
        rows.add(account)
        writeAll(rows)

        if (makeActive) {
            prefs.edit()
                .putString(
                    "active_id",
                    account.id,
                )
                .apply()
        }

        return account
    }

    fun select(id: String): TvPlaylistAccount? {
        val rows =
            readAll().toMutableList()
        val index =
            rows.indexOfFirst {
                it.id == id
            }
        if (index < 0) return null

        val selected =
            rows[index].copy(
                lastUsedAt =
                    System.currentTimeMillis(),
            )
        rows[index] = selected
        writeAll(rows)

        prefs.edit()
            .putString(
                "active_id",
                selected.id,
            )
            .apply()

        return selected
    }

    fun remove(id: String): TvPlaylistAccount? {
        val rows =
            readAll()
                .filterNot {
                    it.id == id
                }
        writeAll(rows)

        val next =
            rows.maxByOrNull {
                it.lastUsedAt
            }

        prefs.edit()
            .putString(
                "active_id",
                next?.id,
            )
            .apply()

        return next
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun migrateLegacyIfNeeded() {
        if (
            prefs.getBoolean(
                "legacy_checked",
                false,
            )
        ) {
            return
        }

        val legacy =
            context.getSharedPreferences(
                "streamlivex_tv",
                Context.MODE_PRIVATE,
            )
        val server =
            legacy.getString(
                "xtream_server",
                "",
            ).orEmpty().trim()
        val username =
            legacy.getString(
                "xtream_username",
                "",
            ).orEmpty().trim()
        val password =
            legacy.getString(
                "xtream_password",
                "",
            ).orEmpty()
        val name =
            legacy.getString(
                "xtream_provider_name",
                "Oynatma Listem",
            ).orEmpty()
                .ifBlank {
                    "Oynatma Listem"
                }

        prefs.edit()
            .putBoolean(
                "legacy_checked",
                true,
            )
            .apply()

        if (
            server.isBlank() ||
            username.isBlank() ||
            password.isBlank()
        ) {
            return
        }

        addOrUpdate(
            TvProviderConfig(
                name = name,
                server = server,
                username = username,
                password = password,
            ),
            makeActive = true,
        )
    }

    private fun readAll(): List<TvPlaylistAccount> {
        val raw =
            prefs.getString(
                "rows",
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
                    val provider =
                        TvProviderConfig(
                            name =
                                row.optString(
                                    "name",
                                ),
                            server =
                                row.optString(
                                    "server",
                                ),
                            username =
                                row.optString(
                                    "username",
                                ),
                            password =
                                row.optString(
                                    "password",
                                ),
                        )

                    if (
                        provider.server
                            .isBlank() ||
                        provider.username
                            .isBlank() ||
                        provider.password
                            .isBlank()
                    ) {
                        continue
                    }

                    add(
                        TvPlaylistAccount(
                            id =
                                row.optString(
                                    "id",
                                ),
                            provider =
                                provider,
                            addedAt =
                                row.optLong(
                                    "addedAt",
                                    0L,
                                ),
                            lastUsedAt =
                                row.optLong(
                                    "lastUsedAt",
                                    0L,
                                ),
                        ),
                    )
                }
            }
        }.getOrDefault(
            emptyList(),
        )
    }

    private fun writeAll(
        rows: List<TvPlaylistAccount>,
    ) {
        val array = JSONArray()
        rows.forEach {
            account ->

            array.put(
                JSONObject()
                    .put(
                        "id",
                        account.id,
                    )
                    .put(
                        "name",
                        account.provider.name,
                    )
                    .put(
                        "server",
                        account.provider.server,
                    )
                    .put(
                        "username",
                        account.provider.username,
                    )
                    .put(
                        "password",
                        account.provider.password,
                    )
                    .put(
                        "addedAt",
                        account.addedAt,
                    )
                    .put(
                        "lastUsedAt",
                        account.lastUsedAt,
                    ),
            )
        }

        prefs.edit()
            .putString(
                "rows",
                array.toString(),
            )
            .apply()
    }

    private fun sameProvider(
        a: TvProviderConfig,
        b: TvProviderConfig,
    ): Boolean =
        a.server
            .trimEnd('/')
            .equals(
                b.server
                    .trimEnd('/'),
                ignoreCase = true,
            ) &&
            a.username ==
                b.username
}
