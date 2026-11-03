package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.messages.SmsApp
import com.sms.messages.data.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val app: SmsApp) : ViewModel() {

    private val query = MutableStateFlow("")

    val results: StateFlow<List<SearchResult>> = query
        .debounce(250)
        .flatMapLatest { text ->
            if (text.trim().length < 2) flowOf(emptyList()) else app.repository.search(text.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentQuery: StateFlow<String> = query

    fun onQueryChange(value: String) {
        query.value = value
    }
}
