package com.sms.messages.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.sms.messages.R
import com.sms.messages.data.AppSettings
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.ConversationEntity
import com.sms.messages.data.MessageType
import com.sms.messages.data.ReactionEntity
import com.sms.messages.notifications.MessageNotifier
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: Long = -1,
    val address: String = "",
    val title: String = "",
    val colorIndex: Int = 0,
    val messages: List<MessageEntity> = emptyList(),
    val draft: String = "",
    val settings: AppSettings = AppSettings(),
    val blocked: Boolean = false,
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val subscriptionId: Int = -1,
    /** Reactions for the whole conversation, keyed by message id. */
    val reactions: Map<Long, List<ReactionEntity>> = emptyMap(),
    /** Message ids currently selected in multi-select mode. */
    val selectedIds: Set<Long> = emptySet(),
    /** The message the composer is quoting, if any. */
    val replyTo: MessageEntity? = null,
    /** The message whose details dialog is open. */
    val detailsFor: MessageEntity? = null,
    /**
     * A message that is being held before it goes to the network. The screen shows the
     * undo bar while this is set.
     */
    val pendingSendId: Long? = null,
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: SmsRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
    private val notifier: MessageNotifier,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: Long = savedStateHandle.get<String>("conversationId")
        ?.toLongOrNull() ?: -1L

    private val draft = MutableStateFlow("")
    private val blocked = MutableStateFlow(false)
    private val sendFailure = MutableStateFlow(false)
    private val settings = MutableStateFlow(AppSettings())
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingSend = MutableStateFlow<Long?>(null)
    private val replyToId = MutableStateFlow<Long?>(null)
    private val detailsFor = MutableStateFlow<MessageEntity?>(null)

    private val uiInputs: Flow<UiInputs> = combine(
        repository.observeConversation(conversationId),
        repository.observeMessages(conversationId),
        repository.observeReactionsIn(conversationId),
    ) { conversation, messages, reactions ->
        UiInputs(conversation, messages, reactions)
    }

    private val localState: Flow<LocalState> = combine(
        draft, blocked, settings, selectedIds, replyToId, detailsFor, pendingSend,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        LocalState(
            draft = values[0] as String,
            blocked = values[1] as Boolean,
            settings = values[2] as AppSettings,
            selectedIds = values[3] as Set<Long>,
            replyToId = values[4] as Long?,
            detailsFor = values[5] as MessageEntity?,
            pendingSendId = values[6] as Long?,
        )
    }

    val uiState: StateFlow<ChatUiState> = combine(uiInputs, localState) { inputs, local ->
        val conversation = inputs.conversation
        val messages = inputs.messages
        val address = conversation?.address.orEmpty()
        val title = conversation?.displayName?.takeIf { it.isNotBlank() }
            ?: PhoneNumbers.format(address).ifEmpty { context.getString(R.string.unknown_contact) }
        ChatUiState(
            conversationId = conversationId,
            address = address,
            title = title,
            colorIndex = conversation?.colorIndex ?: 0,
            messages = messages,
            draft = local.draft,
            settings = local.settings,
            blocked = local.blocked,
            archived = conversation?.archived ?: false,
            pinned = conversation?.pinned ?: false,
            muted = conversation?.muted ?: false,
            subscriptionId = local.settings.defaultSubscriptionId,
            reactions = inputs.reactions.groupBy { it.messageId },
            selectedIds = local.selectedIds,
            replyTo = local.replyToId?.let { target -> messages.firstOrNull { it.id == target } },
            detailsFor = local.detailsFor,
            pendingSendId = local.pendingSendId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private data class UiInputs(
        val conversation: ConversationEntity?,
        val messages: List<MessageEntity>,
        val reactions: List<ReactionEntity>,
    )

    private data class LocalState(
        val draft: String,
        val blocked: Boolean,
        val settings: AppSettings,
        val selectedIds: Set<Long>,
        val replyToId: Long?,
        val detailsFor: MessageEntity?,
        val pendingSendId: Long?,
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings.value = it }
        }
        viewModelScope.launch {
            repository.observeConversation(conversationId)
                .map { it?.address }
                .distinctUntilChanged()
                .flatMapLatest { address ->
                    if (address.isNullOrBlank()) {
                        flowOf(false)
                    } else {
                        flow { emit(repository.isBlocked(address)) }
                    }
                }
                .collect { blocked.value = it }
        }
        viewModelScope.launch {
            val conversation = repository.observeConversation(conversationId).first()
            draft.value = conversation?.draft.orEmpty()
            repository.markConversationRead(conversationId)
            notifier.cancel(conversationId)
        }
    }

    fun onDraftChange(value: String) {
        draft.value = value
        viewModelScope.launch { repository.saveDraft(conversationId, value) }
    }

    fun send(text: String = draft.value) {
        val body = text.trim()
        if (body.isEmpty()) return
        val address = uiState.value.address
        if (address.isBlank()) return
        val quoted = replyToId.value
        draft.value = ""
        replyToId.value = null
        viewModelScope.launch {
            val settingsValue = settings.value
            val holdMillis = settingsValue.undoSendSeconds * 1000L
            runCatching {
                if (holdMillis > 0L) {
                    val id = repository.sendMessageWithUndo(
                        address = address,
                        body = body,
                        subscriptionId = settingsValue.defaultSubscriptionId,
                        deliveryReport = settingsValue.deliveryReports,
                        delayMillis = holdMillis,
                        replyToId = quoted,
                    )
                    // The screen owns the countdown so the bar disappears exactly when the
                    // hold ends, even if the user leaves and comes back.
                    pendingSend.value = id
                } else {
                    repository.sendMessage(
                        address = address,
                        body = body,
                        subscriptionId = settingsValue.defaultSubscriptionId,
                        deliveryReport = settingsValue.deliveryReports,
                        replyToId = quoted,
                    )
                }
            }.onFailure {
                Log.w("ChatViewModel", "Unable to send message", it)
                sendFailure.value = true
            }
        }
    }

    /** Takes back the held message. Returns false when the window had already closed. */
    fun undoPendingSend() {
        val id = pendingSend.value ?: return
        pendingSend.value = null
        viewModelScope.launch { repository.cancelPendingSend(id) }
    }

    /** Called when the hold elapses on its own, so the bar can be dismissed. */
    fun clearPendingSend() {
        pendingSend.value = null
    }

    /** Clears the "could not send" prompt after the screen has shown it. */
    fun clearSendFailure() {
        sendFailure.value = false
    }

    fun retry(messageId: Long) = viewModelScope.launch {
        repository.retryMessage(messageId, settings.value.deliveryReports)
    }

    val sendFailed: StateFlow<Boolean> = sendFailure.asStateFlow()

    fun markRead() = viewModelScope.launch {
        repository.markConversationRead(conversationId)
        notifier.cancel(conversationId)
    }

    fun deleteConversation() = viewModelScope.launch {
        notifier.cancel(conversationId)
        repository.deleteConversation(conversationId)
    }

    fun restoreConversation() = viewModelScope.launch {
        repository.restoreConversation(conversationId)
    }

    fun toggleArchive() = viewModelScope.launch {
        repository.setArchived(conversationId, !uiState.value.archived)
    }

    fun togglePin() = viewModelScope.launch {
        repository.setPinned(conversationId, !uiState.value.pinned)
    }

    fun toggleMute() = viewModelScope.launch {
        repository.setMuted(conversationId, !uiState.value.muted)
    }

    fun block() = viewModelScope.launch {
        val address = uiState.value.address
        if (address.isNotBlank()) repository.blockNumber(address)
    }

    fun unblock() = viewModelScope.launch {
        val address = uiState.value.address
        if (address.isNotBlank()) repository.unblockNumber(address)
    }

    fun statusOf(message: MessageEntity): MessageStatus =
        if (message.type == MessageType.INBOX) MessageStatus.NONE else message.status

    // ------------------------------------------------------------ message actions

    fun toggleStar(message: MessageEntity) = viewModelScope.launch {
        repository.setStarred(message.id, !message.starred)
    }

    fun toggleReaction(messageId: Long, emoji: String) = viewModelScope.launch {
        repository.toggleReaction(messageId, emoji)
    }

    fun startReply(message: MessageEntity) {
        replyToId.value = message.id
    }

    fun cancelReply() {
        replyToId.value = null
    }

    fun showDetails(message: MessageEntity) {
        detailsFor.value = message
    }

    fun hideDetails() {
        detailsFor.value = null
    }

    fun deleteMessage(messageId: Long) = viewModelScope.launch {
        repository.deleteMessage(messageId)
    }

    /** Moves the conversation to trash instead of erasing it, so it can be recovered. */
    fun trashConversation() = viewModelScope.launch {
        notifier.cancel(conversationId)
        repository.trashConversation(conversationId)
    }

    // ------------------------------------------------------------ multi-select

    fun toggleSelection(messageId: Long) {
        selectedIds.value = selectedIds.value.let { current ->
            if (messageId in current) current - messageId else current + messageId
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun selectAll() {
        selectedIds.value = uiState.value.messages.map { it.id }.toSet()
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return@launch
        selectedIds.value = emptySet()
        repository.deleteMessages(ids)
    }

    fun starSelected() = viewModelScope.launch {
        val ids = selectedIds.value
        if (ids.isEmpty()) return@launch
        ids.forEach { repository.setStarred(it, true) }
        selectedIds.value = emptySet()
    }

    /**
     * Collects the selected messages into one block of text so they can be shared. Copies
     * only what is visible in the conversation, oldest first.
     */
    fun selectedText(): String {
        val ids = selectedIds.value
        return uiState.value.messages
            .filter { it.id in ids }
            .joinToString("\n") { it.body }
            .trim()
    }
}
