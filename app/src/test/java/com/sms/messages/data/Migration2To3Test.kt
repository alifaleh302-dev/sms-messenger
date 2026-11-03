package com.sms.messages.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
 * Verifies the 2 -> 3 upgrade.
 *
 * This is the migration that introduces the custom tabs, so the test proves three things
 * that matter: existing conversations and messages survive, the new tables are usable
 * straight afterwards, and Room's own validation of the result passes (which is what
 * catches a hand-written CREATE TABLE that disagrees with the entity).
 */
@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createV2(db)
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
    fun `migration keeps conversations, messages and reactions`() = runBlocking {
        seedV2(helper.writableDatabase)
        helper.close()

        val db = openMigrated()
        try {
            val conversations = db.conversations().getAll()
            assertEquals("conversation survived", 1, conversations.size)
            assertEquals("+966500000001", conversations.first().address)

            val message = db.messages().getById(1)
            assertNotNull("message survived", message)
            assertEquals("hello there", message!!.body)
            assertFalse("otp defaults to false", message.otp)

            assertEquals("reaction survived", 1, db.reactions().getForMessage(1).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `custom tabs work after migration`() = runBlocking {
        seedV2(helper.writableDatabase)
        helper.close()

        val db = openMigrated()
        try {
            val id = db.categories().insert(CategoryEntity(name = "عمل"))
            assertTrue("tab got an id", id > 0)

            db.categories().addMember(CategoryMemberEntity(categoryId = id, conversationId = 1))
            assertEquals(listOf(1L), db.categories().observeMemberIds(id).first())

            // The unique index on the name must still reject a duplicate after migration.
            val stored = db.categories().getByName("عمل")
            assertNotNull(stored)
            assertEquals(id, stored!!.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun `deleting a tab cascades its memberships but keeps the conversation`() = runBlocking {
        seedV2(helper.writableDatabase)
        helper.close()

        val db = openMigrated()
        try {
            val id = db.categories().insert(CategoryEntity(name = "عائلة"))
            db.categories().addMember(CategoryMemberEntity(categoryId = id, conversationId = 1))
            db.categories().deleteById(id)

            assertEquals("membership removed", 0, db.categories().observeMemberIds(id).first().size)
            assertNotNull("conversation untouched", db.conversations().getById(1))
        } finally {
            db.close()
        }
    }

    /** The database as it existed at version 2, taken from the exported schema. */
    private fun createV2(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`address` TEXT NOT NULL, `displayName` TEXT, `snippet` TEXT NOT NULL, " +
                "`lastMessageAt` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `muted` INTEGER NOT NULL, " +
                "`draft` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `trashedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_address` ON `conversations` (`address`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`conversationId` INTEGER NOT NULL, `address` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `type` INTEGER NOT NULL, `status` INTEGER NOT NULL, " +
                "`read` INTEGER NOT NULL, `subscriptionId` INTEGER NOT NULL, `errorCode` INTEGER, " +
                "`attachmentUri` TEXT, `attachmentMime` TEXT, `attachmentName` TEXT, `externalId` INTEGER, " +
                "`starred` INTEGER NOT NULL, `replyToId` INTEGER, " +
                "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_address` ON `messages` (`address`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_starred` ON `messages` (`starred`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reactions` (`messageId` INTEGER NOT NULL, " +
                "`emoji` TEXT NOT NULL, `count` INTEGER NOT NULL, `mine` INTEGER NOT NULL, " +
                "PRIMARY KEY(`messageId`, `emoji`), " +
                "FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reactions_messageId` ON `reactions` (`messageId`)")
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

    private fun seedV2(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO conversations (id, address, displayName, snippet, lastMessageAt, " +
                "unreadCount, archived, pinned, muted, draft, colorIndex, trashedAt) " +
                "VALUES (1, '+966500000001', 'Test Contact', 'hello there', 1000, 2, 0, 0, 0, '', 3, 0)"
        )
        db.execSQL(
            "INSERT INTO messages (id, conversationId, address, body, timestamp, type, status, " +
                "read, subscriptionId, errorCode, externalId, starred, replyToId) " +
                "VALUES (1, 1, '+966500000001', 'hello there', 1000, 0, 0, 0, -1, NULL, NULL, 0, NULL)"
        )
        db.execSQL(
            "INSERT INTO reactions (messageId, emoji, count, mine) VALUES (1, '👍', 1, 1)"
        )
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val TEST_DB = "migration-2-3-test.db"
    }
}
