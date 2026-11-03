package com.sms.messages.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds starred messages, quoted replies, reactions, and the trash column.
 *
 * `fallbackToDestructiveMigration` used to be the safety net here, which would have wiped
 * every stored conversation on upgrade. This migration keeps them, so it must stay in
 * place as long as older installs exist.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN replyToId INTEGER")
        db.execSQL("ALTER TABLE conversations ADD COLUMN trashedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_starred ON messages (starred)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reactions (
                messageId INTEGER NOT NULL,
                emoji TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT 1,
                mine INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(messageId, emoji),
                FOREIGN KEY(messageId) REFERENCES messages(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reactions_messageId ON reactions (messageId)")
    }
}
