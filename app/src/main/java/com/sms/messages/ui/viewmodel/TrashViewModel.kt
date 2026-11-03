package com.sms.messages.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sms.messages.data.ConversationEntity
import com.sms.messages.data.SmsRepository
import com.sms.messages.util.PhoneNumbers
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A trashed conversation with the number of days it has left before it is purged. */
data class TrashItem(
    val id: Long,
    val title: String,
    val snippet: String,
    val colorIndex: Int,
    val trashedAt: Long,
    val daysLeft: Int,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: SmsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val items: StateFlow<List<TrashItem>> = repository.observeTrashed()
        .map { conversations -> conversations.map { it.toTrashItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: Long) = viewModelScope.launch { repository.restoreConversation(id) }

    fun deleteForever(id: Long) = viewModelScope.launch { repository.purgeConversation(id) }

    fun emptyTrash() = viewModelScope.launch {
        items.value.forEach { repository.purgeConversation(it.id) }
    }

    /** Drops anything past the retention window, called when the screen opens. */
    fun purgeExpired() = viewModelScope.launch { repository.purgeExpiredTrash() }

    private fun ConversationEntity.toTrashItem(): TrashItem {
        val elapsed = System.currentTimeMillis() - trashedAt
        val daysLeft = (RETENTION_DAYS - (elapsed / DAY_MILLIS)).coerceAtLeast(0).toInt()
        return TrashItem(
            id = id,
            title = displayName?.takeIf { it.isNotBlank() } ?: PhoneNumbers.format(address),
            snippet = snippet,
            colorIndex = colorIndex,
            trashedAt = trashedAt,
            daysLeft = daysLeft,
        )
    }

    private companion object {
        const val RETENTION_DAYS = 30L
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
