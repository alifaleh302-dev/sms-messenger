package com.sms.messages.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import org.robolectric.annotation.Config

/**
 * Exercises the real Room database and the real SQLite engine on the JVM, so the
 * conversation bookkeeping, the unread counters and the LIKE-based search are verified
 * against actual SQL rather than a stub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MessageStoreTest {

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

    @Test
    fun `inbound messages accumulate unread and update the snippet`() = runTest {
        repository.persistIncoming("+966512345678", "first", 1_000L, -1)
        repository.persistIncoming("+966512345678", "second", 2_000L, -1)

        val conversation = database.conversations().getByAddress("+966512345678")
        assertNotNull(conversation)
        assertEquals(2, conversation!!.unreadCount)
        assertEquals("second", conversation.snippet)
        assertEquals(2_000L, conversation.lastMessageAt)
    }

    @Test
    fun `the same number written differently lands in one conversation`() = runTest {
        repository.persistIncoming("+966 51 234 5678", "a", 1_000L, -1)
        repository.persistIncoming("00966512345678", "b", 2_000L, -1)

        val conversations = database.conversations().getAll()
        assertEquals(1, conversations.size)
        assertEquals(2, database.messages().countUnread(conversations.first().id))
    }

    @Test
    fun `a blocked number is dropped and never stored`() = runTest {
        repository.blockNumber("0512345678")
        val id = repository.persistIncoming("+966512345678", "spam", 1_000L, -1)

        assertEquals(-1L, id)
        assertNull(database.conversations().getByAddress("+966512345678"))
        assertEquals(0, database.messages().getUnfinished().size)
    }

    @Test
    fun `an external id is imported only once`() = runTest {
        val first = repository.persistIncoming("+966500000000", "x", 1_000L, -1, externalId = 42L)
        val second = repository.persistIncoming("+966500000000", "x", 1_000L, -1, externalId = 42L)

        assertTrue(first > 0)
        assertEquals(-1L, second)
    }

    @Test
    fun `marking a conversation read clears the badge`() = runTest {
        repository.persistIncoming("+966511111111", "one", 1_000L, -1)
        repository.persistIncoming("+966511111111", "two", 2_000L, -1)
        val id = database.conversations().getByAddress("+966511111111")!!.id

        repository.markConversationRead(id)

        assertEquals(0, database.conversations().getById(id)!!.unreadCount)
        assertEquals(0, database.messages().countUnread(id))
    }

    @Test
    fun `deleting the last message removes the conversation`() = runTest {
        val messageId = repository.persistIncoming("+966522222222", "only", 1_000L, -1)
        val conversationId = database.conversations().getByAddress("+966522222222")!!.id

        repository.deleteMessage(messageId)

        assertNull(database.conversations().getById(conversationId))
    }

    @Test
    fun `deleting an unread message also drops the unread badge`() = runTest {
        val first = repository.persistIncoming("+966533333333", "one", 1_000L, -1)
        repository.persistIncoming("+966533333333", "two", 2_000L, -1)
        val conversationId = database.conversations().getByAddress("+966533333333")!!.id
        assertEquals(2, database.conversations().getById(conversationId)!!.unreadCount)

        repository.deleteMessage(first)

        assertEquals(1, database.conversations().getById(conversationId)!!.unreadCount)
    }

    @Test
    fun `search finds messages and ignores LIKE wildcards typed by the user`() = runTest {
        repository.persistIncoming("+966544444444", "your code is 1234", 1_000L, -1)
        repository.persistIncoming("+966544444444", "discount 50% today", 2_000L, -1)

        assertEquals(1, repository.search("1234").first().size)
        // A literal percent must match the message containing one, not everything.
        assertEquals(1, repository.search("%").first().size)
        // An underscore must not act as a single-character wildcard.
        assertEquals(0, repository.search("5_%").first().size)
        assertEquals(1, repository.search("50%").first().size)
    }

    @Test
    fun `search matches on the contact name`() = runTest {
        val id = repository.persistIncoming("+966555555555", "hello there", 1_000L, -1)
        val conversationId = database.conversations().getByAddress("+966555555555")!!.id
        database.conversations().setDisplayName(conversationId, "Ahmad")

        val hits = repository.search("Ahmad").first()
        assertEquals(1, hits.size)
        assertEquals(id, hits.first().messageId)
    }

    @Test
    fun `an outgoing message is queued as pending and touches the conversation`() = runTest {
        val id = repository.sendMessage(
            address = "+966566666666",
            body = "hi",
            subscriptionId = -1,
            deliveryReport = false,
        )

        val message = database.messages().getById(id)
        assertNotNull(message)
        assertEquals(MessageType.OUTBOX, message!!.type)
        assertTrue(message.read)
        // No telephony in the test environment, so the send must fail rather than crash.
        assertEquals(MessageStatus.FAILED, database.messages().getById(id)!!.status)
        assertEquals("hi", database.conversations().getByAddress("+966566666666")!!.snippet)
    }

    @Test
    fun `an invalid recipient is rejected instead of stored`() = runTest {
        var thrown = false
        try {
            repository.sendMessage("not-a-number", "hi", -1, false)
        } catch (expected: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("an invalid recipient must be rejected", thrown)
    }

    @Test
    fun `scheduled messages are listed and can be cancelled`() = runTest {
        val id = repository.scheduleMessage("+966577777777", "later", 9_999_999_999L, -1)
        assertEquals(1, repository.observeScheduled().first().size)

        repository.cancelScheduled(id)
        assertEquals(0, repository.observeScheduled().first().size)
    }

    @Test
    fun `templates are saved updated and counted`() = runTest {
        val id = repository.saveTemplate(null, "Greeting", "Hello")
        val stored = database.templates().getById(id)
        assertNotNull(stored)

        repository.saveTemplate(id, "Greeting", "Hi there")
        assertEquals("Hi there", database.templates().getById(id)!!.body)

        repository.markTemplateUsed(id)
        assertEquals(1, database.templates().getById(id)!!.useCount)
    }

    @Test
    fun `conversation flags round trip`() = runTest {
        repository.persistIncoming("+966588888888", "hi", 1_000L, -1)
        val id = database.conversations().getByAddress("+966588888888")!!.id

        repository.setArchived(id, true)
        repository.setPinned(id, true)
        repository.setMuted(id, true)
        repository.saveDraft(id, "draft text")

        val updated = database.conversations().getById(id)!!
        assertTrue(updated.archived)
        assertTrue(updated.pinned)
        assertTrue(updated.muted)
        assertEquals("draft text", updated.draft)
        assertTrue(repository.isConversationMuted(id))

        // Archived conversations leave the active list but stay reachable.
        assertFalse(repository.observeConversations().first().any { it.id == id })
        assertEquals(1, repository.observeArchived().first().size)
    }

    @Test
    fun `blocking is idempotent and unblocking restores delivery`() = runTest {
        repository.blockNumber("+966599999999")
        repository.blockNumber("+966599999999")
        assertEquals(1, repository.observeBlocked().first().size)
        assertTrue(repository.isBlocked("+966599999999"))

        repository.unblockNumber("+966599999999")
        assertFalse(repository.isBlocked("+966599999999"))
        assertTrue(repository.persistIncoming("+966599999999", "back", 1_000L, -1) > 0)
    }

    @Test
    fun `pending messages survive a restart and are re-queued`() = runTest {
        // A message left pending, as if the process died mid-send.
        val id = repository.sendMessage("+966500000001", "stuck", -1, false)
        database.messages().updateStatus(id, MessageStatus.PENDING, null)

        repository.retryUnfinishedMessages(autoRetry = false)

        // Still no telephony, so it must end up failed rather than staying pending forever.
        assertEquals(MessageStatus.FAILED, database.messages().getById(id)!!.status)
    }

    @Test
    fun `retry only touches failed messages inside the retry window`() = runTest {
        val old = repository.sendMessage("+966500000002", "old", -1, false)
        database.messages().updateStatus(old, MessageStatus.FAILED, null)
        // Age it beyond the retry window.
        val stale = database.messages().getById(old)!!.copy(timestamp = 0L)
        database.messages().update(stale)

        val recent = repository.sendMessage("+966500000003", "recent", -1, false)

        val retried = repository.retryUnfinishedMessages(autoRetry = true)
        assertEquals(0, retried)
        assertEquals(MessageStatus.FAILED, database.messages().getById(old)!!.status)
        assertEquals(MessageStatus.FAILED, database.messages().getById(recent)!!.status)
    }

    @Test
    fun `deleting a conversation removes its messages`() = runTest {
        repository.persistIncoming("+966500000004", "a", 1_000L, -1)
        repository.persistIncoming("+966500000004", "b", 2_000L, -1)
        val id = database.conversations().getByAddress("+966500000004")!!.id

        repository.deleteConversation(id)

        assertNull(database.conversations().getById(id))
        assertEquals(0, database.messages().observeForConversation(id).first().size)
    }

    @Test
    fun `total unread counts both active and archived conversations`() = runTest {
        repository.persistIncoming("+966500000005", "a", 1_000L, -1)
        repository.persistIncoming("+966500000006", "b", 2_000L, -1)
        val archived = database.conversations().getByAddress("+966500000006")!!.id
        repository.setArchived(archived, true)

        assertEquals(2, repository.totalUnreadCount().first())
    }
}
