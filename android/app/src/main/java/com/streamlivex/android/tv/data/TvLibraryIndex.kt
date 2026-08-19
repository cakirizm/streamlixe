package com.streamlivex.android.tv.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.Normalizer
import java.util.Locale

class TvLibraryIndex(
    context: Context,
) : SQLiteOpenHelper(
    context.applicationContext,
    "streamlivex_tv_library_v3.db",
    null,
    1,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE media (
                provider_key TEXT NOT NULL,
                kind TEXT NOT NULL,
                local_id TEXT NOT NULL,
                title_key TEXT NOT NULL,
                name TEXT NOT NULL,
                stream_url TEXT NOT NULL,
                artwork TEXT,
                series_id TEXT,
                category_id TEXT,
                PRIMARY KEY(provider_key, kind, local_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_media_title ON media(provider_key, kind, title_key)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS media")
        onCreate(db)
    }

    fun providerKey(provider: TvProviderConfig): String =
        "${provider.server.trimEnd('/')}|${provider.username}"

    fun clearProvider(provider: TvProviderConfig) {
        writableDatabase.delete(
            "media",
            "provider_key=?",
            arrayOf(providerKey(provider)),
        )
    }

    fun put(provider: TvProviderConfig, item: NativeVodItem) {
        put(
            provider,
            TvIndexedMedia(
                kind = "movie",
                localId = item.id,
                name = item.name,
                streamUrl = item.streamUrl,
                artwork = item.poster,
                seriesId = null,
                categoryId = item.categoryId,
            ),
        )
    }

    fun put(provider: TvProviderConfig, item: NativeSeriesItem) {
        put(
            provider,
            TvIndexedMedia(
                kind = "series",
                localId = item.id,
                name = item.name,
                streamUrl = "",
                artwork = item.cover,
                seriesId = item.seriesId,
                categoryId = item.categoryId,
            ),
        )
    }

    fun put(provider: TvProviderConfig, item: TvIndexedMedia) {
        val values = ContentValues().apply {
            put("provider_key", providerKey(provider))
            put("kind", item.kind)
            put("local_id", item.localId)
            put("title_key", titleKey(item.name))
            put("name", item.name)
            put("stream_url", item.streamUrl)
            put("artwork", item.artwork)
            put("series_id", item.seriesId)
            put("category_id", item.categoryId)
        }
        writableDatabase.insertWithOnConflict(
            "media",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun findByTitle(
        provider: TvProviderConfig,
        kind: String,
        title: String,
    ): TvIndexedMedia? {
        val cursor = readableDatabase.query(
            "media",
            arrayOf(
                "kind",
                "local_id",
                "name",
                "stream_url",
                "artwork",
                "series_id",
                "category_id",
            ),
            "provider_key=? AND kind=? AND title_key=?",
            arrayOf(providerKey(provider), kind, titleKey(title)),
            null,
            null,
            null,
            "1",
        )

        cursor.use {
            if (!it.moveToFirst()) return null
            return TvIndexedMedia(
                kind = it.getString(0),
                localId = it.getString(1),
                name = it.getString(2),
                streamUrl = it.getString(3),
                artwork = it.getString(4),
                seriesId = it.getString(5),
                categoryId = it.getString(6),
            )
        }
    }

    fun suggestions(
        provider: TvProviderConfig,
        kind: String? = null,
        limit: Int = 16,
    ): List<TvIndexedMedia> {
        val where = if (kind == null) "provider_key=?" else "provider_key=? AND kind=?"
        val args =
            if (kind == null) arrayOf(providerKey(provider))
            else arrayOf(providerKey(provider), kind)

        val cursor = readableDatabase.query(
            "media",
            arrayOf(
                "kind",
                "local_id",
                "name",
                "stream_url",
                "artwork",
                "series_id",
                "category_id",
            ),
            where,
            args,
            null,
            null,
            "ROWID DESC",
            limit.toString(),
        )

        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    add(
                        TvIndexedMedia(
                            kind = it.getString(0),
                            localId = it.getString(1),
                            name = it.getString(2),
                            streamUrl = it.getString(3),
                            artwork = it.getString(4),
                            seriesId = it.getString(5),
                            categoryId = it.getString(6),
                        ),
                    )
                }
            }
        }
    }

    fun count(provider: TvProviderConfig): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM media WHERE provider_key=?",
            arrayOf(providerKey(provider)),
        )
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    companion object {
        fun titleKey(value: String): String {
            return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
                .lowercase(Locale.ROOT)
                .replace(
                    Regex(
                        "\\b(19|20)\\d{2}\\b|\\b4k\\b|\\buhd\\b|\\bfhd\\b|\\bhd\\b|\\b1080p\\b|\\b720p\\b",
                        RegexOption.IGNORE_CASE,
                    ),
                    " ",
                )
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
        }
    }
}
