package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.sms.messages.data.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.sms.messages.data.ConversationEntity
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationListItem(
    val id: Long,
    val address: String,
    val name: String,
    val snippet: String,
    val timestamp: Long,
    val unread: Int,
    val pinned: Boolean,
    val muted: Boolean,
    val archived: Boolean,
    val draft: String,
    val colorIndex: Int,
)

data class ConversationsUiState(
    val items: List<ConversationListItem> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val repository: SmsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<ConversationsUiState> = combine(
        repository.observeConversations(),
        query,
    ) { conversations, text ->
        val filtered = if (text.isBlank()) {
            conversations
        } else {
            conversations.filter { conversation ->
                conversation.address.contains(text, ignoreCase = true) ||
                    conversation.displayName?.contains(text, ignoreCase = true) == true ||
                    conversation.snippet.contains(text, ignoreCase = true)
            }
        }
        ConversationsUiState(
            items = filtered.map { it.toItem() },
            query = text,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationsUiState())

    val archived: StateFlow<List<ConversationListItem>> = combine(
        repository.observeArchived(),
        query,
    ) { list, text ->
        val filtered = if (text.isBlank()) {
            list
        } else {
            list.filter {
                it.address.contains(text, ignoreCase = true) ||
                    it.displayName?.contains(text, ignoreCase = true) == true
            }
        }
        filtered.map { it.toItem() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadTotal: StateFlow<Int> = repository.observeTotalUnread()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val starredCount: StateFlow<Int> = repository.observeStarredCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val trashCount: StateFlow<Int> = repository.observeTrashCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun archive(id: Long) = viewModelScope.launch { repository.setArchived(id, true) }

    fun unarchive(id: Long) = viewModelScope.launch { repository.setArchived(id, false) }

    fun togglePin(id: Long, pinned: Boolean) =
        viewModelScope.launch { repository.setPinned(id, !pinned) }

    fun toggleMute(id: Long, muted: Boolean) =
        viewModelScope.launch { repository.setMuted(id, !muted) }

    fun markRead(id: Long) = viewModelScope.launch { repository.markConversationRead(id) }

    fun markUnread(id: Long) = viewModelScope.launch { repository.markConversationUnread(id) }

    /** Moves to trash rather than erasing, so the conversation stays recoverable. */
    fun trash(id: Long) = viewModelScope.launch { repository.trashConversation(id) }

    fun delete(id: Long) = viewModelScope.launch { repository.deleteConversation(id) }

    fun markAllRead() = viewModelScope.launch {
        uiState.value.items.forEach { repository.markConversationRead(it.id) }
    }

    fun block(address: String) = viewModelScope.launch { repository.blockNumber(address) }

    private fun ConversationEntity.toItem(): ConversationListItem = ConversationListItem(
        id = id,
        address = address,
        name = displayName?.takeIf { it.isNotBlank() }
            ?: PhoneNumbers.format(address).ifEmpty { address },
        snippet = snippet,
        timestamp = lastMessageAt,
        unread = unreadCount,
        pinned = pinned,
        muted = muted,
        archived = archived,
        draft = draft,
        colorIndex = colorIndex,
    )
}
