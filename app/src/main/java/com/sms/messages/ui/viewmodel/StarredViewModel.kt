package com.sms.messages.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.SmsRepository
import com.sms.messages.util.PhoneNumbers
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One starred message plus the conversation it came from, for the starred list. */
data class StarredItem(
    val message: MessageEntity,
    val conversationId: Long,
    val title: String,
    val colorIndex: Int,
)

@HiltViewModel
class StarredViewModel @Inject constructor(
    private val repository: SmsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val items: StateFlow<List<StarredItem>> = combine(
        repository.observeStarred(),
        repository.observeConversations(),
    ) { messages, conversations ->
        val byId = conversations.associateBy { it.id }
        messages.map { message ->
            val conversation = byId[message.conversationId]
            StarredItem(
                message = message,
                conversationId = message.conversationId,
                title = conversation?.displayName?.takeIf { it.isNotBlank() }
                    ?: PhoneNumbers.format(message.address),
                colorIndex = conversation?.colorIndex ?: 0,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unstar(message: MessageEntity) = viewModelScope.launch {
        repository.setStarred(message.id, false)
    }
}
