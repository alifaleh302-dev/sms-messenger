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

/**
 * Adds the custom tab tables and the one-time-code flag.
 *
 * The tables are new, so the only way this can lose anything is if it runs against a file
 * that already has them; the `IF NOT EXISTS` clauses make a repeat run a no-op. Nothing
 * existing is altered, so every conversation, message and reaction survives untouched.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN otp INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_otp ON messages (otp)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                iconIndex INTEGER NOT NULL DEFAULT 0,
                colorIndex INTEGER NOT NULL DEFAULT 0,
                position INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories (name)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS category_members (
                categoryId INTEGER NOT NULL,
                conversationId INTEGER NOT NULL,
                PRIMARY KEY(categoryId, conversationId),
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE,
                FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_category_members_categoryId " +
                "ON category_members (categoryId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_category_members_conversationId " +
                "ON category_members (conversationId)"
        )
    }
}
