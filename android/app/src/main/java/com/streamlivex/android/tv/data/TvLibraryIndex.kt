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
    4,
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
        db.execSQL(
            """
            CREATE TABLE categories (
                provider_key TEXT NOT NULL,
                kind TEXT NOT NULL,
                category_id TEXT NOT NULL,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(provider_key, kind, category_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE new_items (
                provider_key TEXT NOT NULL,
                kind TEXT NOT NULL,
                local_id TEXT NOT NULL,
                detected_at INTEGER NOT NULL,
                PRIMARY KEY(provider_key, kind, local_id)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS new_items (
                    provider_key TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    local_id TEXT NOT NULL,
                    detected_at INTEGER NOT NULL,
                    PRIMARY KEY(provider_key, kind, local_id)
                )
                """.trimIndent(),
            )
        }
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

    fun markNotReady(
        provider: TvProviderConfig,
    ) {
        val key =
            providerKey(provider)
        val db =
            writableDatabase

        val values =
            ContentValues().apply {
                put(
                    "ready",
                    0,
                )
            }

        db.update(
            "library_meta",
            values,
            "provider_key=?",
            arrayOf(key),
        )
    }

    fun clearProvider(provider: TvProviderConfig) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val key = providerKey(provider)
            db.delete("media", "provider_key=?", arrayOf(key))
            db.delete("library_meta", "provider_key=?", arrayOf(key))
            db.delete("categories", "provider_key=?", arrayOf(key))
            db.delete("new_items", "provider_key=?", arrayOf(key))
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
            db.execSQL("DROP TABLE IF EXISTS temp_previous_media")
            db.execSQL(
                """
                CREATE TEMP TABLE temp_previous_media AS
                SELECT kind, local_id
                FROM media
                WHERE provider_key=?
                """.trimIndent(),
                arrayOf(key),
            )

            val previousCountCursor =
                db.rawQuery(
                    "SELECT COUNT(*) FROM temp_previous_media",
                    emptyArray(),
                )
            val previousCount =
                previousCountCursor.use {
                    if (it.moveToFirst()) {
                        it.getInt(0)
                    } else {
                        0
                    }
                }

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

            db.delete(
                "new_items",
                "provider_key=?",
                arrayOf(key),
            )

            if (previousCount > 0) {
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO new_items(provider_key, kind, local_id, detected_at)
                    SELECT m.provider_key, m.kind, m.local_id, ?
                    FROM media m
                    LEFT JOIN temp_previous_media p
                      ON p.kind=m.kind AND p.local_id=m.local_id
                    WHERE m.provider_key=? AND p.local_id IS NULL
                    """.trimIndent(),
                    arrayOf(
                        System.currentTimeMillis(),
                        key,
                    ),
                )
            }

            db.execSQL(
                "DROP TABLE IF EXISTS temp_previous_media",
            )

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

    fun updatedAt(
        provider: TvProviderConfig,
    ): Long {
        val cursor =
            readableDatabase.rawQuery(
                "SELECT updated_at FROM library_meta WHERE provider_key=? LIMIT 1",
                arrayOf(providerKey(provider)),
            )
        cursor.use {
            return if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                0L
            }
        }
    }

    fun needsRefresh(
        provider: TvProviderConfig,
        ttlHours: Int = 24,
    ): Boolean {
        if (!isReady(provider)) {
            return true
        }

        val updated =
            updatedAt(provider)
        if (updated <= 0L) {
            return true
        }

        val ttlMs =
            ttlHours
                .coerceAtLeast(1)
                .toLong() *
                60L *
                60L *
                1000L

        return System.currentTimeMillis() -
            updated >= ttlMs
    }

    fun newItems(
        provider: TvProviderConfig,
        kind: String? = null,
        limit: Int = 24,
    ): List<TvIndexedMedia> {
        val key =
            providerKey(provider)
        val sql =
            if (kind.isNullOrBlank()) {
                """
                SELECT m.kind,m.local_id,m.name,m.stream_url,m.artwork,m.series_id,m.category_id
                FROM new_items n
                JOIN media m
                  ON m.provider_key=n.provider_key
                 AND m.kind=n.kind
                 AND m.local_id=n.local_id
                WHERE n.provider_key=?
                ORDER BY n.detected_at DESC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT m.kind,m.local_id,m.name,m.stream_url,m.artwork,m.series_id,m.category_id
                FROM new_items n
                JOIN media m
                  ON m.provider_key=n.provider_key
                 AND m.kind=n.kind
                 AND m.local_id=n.local_id
                WHERE n.provider_key=? AND n.kind=?
                ORDER BY n.detected_at DESC
                LIMIT ?
                """.trimIndent()
            }

        val args =
            if (kind.isNullOrBlank()) {
                arrayOf(
                    key,
                    limit.toString(),
                )
            } else {
                arrayOf(
                    key,
                    kind,
                    limit.toString(),
                )
            }

        val cursor =
            readableDatabase.rawQuery(
                sql,
                args,
            )

        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    add(
                        TvIndexedMedia(
                            kind =
                                it.getString(0),
                            localId =
                                it.getString(1),
                            name =
                                it.getString(2),
                            streamUrl =
                                it.getString(3),
                            artwork =
                                it.getString(4),
                            seriesId =
                                it.getString(5),
                            categoryId =
                                it.getString(6),
                        ),
                    )
                }
            }
        }
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

    fun saveVodCategories(
        provider: TvProviderConfig,
        rows: List<NativeVodCategory>,
    ) {
        saveCategories(
            provider = provider,
            kind = "movie",
            rows = rows.map { it.id to it.name },
        )
    }

    fun saveSeriesCategories(
        provider: TvProviderConfig,
        rows: List<NativeSeriesCategory>,
    ) {
        saveCategories(
            provider = provider,
            kind = "series",
            rows = rows.map { it.id to it.name },
        )
    }

    fun loadVodCategories(
        provider: TvProviderConfig,
    ): List<NativeVodCategory> =
        loadCategories(provider, "movie")
            .map { NativeVodCategory(it.first, it.second) }

    fun loadSeriesCategories(
        provider: TvProviderConfig,
    ): List<NativeSeriesCategory> =
        loadCategories(provider, "series")
            .map { NativeSeriesCategory(it.first, it.second) }

    private fun saveCategories(
        provider: TvProviderConfig,
        kind: String,
        rows: List<Pair<String, String>>,
    ) {
        val db = writableDatabase
        val key = providerKey(provider)
        db.beginTransaction()
        try {
            db.delete(
                "categories",
                "provider_key=? AND kind=?",
                arrayOf(key, kind),
            )
            rows.forEachIndexed { index, row ->
                val values = ContentValues().apply {
                    put("provider_key", key)
                    put("kind", kind)
                    put("category_id", row.first)
                    put("name", row.second)
                    put("sort_order", index)
                }
                db.insertWithOnConflict(
                    "categories",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun loadCategories(
        provider: TvProviderConfig,
        kind: String,
    ): List<Pair<String, String>> {
        val cursor = readableDatabase.query(
            "categories",
            arrayOf("category_id", "name"),
            "provider_key=? AND kind=?",
            arrayOf(providerKey(provider), kind),
            null,
            null,
            "sort_order ASC",
        )
        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    add(it.getString(0) to it.getString(1))
                }
            }
        }
    }

    fun search(
        provider: TvProviderConfig,
        query: String,
        kind: String? = null,
        limit: Int = 60,
    ): List<TvIndexedMedia> {
        val key = titleKey(query)
        if (key.length < 2) return emptyList()

        val sql =
            if (kind.isNullOrBlank()) {
                """
                SELECT kind,local_id,name,stream_url,artwork,series_id,category_id
                FROM media
                WHERE provider_key=? AND title_key LIKE ?
                ORDER BY
                  CASE
                    WHEN title_key=? THEN 0
                    WHEN title_key LIKE ? THEN 1
                    ELSE 2
                  END,
                  name COLLATE NOCASE ASC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT kind,local_id,name,stream_url,artwork,series_id,category_id
                FROM media
                WHERE provider_key=? AND kind=? AND title_key LIKE ?
                ORDER BY
                  CASE
                    WHEN title_key=? THEN 0
                    WHEN title_key LIKE ? THEN 1
                    ELSE 2
                  END,
                  name COLLATE NOCASE ASC
                LIMIT ?
                """.trimIndent()
            }

        val args =
            if (kind.isNullOrBlank()) {
                arrayOf(providerKey(provider), "%$key%", key, "$key%", limit.toString())
            } else {
                arrayOf(providerKey(provider), kind, "%$key%", key, "$key%", limit.toString())
            }

        val cursor = readableDatabase.rawQuery(sql, args)
        return buildList {
            cursor.use {
                while (it.moveToNext()) add(it.toIndexed())
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
