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
    2,
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
        db.execSQL(
            "CREATE INDEX idx_media_category ON media(provider_key, kind, category_id, local_id)",
        )
        db.execSQL(
            """
            CREATE TABLE library_meta (
                provider_key TEXT PRIMARY KEY,
                ready INTEGER NOT NULL DEFAULT 0,
                item_count INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        db.execSQL("DROP TABLE IF EXISTS media")
        db.execSQL("DROP TABLE IF EXISTS library_meta")
        onCreate(db)
    }

    fun providerKey(provider: TvProviderConfig): String =
        "${provider.server.trimEnd('/')}|${provider.username}"

    fun isReady(provider: TvProviderConfig): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT ready FROM library_meta WHERE provider_key=? LIMIT 1",
            arrayOf(providerKey(provider)),
        )
        cursor.use {
            return it.moveToFirst() && it.getInt(0) == 1
        }
    }

    fun count(provider: TvProviderConfig): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT item_count FROM library_meta WHERE provider_key=? LIMIT 1",
            arrayOf(providerKey(provider)),
        )
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }

        val fallback = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM media WHERE provider_key=?",
            arrayOf(providerKey(provider)),
        )
        fallback.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun clearProvider(provider: TvProviderConfig) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val key = providerKey(provider)
            db.delete("media", "provider_key=?", arrayOf(key))
            db.delete("library_meta", "provider_key=?", arrayOf(key))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Rebuilds the full VOD + Series index without ever holding the full library in RAM.
     * All inserts run in one SQLite transaction, which is dramatically faster and produces
     * much less I/O pressure than one transaction per item.
     */
    fun rebuildProvider(
        provider: TvProviderConfig,
        client: XtreamClient,
        onProgress: (stage: String, processed: Int) -> Unit = { _, _ -> },
    ): Result<Int> = runCatching {
        val db = writableDatabase
        val key = providerKey(provider)
        var processed = 0

        db.beginTransaction()
        try {
            db.delete("media", "provider_key=?", arrayOf(key))
            db.delete("library_meta", "provider_key=?", arrayOf(key))

            onProgress("movies", 0)
            client.scanVod(provider) { item ->
                insert(db, provider, item.toIndexed())
                processed += 1
                if (processed % 100 == 0) {
                    onProgress("movies", processed)
                }
            }.getOrThrow()

            onProgress("series", processed)
            client.scanSeries(provider) { item ->
                insert(db, provider, item.toIndexed())
                processed += 1
                if (processed % 100 == 0) {
                    onProgress("series", processed)
                }
            }.getOrThrow()

            val meta = ContentValues().apply {
                put("provider_key", key)
                put("ready", 1)
                put("item_count", processed)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(
                "library_meta",
                null,
                meta,
                SQLiteDatabase.CONFLICT_REPLACE,
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        onProgress("done", processed)
        processed
    }

    fun put(
        provider: TvProviderConfig,
        item: NativeVodItem,
    ) {
        put(provider, item.toIndexed())
    }

    fun put(
        provider: TvProviderConfig,
        item: NativeSeriesItem,
    ) {
        put(provider, item.toIndexed())
    }

    fun put(
        provider: TvProviderConfig,
        item: TvIndexedMedia,
    ) {
        insert(writableDatabase, provider, item)
    }

    fun findByTitle(
        provider: TvProviderConfig,
        kind: String,
        title: String,
    ): TvIndexedMedia? {
        val cursor = readableDatabase.query(
            "media",
            COLUMNS,
            "provider_key=? AND kind=? AND title_key=?",
            arrayOf(
                providerKey(provider),
                kind,
                titleKey(title),
            ),
            null,
            null,
            null,
            "1",
        )

        cursor.use {
            if (!it.moveToFirst()) return null
            return it.toIndexed()
        }
    }

    fun suggestions(
        provider: TvProviderConfig,
        kind: String? = null,
        limit: Int = 16,
    ): List<TvIndexedMedia> {
        val where =
            if (kind == null) {
                "provider_key=?"
            } else {
                "provider_key=? AND kind=?"
            }
        val args =
            if (kind == null) {
                arrayOf(providerKey(provider))
            } else {
                arrayOf(providerKey(provider), kind)
            }

        val cursor = readableDatabase.query(
            "media",
            COLUMNS,
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
                    add(it.toIndexed())
                }
            }
        }
    }

    fun categoryCount(
        provider: TvProviderConfig,
        kind: String,
        categoryId: String,
    ): Int {
        val cursor = readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM media
            WHERE provider_key=? AND kind=? AND category_id=?
            """.trimIndent(),
            arrayOf(providerKey(provider), kind, categoryId),
        )
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun categoryPage(
        provider: TvProviderConfig,
        kind: String,
        categoryId: String,
        limit: Int,
        offset: Int,
    ): List<TvIndexedMedia> {
        val cursor = readableDatabase.query(
            "media",
            COLUMNS,
            "provider_key=? AND kind=? AND category_id=?",
            arrayOf(providerKey(provider), kind, categoryId),
            null,
            null,
            "name COLLATE NOCASE ASC",
            "$offset,$limit",
        )

        return buildList(limit) {
            cursor.use {
                while (it.moveToNext()) {
                    add(it.toIndexed())
                }
            }
        }
    }

    private fun insert(
        db: SQLiteDatabase,
        provider: TvProviderConfig,
        item: TvIndexedMedia,
    ) {
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
        db.insertWithOnConflict(
            "media",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun android.database.Cursor.toIndexed(): TvIndexedMedia =
        TvIndexedMedia(
            kind = getString(0),
            localId = getString(1),
            name = getString(2),
            streamUrl = getString(3),
            artwork = getString(4),
            seriesId = getString(5),
            categoryId = getString(6),
        )

    private fun NativeVodItem.toIndexed(): TvIndexedMedia =
        TvIndexedMedia(
            kind = "movie",
            localId = id,
            name = name,
            streamUrl = streamUrl,
            artwork = poster,
            seriesId = null,
            categoryId = categoryId,
        )

    private fun NativeSeriesItem.toIndexed(): TvIndexedMedia =
        TvIndexedMedia(
            kind = "series",
            localId = id,
            name = name,
            streamUrl = "",
            artwork = cover,
            seriesId = seriesId,
            categoryId = categoryId,
        )

    companion object {
        private val COLUMNS = arrayOf(
            "kind",
            "local_id",
            "name",
            "stream_url",
            "artwork",
            "series_id",
            "category_id",
        )

        fun titleKey(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
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
