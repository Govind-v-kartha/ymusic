package io.ymusic.ui.playhistory

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayHistorySchemaMigrationTest {

    @Test
    fun fresh_install_creates_schema_version_8() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayHistorySchema.create(driver)

        // Verify repeat_events has canonical_key column
        driver.executeQuery(null, "SELECT item_key, played_at, weight, rest_until, canonical_key FROM repeat_events LIMIT 0", {
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)

        // Verify version
        assertEquals(8L, PlayHistorySchema.version)
        driver.close()
    }

    @Test
    fun migrate_from_v5_creates_repeat_events_with_all_columns() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Setup initial v5 schema (without repeat_events)
        driver.execute(null, "CREATE TABLE local_play_history (code TEXT PRIMARY KEY NOT NULL, watch_time INTEGER NOT NULL, tracked_at INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE yt_play_history (video_id TEXT PRIMARY KEY NOT NULL, watch_time INTEGER NOT NULL, tracked_at INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE yt_video_metadata (video_id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, artist TEXT, thumbnail_url TEXT, duration TEXT, duration_ms INTEGER, last_updated INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE play_again (queue_id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, thumbnail_url TEXT, queue_index INTEGER NOT NULL, queue_size INTEGER NOT NULL, position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, items TEXT NOT NULL, updated_at INTEGER NOT NULL, subtitle TEXT, strip_start INTEGER NOT NULL DEFAULT 0, strip_thumbs TEXT NOT NULL DEFAULT '', source_token TEXT)", 0)

        // Migrate from 5 to 8
        PlayHistorySchema.migrate(driver, oldVersion = 5, newVersion = 8)

        // Insert and read back with canonical_key
        driver.execute(null, "INSERT INTO repeat_events (item_key, played_at, weight, rest_until, canonical_key) VALUES ('k1', 1000, 1.0, 2000, 'c1')", 0)
        driver.executeQuery(null, "SELECT item_key, canonical_key FROM repeat_events WHERE item_key = 'k1'", { cursor ->
            assertTrue(cursor.next().value)
            assertEquals("k1", cursor.getString(0))
            assertEquals("c1", cursor.getString(1))
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)
        driver.close()
    }

    @Test
    fun migrate_from_v6_adds_rest_until_and_canonical_key() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Setup v6 table (repeat_events has item_key, played_at, weight only)
        driver.execute(null, "CREATE TABLE repeat_events (item_key TEXT NOT NULL, played_at INTEGER NOT NULL, weight REAL NOT NULL)", 0)

        // Migrate from 6 to 8
        PlayHistorySchema.migrate(driver, oldVersion = 6, newVersion = 8)

        // Both rest_until and canonical_key must exist
        driver.execute(null, "INSERT INTO repeat_events (item_key, played_at, weight, rest_until, canonical_key) VALUES ('k2', 1000, 1.0, 2000, 'c2')", 0)
        driver.executeQuery(null, "SELECT item_key, rest_until, canonical_key FROM repeat_events WHERE item_key = 'k2'", { cursor ->
            assertTrue(cursor.next().value)
            assertEquals("k2", cursor.getString(0))
            assertEquals(2000L, cursor.getLong(1))
            assertEquals("c2", cursor.getString(2))
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)
        driver.close()
    }

    @Test
    fun migrate_from_v7_adds_canonical_key_and_index() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Setup v7 table (repeat_events has item_key, played_at, weight, rest_until)
        driver.execute(null, "CREATE TABLE repeat_events (item_key TEXT NOT NULL, played_at INTEGER NOT NULL, weight REAL NOT NULL, rest_until INTEGER)", 0)

        // Migrate from 7 to 8
        PlayHistorySchema.migrate(driver, oldVersion = 7, newVersion = 8)

        // canonical_key must exist
        driver.execute(null, "INSERT INTO repeat_events (item_key, played_at, weight, rest_until, canonical_key) VALUES ('k3', 1000, 1.0, null, 'c3')", 0)
        driver.executeQuery(null, "SELECT item_key, canonical_key FROM repeat_events WHERE item_key = 'k3'", { cursor ->
            assertTrue(cursor.next().value)
            assertEquals("k3", cursor.getString(0))
            assertEquals("c3", cursor.getString(1))
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)
        driver.close()
    }

    @Test
    fun quick_skip_records_exactly_one_rest_event_in_history_store() = kotlinx.coroutines.test.runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayHistorySchema.create(driver)
        val historyStore = PlayHistoryStore(driver)

        val key = "yt:song_quick_skipped"
        val canonicalKey = "song_quick_skipped"
        val now = 1_000_000L
        val restUntil = now + 3600_000L

        // Simulate coordinator's persistence write for a quick-skipped track
        historyStore.insertRepeatRest(
            itemKey = key,
            canonicalKey = canonicalKey,
            playedAt = now,
            restUntil = restUntil,
        )

        // Query database directly to assert exactly 1 row
        var rowCount = 0
        var storedItemKey: String? = null
        var storedCanonicalKey: String? = null
        var storedRestUntil: Long? = null

        driver.executeQuery(
            null,
            "SELECT item_key, canonical_key, rest_until FROM repeat_events WHERE item_key = ?",
            { cursor ->
                while (cursor.next().value) {
                    rowCount++
                    storedItemKey = cursor.getString(0)
                    storedCanonicalKey = cursor.getString(1)
                    storedRestUntil = cursor.getLong(2)
                }
                app.cash.sqldelight.db.QueryResult.Unit
            },
            1,
        ) {
            bindString(0, key)
        }

        assertEquals(1, rowCount, "Exactly one rest row must be stored for a quick skip")
        assertEquals(key, storedItemKey)
        assertEquals(canonicalKey, storedCanonicalKey)
        assertEquals(restUntil, storedRestUntil)
        driver.close()
    }
}
