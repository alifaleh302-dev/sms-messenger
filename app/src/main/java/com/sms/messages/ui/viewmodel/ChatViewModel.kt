package com.sms.messages.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.messages.R
import com.sms.messages.SmsApp
import com.sms.messages.data.AppSettings
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import com.sms.messages.notifications.MessageNotifier
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val app: SmsApp,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: Long = savedStateHandle.get<String>("conversationId")
        ?.toLongOrNull() ?: -1L

    private val draft = MutableStateFlow("")
    private val blocked = MutableStateFlow(false)
    private val settings = MutableStateFlow(AppSettings())

    val uiState: StateFlow<ChatUiState> = combine(
        app.repository.observeConversation(conversationId),
        app.repository.observeMessages(conversationId),
        draft,
        blocked,
        settings,
    ) { conversation, messages, draftText, isBlocked, appSettings ->
        val address = conversation?.address.orEmpty()
        val title = conversation?.displayName?.takeIf { it.isNotBlank() }
            ?: PhoneNumbers.format(address).ifEmpty { app.getString(R.string.unknown_contact) }
        ChatUiState(
            conversationId = conversationId,
            address = address,
            title = title,
            colorIndex = conversation?.colorIndex ?: 0,
            messages = messages,
            draft = draftText,
            settings = appSettings,
            blocked = isBlocked,
            archived = conversation?.archived ?: false,
            pinned = conversation?.pinned ?: false,
            muted = conversation?.muted ?: false,
            subscriptionId = appSettings.defaultSubscriptionId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            app.settings.settings.collect { settings.value = it }
        }
        viewModelScope.launch {
            app.repository.observeConversation(conversationId)
                .map { it?.address }
                .distinctUntilChanged()
                .flatMapLatest { address ->
                    if (address.isNullOrBlank()) {
                        flowOf(false)
                    } else {
                        flow { emit(app.repository.isBlocked(address)) }
                    }
                }
                .collect { blocked.value = it }
        }
        viewModelScope.launch {
            val conversation = app.repository.observeConversation(conversationId).first()
            draft.value = conversation?.draft.orEmpty()
            app.repository.markConversationRead(conversationId)
            MessageNotifier(app).cancel(conversationId)
        }
    }

    fun onDraftChange(value: String) {
        draft.value = value
        viewModelScope.launch { app.repository.saveDraft(conversationId, value) }
    }

    fun send(text: String = draft.value) {
        val body = text.trim()
        if (body.isEmpty()) return
        val address = uiState.value.address
        if (address.isBlank()) return
        draft.value = ""
        viewModelScope.launch {
            val settingsValue = settings.value
            runCatching {
                app.repository.sendMessage(
                    address = address,
                    body = body,
                    subscriptionId = settingsValue.defaultSubscriptionId,
                    deliveryReport = settingsValue.deliveryReports,
                )
            }.onFailure { Log.w("ChatViewModel", "Unable to send message", it) }
        }
    }

    fun retry(messageId: Long) = viewModelScope.launch {
        app.repository.retryMessage(messageId, settings.value.deliveryReports)
    }

    fun deleteMessage(messageId: Long) = viewModelScope.launch {
        app.repository.deleteMessage(messageId)
    }

    fun markRead() = viewModelScope.launch {
        app.repository.markConversationRead(conversationId)
        MessageNotifier(app).cancel(conversationId)
    }

    fun deleteConversation() = viewModelScope.launch {
        MessageNotifier(app).cancel(conversationId)
        app.repository.deleteConversation(conversationId)
    }

    fun toggleArchive() = viewModelScope.launch {
        app.repository.setArchived(conversationId, !uiState.value.archived)
    }

    fun togglePin() = viewModelScope.launch {
        app.repository.setPinned(conversationId, !uiState.value.pinned)
    }

    fun toggleMute() = viewModelScope.launch {
        app.repository.setMuted(conversationId, !uiState.value.muted)
    }

    fun block() = viewModelScope.launch {
        val address = uiState.value.address
        if (address.isNotBlank()) app.repository.blockNumber(address)
    }

    fun unblock() = viewModelScope.launch {
        val address = uiState.value.address
        if (address.isNotBlank()) app.repository.unblockNumber(address)
    }

    fun statusOf(message: MessageEntity): MessageStatus =
        if (message.type == MessageType.INBOX) MessageStatus.NONE else message.status
}
