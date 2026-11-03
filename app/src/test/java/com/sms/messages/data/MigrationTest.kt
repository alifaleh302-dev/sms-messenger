package com.sms.messages.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the 1 -> 2 upgrade keeps stored messages.
 *
 * The database previously relied on `fallbackToDestructiveMigration`, which would silently
 * drop every conversation on upgrade. This opens a genuine version-1 file, runs the real
 * migration through Room, then lets Room validate the result by opening the database the
 * normal way, so the migration and the entity definitions have to agree.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createV1(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `migration keeps conversations and messages`() = runBlocking {
        seedV1(helper.writableDatabase)
        helper.close()

        val db = openMigrated()
        try {
            val conversations = db.conversations().getAll()
            assertEquals("conversation survived", 1, conversations.size)
            assertEquals("+966500000001", conversations.first().address)
            assertEquals("unread count preserved", 2, conversations.first().unreadCount)
            assertEquals("not in trash", 0L, conversations.first().trashedAt)

            val message = db.messages().getById(1)
            assertNotNull("message survived", message)
            assertEquals("hello there", message!!.body)
            assertFalse("starred defaults to false", message.starred)
            assertNull("replyToId defaults to null", message.replyToId)
        } finally {
            db.close()
        }
    }

    @Test
    fun `reactions work after migration`() = runBlocking {
        seedV1(helper.writableDatabase)
        helper.close()

        val db = openMigrated()
        try {
            db.reactions().upsert(ReactionEntity(messageId = 1, emoji = "👍", count = 1, mine = true))
            val stored = db.reactions().getForMessage(1)
            assertEquals(1, stored.size)
            assertEquals("👍", stored.first().emoji)
            assertTrue(stored.first().mine)
        } finally {
            db.close()
        }
    }

    /** The database as it existed at version 1, taken from the exported schema. */
    private fun createV1(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`address` TEXT NOT NULL, `displayName` TEXT, `snippet` TEXT NOT NULL, " +
                "`lastMessageAt` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `muted` INTEGER NOT NULL, " +
                "`draft` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_address` ON `conversations` (`address`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`conversationId` INTEGER NOT NULL, `address` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `type` INTEGER NOT NULL, `status` INTEGER NOT NULL, " +
                "`read` INTEGER NOT NULL, `subscriptionId` INTEGER NOT NULL, `errorCode` INTEGER, " +
                "`attachmentUri` TEXT, `attachmentMime` TEXT, `attachmentName` TEXT, `externalId` INTEGER, " +
                "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_address` ON `messages` (`address`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `useCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scheduled_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`address` TEXT NOT NULL, `displayName` TEXT, `body` TEXT NOT NULL, `scheduledAt` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `subscriptionId` INTEGER NOT NULL, `completed` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_messages_scheduledAt` ON `scheduled_messages` (`scheduledAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `blocked_numbers` (`address` TEXT NOT NULL, `displayName` TEXT, " +
                "`blockedAt` INTEGER NOT NULL, PRIMARY KEY(`address`))"
        )
    }

    private fun seedV1(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO conversations (id, address, displayName, snippet, lastMessageAt, " +
                "unreadCount, archived, pinned, muted, draft, colorIndex) " +
                "VALUES (1, '+966500000001', 'Test Contact', 'hello there', 1000, 2, 0, 0, 0, '', 3)"
        )
        db.execSQL(
            "INSERT INTO messages (id, conversationId, address, body, timestamp, type, status, " +
                "read, subscriptionId, errorCode, externalId) " +
                "VALUES (1, 1, '+966500000001', 'hello there', 1000, 0, 0, 0, -1, NULL, NULL)"
        )
    }

    /**
     * Opens the seeded version-1 file the way the app does. Room detects the version gap and
     * runs [MIGRATION_1_2] itself, then validates the result against the entities.
     */
    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
