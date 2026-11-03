package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.messages.SmsApp
import com.sms.messages.data.ScheduledMessageEntity
import com.sms.messages.sms.ScheduledMessageScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduledViewModel(private val app: SmsApp) : ViewModel() {

    val items: StateFlow<List<ScheduledMessageEntity>> = app.repository.observeScheduled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(item: ScheduledMessageEntity) = viewModelScope.launch {
        ScheduledMessageScheduler.cancel(app, item.id)
        app.repository.cancelScheduled(item.id)
    }

    fun reschedule(item: ScheduledMessageEntity, atMillis: Long) = viewModelScope.launch {
        app.repository.cancelScheduled(item.id)
        val id = app.repository.scheduleMessage(
            address = item.address,
            body = item.body,
            atMillis = atMillis,
            subscriptionId = item.subscriptionId,
        )
        ScheduledMessageScheduler.schedule(app, id, atMillis)
    }
}
