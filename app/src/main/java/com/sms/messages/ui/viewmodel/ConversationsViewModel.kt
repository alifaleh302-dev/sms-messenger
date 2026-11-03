package com.sms.messages.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sms.messages.R
import com.sms.messages.data.CategoryEntity
import com.sms.messages.data.CategoryMemberEntity
import com.sms.messages.data.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.sms.messages.data.ConversationEntity
import com.sms.messages.util.PhoneNumbers
import com.sms.messages.util.SmartTab
import kotlinx.coroutines.flow.Flow
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
    /** Custom tabs this conversation has been filed under. */
    val categoryIds: Set<Long> = emptySet(),
)

/** One entry in the tab strip: a built-in tab or a user-created one. */
data class TabItem(
    val key: String,
    val label: String,
    /** Non-null only for custom tabs, which is what makes them editable and deletable. */
    val categoryId: Long? = null,
    val iconIndex: Int = 0,
    val colorIndex: Int = 0,
    val count: Int = 0,
) {
    val isCustom: Boolean get() = categoryId != null
}

data class ConversationsUiState(
    /** The rows to show, already narrowed to the selected tab and the search text. */
    val items: List<ConversationListItem> = emptyList(),
    /**
     * Every conversation, whatever the selected tab. The tab editor lists these, so a
     * conversation can be filed into a tab while a different tab is on screen.
     */
    val allItems: List<ConversationListItem> = emptyList(),
    val tabs: List<TabItem> = emptyList(),
    val selectedTabKey: String = TAB_ALL,
    val query: String = "",
    val loading: Boolean = true,
    val message: String? = null,
) {
    companion object {
        const val TAB_ALL = "all"
        const val TAB_PERSONAL = "personal"
        const val TAB_TRANSACTIONS = "transactions"
        fun tabKeyFor(categoryId: Long) = "category:$categoryId"
    }
}

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SmsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedTabKey = MutableStateFlow(ConversationsUiState.TAB_ALL)
    private val message = MutableStateFlow<String?>(null)

    /**
     * The built-in tab each conversation falls into. Classifying reads every body, so the
     * result is cached and refreshed only when the conversation list changes.
     */
    private val smartTabs = MutableStateFlow<Map<Long, SmartTab>>(emptyMap())

    /** The three stored inputs, kept together so the outer combine stays type-safe. */
    private data class StoredInputs(
        val conversations: List<ConversationEntity>,
        val categories: List<CategoryEntity>,
        val members: List<CategoryMemberEntity>,
    )

    private val storedInputs: Flow<StoredInputs> = combine(
        repository.observeConversations(),
        repository.observeCategories(),
        repository.observeCategoryMembers(),
    ) { conversations, categories, members ->
        StoredInputs(conversations, categories, members)
    }

    val uiState: StateFlow<ConversationsUiState> = combine(
        storedInputs,
        query,
        selectedTabKey,
        message,
    ) { stored, text, selected, note ->
        val conversations = stored.conversations
        val categories = stored.categories
        val membership = stored.members
            .groupBy { it.conversationId }
            .mapValues { (_, rows) -> rows.map { it.categoryId }.toSet() }

        val items = conversations.map { it.toItem(membership[it.id].orEmpty()) }

        val tabs = buildTabs(conversations, categories, membership)
        val activeKey = selected.takeIf { key -> tabs.any { it.key == key } }
            ?: ConversationsUiState.TAB_ALL

        ConversationsUiState(
            items = items.filter { inTab(it, activeKey) }.filter { matches(it, text) },
            allItems = items,
            tabs = tabs,
            selectedTabKey = activeKey,
            query = text,
            loading = false,
            message = note,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationsUiState())

    init {
        viewModelScope.launch {
            repository.observeConversations().collect { conversations ->
                smartTabs.value = repository.smartTabsFor(conversations)
            }
        }
    }

    private fun matches(item: ConversationListItem, text: String): Boolean {
        if (text.isBlank()) return true
        return item.address.contains(text, ignoreCase = true) ||
            item.name.contains(text, ignoreCase = true) ||
            item.snippet.contains(text, ignoreCase = true)
    }

    private fun inTab(item: ConversationListItem, key: String): Boolean = when (key) {
        ConversationsUiState.TAB_ALL -> true
        ConversationsUiState.TAB_PERSONAL -> smartTabs.value[item.id] != SmartTab.TRANSACTIONS
        ConversationsUiState.TAB_TRANSACTIONS -> smartTabs.value[item.id] == SmartTab.TRANSACTIONS
        else -> {
            val id = key.removePrefix("category:").toLongOrNull()
            id != null && id in item.categoryIds
        }
    }

    private fun buildTabs(
        conversations: List<ConversationEntity>,
        categories: List<CategoryEntity>,
        membership: Map<Long, Set<Long>>,
    ): List<TabItem> {
        val labels = smartTabs.value
        val transactions = conversations.count { labels[it.id] == SmartTab.TRANSACTIONS }
        val counts = HashMap<Long, Int>()
        membership.values.forEach { ids -> ids.forEach { counts[it] = (counts[it] ?: 0) + 1 } }

        return buildList {
            add(
                TabItem(
                    ConversationsUiState.TAB_ALL,
                    context.getString(R.string.tab_all),
                    count = conversations.size,
                )
            )
            add(
                TabItem(
                    ConversationsUiState.TAB_PERSONAL,
                    context.getString(R.string.tab_personal),
                    count = conversations.size - transactions,
                )
            )
            add(
                TabItem(
                    ConversationsUiState.TAB_TRANSACTIONS,
                    context.getString(R.string.tab_transactions),
                    count = transactions,
                )
            )
            categories.forEach { category ->
                add(
                    TabItem(
                        key = ConversationsUiState.tabKeyFor(category.id),
                        label = category.name,
                        categoryId = category.id,
                        iconIndex = category.iconIndex,
                        colorIndex = category.colorIndex,
                        count = counts[category.id] ?: 0,
                    )
                )
            }
        }
    }

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
        filtered.map { it.toItem(emptySet()) }
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

    fun selectTab(key: String) {
        selectedTabKey.value = key
    }

    /** Creates a tab and selects it, so the user lands where they just made. */
    fun createCategory(name: String, iconIndex: Int = 0, colorIndex: Int = 0) {
        viewModelScope.launch {
            val id = repository.createCategory(name, iconIndex, colorIndex)
            if (id == null) {
                message.value = context.getString(R.string.tab_name_taken)
            } else {
                selectedTabKey.value = ConversationsUiState.tabKeyFor(id)
            }
        }
    }

    fun renameCategory(id: Long, name: String, iconIndex: Int, colorIndex: Int) {
        viewModelScope.launch {
            if (!repository.updateCategory(id, name, iconIndex, colorIndex)) {
                message.value = context.getString(R.string.tab_name_taken)
            }
        }
    }

    /** Deleting a tab only removes the grouping; the conversations themselves stay. */
    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            if (selectedTabKey.value == ConversationsUiState.tabKeyFor(id)) {
                selectedTabKey.value = ConversationsUiState.TAB_ALL
            }
        }
    }

    fun addToCategory(categoryId: Long, conversationId: Long) {
        viewModelScope.launch {
            repository.addToCategory(categoryId, conversationId)
            message.value = context.getString(R.string.toast_added_to_tab)
        }
    }

    fun removeFromCategory(categoryId: Long, conversationId: Long) {
        viewModelScope.launch {
            repository.removeFromCategory(categoryId, conversationId)
            message.value = context.getString(R.string.toast_removed_from_tab)
        }
    }

    fun clearMessage() {
        message.value = null
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

    private fun ConversationEntity.toItem(categoryIds: Set<Long>): ConversationListItem =
        ConversationListItem(
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
            categoryIds = categoryIds,
        )
}
