package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.sms.messages.data.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.sms.messages.data.BlockedNumberEntity
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BlockedViewModel @Inject constructor(
    private val repository: SmsRepository,
) : ViewModel() {

    val blocked: StateFlow<List<BlockedNumberEntity>> = repository.observeBlocked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun block(rawNumber: String) = viewModelScope.launch {
        val normalized = PhoneNumbers.normalize(rawNumber)
        if (normalized.isNotEmpty()) repository.blockNumber(normalized)
    }

    fun unblock(address: String) = viewModelScope.launch {
        repository.unblockNumber(address)
    }
}
