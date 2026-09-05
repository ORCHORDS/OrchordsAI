package com.orchords.orchordsai.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the `payload_blob_id` column to `message_node` and an index for lookups
 * issued by [com.orchords.orchordsai.data.db.MessageNodePayloadStore].
 *
 * Existing rows are NOT eagerly backfilled: pre-existing inline JSON stays inline
 * (possibly large) and is externalized lazily on the next write through the
 * `MessageNodePayloadStore` (see issue #345). This keeps the migration safe on
 * devices with limited RAM by avoiding a one-shot read+write of every large row.
 *
 * Implemented as a manual `Migration` (rather than an `AutoMigrationSpec`) so we can
 * add the index on the new column without a table rebuild: an auto-migration cannot
 * issue `CREATE INDEX` against a column added by the same migration.
 */
val Migration_24_25: Migration = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE message_node ADD COLUMN payload_blob_id INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_node_payload_blob_id " +
                "ON message_node(payload_blob_id)"
        )
    }
}
