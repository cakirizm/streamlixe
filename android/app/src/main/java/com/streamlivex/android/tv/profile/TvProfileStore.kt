package com.streamlivex.android.tv.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class TvProfile(
    val id: String,
    val name: String,
    val avatar: String,
    val isKids: Boolean,
    val pinHash: String?,
    val isGuest: Boolean = false,
)

object TvActiveScope {
    @Volatile
    var playlistId: String =
        "legacy"

    @Volatile
    var profileId: String =
        "default"

    @Volatile
    var kidsMode: Boolean =
        false

    fun activate(
        playlistId: String?,
        profile: TvProfile,
    ) {
        this.playlistId =
            playlistId
                ?.ifBlank {
                    "legacy"
                }
                ?: "legacy"
        profileId =
            profile.id
        kidsMode =
            profile.isKids
    }

    fun storageKey(): String =
        sanitize(
            "${playlistId}_${profileId}",
        )

    private fun sanitize(
        value: String,
    ): String =
        value.replace(
            Regex(
                "[^A-Za-z0-9_-]",
            ),
            "_",
        )
}

object TvProfilePolicy {
    private val blockedTokens =
        listOf(
            "adult",
            "xxx",
            "18+",
            "18 plus",
            "erotic",
            "erotik",
            "porn",
            "porno",
        )

    fun allow(
        title: String?,
        category: String? = null,
    ): Boolean {
        if (!TvActiveScope.kidsMode) {
            return true
        }

        val haystack =
            listOfNotNull(
                title,
                category,
            )
                .joinToString(" ")
                .lowercase()

        return blockedTokens.none {
            haystack.contains(it)
        }
    }
}

class TvProfileStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                "streamlivex_tv_profiles_v1",
                Context.MODE_PRIVATE,
            )

    fun all(): List<TvProfile> {
        val rows =
            readAll()
        if (rows.isNotEmpty()) {
            return rows
        }

        val first =
            TvProfile(
                id =
                    UUID.randomUUID()
                        .toString(),
                name = "Profil 1",
                avatar = "🙂",
                isKids = false,
                pinHash = null,
            )
        writeAll(
            listOf(first),
        )
        return listOf(first)
    }

    fun add(
        name: String,
        avatar: String,
        isKids: Boolean,
        pin: String?,
        isGuest: Boolean = false,
    ): TvProfile {
        val profile =
            TvProfile(
                id =
                    UUID.randomUUID()
                        .toString(),
                name =
                    name.trim()
                        .ifBlank {
                            "Profil"
                        },
                avatar =
                    avatar.ifBlank {
                        if (isKids) {
                            "🧒"
                        } else {
                            "🙂"
                        }
                    },
                isKids =
                    isKids,
                pinHash =
                    pin
                        ?.takeIf {
                            it.length == 4
                        }
                        ?.let {
                            hashPin(it)
                        },
                isGuest =
                    isGuest,
            )

        writeAll(
            all() + profile,
        )
        return profile
    }

    fun update(
        id: String,
        name: String,
        avatar: String,
        isKids: Boolean,
        pin: String?,
        keepExistingPin: Boolean = true,
    ): TvProfile? {
        val rows =
            all().toMutableList()
        val index =
            rows.indexOfFirst {
                it.id == id
            }

        if (index < 0) {
            return null
        }

        val current =
            rows[index]

        val nextPinHash =
            when {
                pin != null &&
                    pin.length == 4 ->
                    hashPin(pin)

                keepExistingPin ->
                    current.pinHash

                else ->
                    null
            }

        val updated =
            current.copy(
                name =
                    name.trim()
                        .ifBlank {
                            current.name
                        },
                avatar =
                    avatar.ifBlank {
                        current.avatar
                    },
                isKids =
                    isKids,
                pinHash =
                    nextPinHash,
            )

        rows[index] =
            updated
        writeAll(rows)
        return updated
    }

    fun remove(id: String) {
        val rows =
            all().filterNot {
                it.id == id
            }
        if (rows.isEmpty()) {
            writeAll(emptyList())
            all()
        } else {
            writeAll(rows)
        }
    }

    fun verifyPin(
        profile: TvProfile,
        pin: String,
    ): Boolean {
        val expected =
            profile.pinHash
                ?: return true
        return hashPin(pin) ==
            expected
    }

    private fun hashPin(
        pin: String,
    ): String {
        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256",
                )
                .digest(
                    pin.toByteArray(),
                )

        return digest.joinToString(
            "",
        ) {
            "%02x".format(it)
        }
    }

    private fun readAll(): List<TvProfile> {
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
                    add(
                        TvProfile(
                            id =
                                row.optString(
                                    "id",
                                ),
                            name =
                                row.optString(
                                    "name",
                                ),
                            avatar =
                                row.optString(
                                    "avatar",
                                    "🙂",
                                ),
                            isKids =
                                row.optBoolean(
                                    "isKids",
                                    false,
                                ),
                            pinHash =
                                row.optString(
                                    "pinHash",
                                )
                                    .ifBlank {
                                        null
                                    },
                            isGuest =
                                row.optBoolean(
                                    "isGuest",
                                    false,
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
        rows: List<TvProfile>,
    ) {
        val array =
            JSONArray()
        rows.forEach {
            profile ->

            array.put(
                JSONObject()
                    .put(
                        "id",
                        profile.id,
                    )
                    .put(
                        "name",
                        profile.name,
                    )
                    .put(
                        "avatar",
                        profile.avatar,
                    )
                    .put(
                        "isKids",
                        profile.isKids,
                    )
                    .put(
                        "pinHash",
                        profile.pinHash
                            ?: "",
                    )
                    .put(
                        "isGuest",
                        profile.isGuest,
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
}
