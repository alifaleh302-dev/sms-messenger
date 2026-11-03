package com.sms.messages.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sms.messages.data.AppDatabase
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import com.sms.messages.notifications.MessageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * Drives [ChatViewModel] against the real database and real repository so the multi-select,
 * reply, star and trash flows are verified the way the screen uses them, not in isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var repository: SmsRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        // Room's observation flows run on its own executors; pointing them at the test
        // dispatcher lets advanceUntilIdle() drive them, otherwise the tests race the writes.
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        repository = SmsRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun seed(vararg bodies: String): Long {
        bodies.forEachIndexed { index, body ->
            repository.persistIncoming("+966500000099", body, 1_000L * (index + 1), -1)
        }
        return database.conversations().getByAddress("+966500000099")!!.id
    }

    private fun TestScope.build(conversationId: Long) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settings = SettingsRepository(context)
        viewModel = ChatViewModel(
            repository = repository,
            settingsRepository = settings,
            context = context,
            notifier = MessageNotifier(
                context = context,
                repository = repository,
                settingsRepository = settings,
                scope = CoroutineScope(dispatcher),
            ),
            savedStateHandle = SavedStateHandle(mapOf("conversationId" to conversationId.toString())),
        )
        // uiState is WhileSubscribed, so it only fills in while something collects it.
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun `selecting messages builds a selection that survives toggling twice`() = runTest(dispatcher) {
        val id = seed("one", "two", "three")
        build(id)
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        assertEquals(3, messages.size)

        viewModel.toggleSelection(messages[0].id)
        viewModel.toggleSelection(messages[1].id)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.selectedIds.size)
        assertTrue(viewModel.uiState.value.inSelectionMode)

        viewModel.toggleSelection(messages[1].id)
        advanceUntilIdle()
        assertEquals(setOf(messages[0].id), viewModel.uiState.value.selectedIds)

        viewModel.clearSelection()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.inSelectionMode)
    }

    @Test
    fun `select all picks every message and the collected text is ordered oldest first`() =
        runTest(dispatcher) {
            val id = seed("first", "second", "third")
            build(id)
            advanceUntilIdle()

            viewModel.selectAll()
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.selectedIds.size)
            assertEquals("first\nsecond\nthird", viewModel.selectedText())
        }

    @Test
    fun `deleting a selection removes those messages and clears the selection`() =
        runTest(dispatcher) {
            val id = seed("keep", "drop me")
            build(id)
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            viewModel.toggleSelection(messages[1].id)
            viewModel.deleteSelected()
            advanceUntilIdle()

            val remaining = viewModel.uiState.value.messages
            assertEquals(1, remaining.size)
            assertEquals("keep", remaining.first().body)
            assertFalse(viewModel.uiState.value.inSelectionMode)
        }

    @Test
    fun `starring a selection marks every chosen message`() = runTest(dispatcher) {
        val id = seed("a", "b", "c")
        build(id)
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        viewModel.toggleSelection(messages[0].id)
        viewModel.toggleSelection(messages[2].id)
        viewModel.starSelected()
        advanceUntilIdle()

        val starred = repository.observeStarred().first().map { it.body }.toSet()
        assertEquals(setOf("a", "c"), starred)
    }

    @Test
    fun `a reply is attached to the outgoing message and clears the quote`() = runTest(dispatcher) {
        val id = seed("original question")
        build(id)
        advanceUntilIdle()

        val original = viewModel.uiState.value.messages.single()
        viewModel.startReply(original)
        advanceUntilIdle()
        assertEquals(original.id, viewModel.uiState.value.replyTo?.id)

        viewModel.onDraftChange("my answer")
        viewModel.send()
        advanceUntilIdle()

        assertNull("the quote resets after sending", viewModel.uiState.value.replyTo)
        val sent = viewModel.uiState.value.messages.last()
        assertEquals("my answer", sent.body)
        assertEquals("the reply points at the quoted message", original.id, sent.replyToId)
    }

    @Test
    fun `cancelling a reply drops the quote without sending anything`() = runTest(dispatcher) {
        val id = seed("question")
        build(id)
        advanceUntilIdle()

        viewModel.startReply(viewModel.uiState.value.messages.single())
        viewModel.cancelReply()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.replyTo)
        assertEquals(1, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `reacting to a message shows the chip on that message only`() = runTest(dispatcher) {
        val id = seed("react here", "not here")
        build(id)
        advanceUntilIdle()

        val target = viewModel.uiState.value.messages.first()
        viewModel.toggleReaction(target.id, "🎉")
        advanceUntilIdle()

        val reactions = viewModel.uiState.value.reactions
        assertEquals(1, reactions[target.id]?.size ?: 0)
        assertEquals("🎉", reactions[target.id]!!.first().emoji)
        val other = viewModel.uiState.value.messages[1].id
        assertTrue("the other message has no reaction", reactions[other].isNullOrEmpty())
    }

    @Test
    fun `trashing the conversation hides it from the inbox`() = runTest(dispatcher) {
        val id = seed("to be trashed")
        build(id)
        advanceUntilIdle()

        viewModel.trashConversation()
        advanceUntilIdle()

        assertTrue(repository.observeConversations().first().isEmpty())
        assertEquals(1, repository.observeTrashed().first().size)
    }

    @Test
    fun `opening details exposes the message and closing hides it`() = runTest(dispatcher) {
        val id = seed("inspect me")
        build(id)
        advanceUntilIdle()

        val message = viewModel.uiState.value.messages.single()
        viewModel.showDetails(message)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.detailsFor)
        assertEquals("inspect me", viewModel.uiState.value.detailsFor!!.body)

        viewModel.hideDetails()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.detailsFor)
    }
}
