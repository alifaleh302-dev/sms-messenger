package com.sms.messages.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sms.messages.data.SmsRepository
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.sms.messages.data.ScheduledMessageEntity
import com.sms.messages.sms.ScheduledMessageScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduledViewModel @Inject constructor(
    private val repository: SmsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val items: StateFlow<List<ScheduledMessageEntity>> = repository.observeScheduled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(item: ScheduledMessageEntity) = viewModelScope.launch {
        ScheduledMessageScheduler.cancel(context, item.id)
        repository.cancelScheduled(item.id)
    }

    fun reschedule(item: ScheduledMessageEntity, atMillis: Long) = viewModelScope.launch {
        repository.cancelScheduled(item.id)
        val id = repository.scheduleMessage(
            address = item.address,
            body = item.body,
            atMillis = atMillis,
            subscriptionId = item.subscriptionId,
        )
        ScheduledMessageScheduler.schedule(context, id, atMillis)
    }
}
