package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.messages.SmsApp
import com.sms.messages.data.BlockedNumberEntity
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlockedViewModel(private val app: SmsApp) : ViewModel() {

    val blocked: StateFlow<List<BlockedNumberEntity>> = app.repository.observeBlocked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun block(rawNumber: String) = viewModelScope.launch {
        val normalized = PhoneNumbers.normalize(rawNumber)
        if (normalized.isNotEmpty()) app.repository.blockNumber(normalized)
    }

    fun unblock(address: String) = viewModelScope.launch {
        app.repository.unblockNumber(address)
    }
}
