package com.sms.messages.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the Google Messages style interactions added on top of the message store: starring,
 * emoji reactions, multi-select deletion, the trash window, and mark-as-unread. Everything
 * runs against real SQLite so the new SQL is exercised, not simulated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MessageActionsTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: SmsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SmsRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------------ starring

    @Test
    fun `starring a message surfaces it in the starred list`() = runTest {
        val id = repository.persistIncoming("+966500000010", "keep this", 1_000L, -1)

        repository.setStarred(id, true)

        val starred = repository.observeStarred().first()
        assertEquals(1, starred.size)
        assertEquals(id, starred.first().id)
        assertTrue(starred.first().starred)
        assertEquals(1, repository.observeStarredCount().first())
    }

    @Test
    fun `unstarring removes the message from the starred list`() = runTest {
        val id = repository.persistIncoming("+966500000011", "temporary", 1_000L, -1)
        repository.setStarred(id, true)

        repository.setStarred(id, false)

        assertTrue(repository.observeStarred().first().isEmpty())
    }

    // ------------------------------------------------------------------ reactions

    @Test
    fun `reacting twice with the same emoji removes the reaction`() = runTest {
        val id = repository.persistIncoming("+966500000012", "react to me", 1_000L, -1)

        repository.toggleReaction(id, "👍")
        assertEquals(1, repository.reactionsFor(id).size)

        repository.toggleReaction(id, "👍")
        assertTrue(repository.reactionsFor(id).isEmpty())
    }

    @Test
    fun `a second reaction replaces the first because only one is allowed`() = runTest {
        val id = repository.persistIncoming("+966500000013", "one at a time", 1_000L, -1)

        repository.toggleReaction(id, "👍")
        repository.toggleReaction(id, "❤️")

        val reactions = repository.reactionsFor(id)
        assertEquals("only one reaction of ours survives", 1, reactions.size)
        assertEquals("❤️", reactions.first().emoji)
        assertTrue(reactions.first().mine)
    }

    @Test
    fun `incoming reactions from others accumulate without claiming ownership`() = runTest {
        val id = repository.persistIncoming("+966500000014", "group reaction", 1_000L, -1)

        repository.applyIncomingReaction(id, "🎉")
        repository.applyIncomingReaction(id, "🎉")

        val reaction = repository.reactionsFor(id).single()
        assertEquals(2, reaction.count)
        assertFalse("a reaction from the network is not ours", reaction.mine)
    }

    // ------------------------------------------------------------------ deletion

    @Test
    fun `deleting several messages at once drops them and keeps the summary correct`() = runTest {
        val first = repository.persistIncoming("+966500000015", "one", 1_000L, -1)
        val second = repository.persistIncoming("+966500000015", "two", 2_000L, -1)
        val third = repository.persistIncoming("+966500000015", "three", 3_000L, -1)

        repository.deleteMessages(listOf(first, second))

        val remaining = database.messages().observeForConversation(
            database.conversations().getByAddress("+966500000015")!!.id
        ).first()
        assertEquals(1, remaining.size)
        assertEquals(third, remaining.first().id)
        assertEquals("three", remaining.first().body)
    }

    @Test
    fun `deleting the newest message rewinds the snippet to the previous one`() = runTest {
        repository.persistIncoming("+966500000016", "older", 1_000L, -1)
        val newest = repository.persistIncoming("+966500000016", "newest", 2_000L, -1)

        repository.deleteMessage(newest)

        val conversation = database.conversations().getByAddress("+966500000016")!!
        assertEquals("older", conversation.snippet)
        assertEquals(1_000L, conversation.lastMessageAt)
    }

    // ------------------------------------------------------------------ unread

    @Test
    fun `marking unread brings the badge back`() = runTest {
        val id = repository.persistIncoming("+966500000017", "hello", 1_000L, -1)
        val conversationId = database.conversations().getByAddress("+966500000017")!!.id
        repository.markConversationRead(conversationId)
        assertEquals(0, database.conversations().getById(conversationId)!!.unreadCount)

        repository.markConversationUnread(conversationId)

        assertTrue(database.conversations().getById(conversationId)!!.unreadCount > 0)
        assertFalse(database.messages().getById(id)!!.read)
    }

    // ------------------------------------------------------------------ trash

    @Test
    fun `trashing hides a conversation from the inbox but keeps it recoverable`() = runTest {
        repository.persistIncoming("+966500000018", "bye", 1_000L, -1)
        val conversationId = database.conversations().getByAddress("+966500000018")!!.id

        repository.trashConversation(conversationId)

        assertTrue("gone from the inbox", repository.observeConversations().first().isEmpty())
        assertEquals("still in trash", 1, repository.observeTrashed().first().size)
        assertEquals(1, repository.observeTrashCount().first())
    }

    @Test
    fun `restoring a trashed conversation brings it back to the inbox`() = runTest {
        repository.persistIncoming("+966500000019", "come back", 1_000L, -1)
        val conversationId = database.conversations().getByAddress("+966500000019")!!.id
        repository.trashConversation(conversationId)

        repository.restoreConversation(conversationId)

        assertEquals(1, repository.observeConversations().first().size)
        assertTrue(repository.observeTrashed().first().isEmpty())
    }

    @Test
    fun `messages survive while their conversation sits in trash`() = runTest {
        val id = repository.persistIncoming("+966500000020", "important", 1_000L, -1)
        val conversationId = database.conversations().getByAddress("+966500000020")!!.id

        repository.trashConversation(conversationId)

        assertEquals("important", database.messages().getById(id)!!.body)
    }

    @Test
    fun `purging a conversation deletes its messages for good`() = runTest {
        val id = repository.persistIncoming("+966500000021", "gone", 1_000L, -1)
        val conversationId = database.conversations().getByAddress("+966500000021")!!.id
        repository.trashConversation(conversationId)

        repository.purgeConversation(conversationId)

        assertNull(database.messages().getById(id))
        assertTrue(repository.observeTrashed().first().isEmpty())
    }

    @Test
    fun `only conversations past the retention window are purged`() = runTest {
        repository.persistIncoming("+966500000022", "old", 1_000L, -1)
        repository.persistIncoming("+966500000023", "new", 1_000L, -1)
        val old = database.conversations().getByAddress("+966500000022")!!.id
        val recent = database.conversations().getByAddress("+966500000023")!!.id

        // Backdate one conversation beyond the 30-day window.
        database.conversations().setTrashedAt(old, 0L)
        database.conversations().setTrashedAt(
            old,
            System.currentTimeMillis() - SmsRepository.TRASH_RETENTION_MILLIS - 1_000L,
        )
        database.conversations().setTrashedAt(recent, System.currentTimeMillis())

        val purged = repository.purgeExpiredTrash()

        assertEquals(1, purged)
        assertEquals("the recent one stays", listOf(recent), repository.observeTrashed().first().map { it.id })
    }

    // ------------------------------------------------------------------ replies

    @Test
    fun `a reply keeps its quote link after the original is deleted`() = runTest {
        val original = repository.persistIncoming("+966500000024", "question?", 1_000L, -1)
        val conversationId = database.conversations().getByAddress("+966500000024")!!.id
        val replyId = database.messages().insert(
            MessageEntity(
                conversationId = conversationId,
                address = "+966500000024",
                body = "answer",
                timestamp = 2_000L,
                type = MessageType.OUTBOX,
                replyToId = original,
            )
        )

        repository.deleteMessage(original)

        // The quote target is gone, so the strip simply stops rendering; the reply survives.
        assertNull(repository.quotedMessage(original))
        assertEquals("answer", database.messages().getById(replyId)!!.body)
    }
}
