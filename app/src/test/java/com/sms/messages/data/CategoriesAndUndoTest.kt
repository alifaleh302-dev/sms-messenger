package com.sms.messages.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
 * Covers the three features that touch stored data: custom tabs, the one-time-code sweep
 * and the undo-send hold. Everything runs against a real in-memory Room database and the
 * real SQLite engine, so the foreign keys and the cascade rules are actually exercised.
 *
 * The undo-send tests drive the repository on its own test scheduler. That scheduler is
 * deliberately separate from the one runTest uses: Room runs its queries on real threads,
 * and while the test body waits on those, runTest advances its own virtual clock, which
 * would silently expire a hold the test is still asserting on. With a private scheduler the
 * hold only moves when the test says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoriesAndUndoTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: SmsRepository

    /** Drives the repository's undo-send hold, independently of the test's own clock. */
    private val holdScheduler = TestCoroutineScheduler()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SmsRepository(
            context,
            database,
            scope = CoroutineScope(StandardTestDispatcher(holdScheduler)),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Lets the hold run out and the queued send execute. */
    private fun elapseHold(millis: Long = 4_100L) {
        holdScheduler.advanceTimeBy(millis)
        holdScheduler.runCurrent()
    }

    // ------------------------------------------------------------ custom tabs

    @Test
    fun `a tab is created and listed`() = runTest {
        val id = repository.createCategory("عمل")
        assertNotNull(id)
        val tabs = repository.observeCategories().first()
        assertEquals(1, tabs.size)
        assertEquals("عمل", tabs.first().name)
    }

    @Test
    fun `a duplicate tab name is refused`() = runTest {
        assertNotNull(repository.createCategory("عائلة"))
        assertNull("second tab with the same name must be refused", repository.createCategory("عائلة"))
        assertEquals(1, repository.observeCategories().first().size)
    }

    @Test
    fun `a blank tab name is refused`() = runTest {
        assertNull(repository.createCategory("   "))
    }

    @Test
    fun `renaming to a name another tab holds is refused`() = runTest {
        val first = repository.createCategory("عمل")!!
        repository.createCategory("دراسة")

        assertFalse(repository.updateCategory(first, "دراسة", 0, 0))
        assertEquals("عمل", database.categories().getById(first)!!.name)

        assertTrue(repository.updateCategory(first, "دراسة ثانية", 1, 2))
        assertEquals("دراسة ثانية", database.categories().getById(first)!!.name)
    }

    @Test
    fun `conversations are added to and removed from a tab`() = runTest {
        repository.persistIncoming("+966500000001", "hi", 1_000L, -1)
        val conversation = database.conversations().getByAddress("+966500000001")!!
        val tab = repository.createCategory("عمل")!!

        repository.addToCategory(tab, conversation.id)
        assertEquals(listOf(conversation.id), repository.observeCategoryMemberIds(tab).first())

        repository.removeFromCategory(tab, conversation.id)
        assertTrue(repository.observeCategoryMemberIds(tab).first().isEmpty())
    }

    @Test
    fun `deleting a tab removes its memberships but keeps the conversation`() = runTest {
        repository.persistIncoming("+966500000001", "hi", 1_000L, -1)
        val conversation = database.conversations().getByAddress("+966500000001")!!
        val tab = repository.createCategory("مؤقت")!!
        repository.addToCategory(tab, conversation.id)

        repository.deleteCategory(tab)

        assertTrue(repository.observeCategories().first().isEmpty())
        assertTrue(repository.observeCategoryMembers().first().isEmpty())
        assertNotNull("conversation must survive", database.conversations().getById(conversation.id))
    }

    @Test
    fun `classifying a conversation reads its stored messages`() = runTest {
        repository.persistIncoming("8888", "Your verification code is 445566", 1_000L, -1)
        val conversation = database.conversations().getByAddress("8888")!!

        assertEquals(com.sms.messages.util.SmartTab.TRANSACTIONS, repository.smartTabFor(conversation))
    }

    // ------------------------------------------------------------ one-time codes

    @Test
    fun `an incoming code is flagged on arrival`() = runTest {
        repository.persistIncoming("8888", "Your OTP is 123456", 1_000L, -1)
        val conversation = database.conversations().getByAddress("8888")!!
        val message = database.messages().observeForConversation(conversation.id).first().first()
        assertTrue("otp flag must be set", message.otp)
    }

    @Test
    fun `an ordinary message is not flagged`() = runTest {
        repository.persistIncoming("+966500000001", "see you at 5", 1_000L, -1)
        val conversation = database.conversations().getByAddress("+966500000001")!!
        val message = database.messages().observeForConversation(conversation.id).first().first()
        assertFalse(message.otp)
    }

    @Test
    fun `purging removes only codes older than the window`() = runTest {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        repository.persistIncoming("8888", "Your OTP is 111111", now - day - 60_000L, -1)
        repository.persistIncoming("8888", "Your OTP is 222222", now - 60_000L, -1)

        val removed = repository.purgeExpiredOtp()

        assertEquals("only the stale code goes", 1, removed)
        val remaining = database.messages()
            .observeForConversation(database.conversations().getByAddress("8888")!!.id)
            .first()
        assertEquals(1, remaining.size)
        assertTrue(remaining.first().body.contains("222222"))
    }

    @Test
    fun `purging an ordinary thread removes nothing`() = runTest {
        repository.persistIncoming("+966500000001", "hello", 1_000L, -1)
        assertEquals(0, repository.purgeExpiredOtp())
    }

    // ------------------------------------------------------------ undo send

    @Test
    fun `a held message appears at once and is sent when the window closes`() = runTest {
        val id = repository.sendMessageWithUndo(
            address = "+966500000001",
            body = "hello",
            subscriptionId = -1,
            deliveryReport = false,
            delayMillis = 4_000L,
        )

        val conversation = database.conversations().getByAddress("+966500000001")
        assertNotNull("conversation appears immediately", conversation)
        assertNotNull("message is stored immediately", database.messages().getById(id))
        assertTrue("the hold is tracked", repository.isPendingSend(id))

        elapseHold()

        // The send itself fails under Robolectric (no real SmsManager), which is fine: what
        // matters is that the hold ended and the platform was reached.
        assertFalse("the hold is over", repository.isPendingSend(id))
        assertNotNull("the message is still there", database.messages().getById(id))
    }

    @Test
    fun `undoing a held message removes it before the window closes`() = runTest {
        val id = repository.sendMessageWithUndo(
            address = "+966500000001",
            body = "oops",
            subscriptionId = -1,
            deliveryReport = false,
            delayMillis = 4_000L,
        )

        assertTrue(repository.cancelPendingSend(id))
        assertNull("the message is gone", database.messages().getById(id))
        assertNull("the empty conversation is cleaned up", database.conversations().getByAddress("+966500000001"))
    }

    @Test
    fun `undoing after the window closed reports failure`() = runTest {
        val id = repository.sendMessageWithUndo(
            address = "+966500000001",
            body = "hello",
            subscriptionId = -1,
            deliveryReport = false,
            delayMillis = 4_000L,
        )
        elapseHold()

        assertFalse("too late to take it back", repository.cancelPendingSend(id))
    }
}
