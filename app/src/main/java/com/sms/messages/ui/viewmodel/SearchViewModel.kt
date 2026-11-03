package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.sms.messages.data.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.sms.messages.data.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SmsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val starredOnly = MutableStateFlow(false)
    private val unreadOnly = MutableStateFlow(false)

    private data class SearchInputs(
        val text: String,
        val starred: Boolean,
        val unread: Boolean,
    )

    private val inputs: Flow<SearchInputs> = combine(query, starredOnly, unreadOnly) { text, star, unread ->
        SearchInputs(text, star, unread)
    }

    val results: StateFlow<List<SearchResult>> = inputs
        .debounce(250)
        .flatMapLatest { input ->
            val text = input.text.trim()
            // A filter on its own is a valid search, so the length rule only applies to text.
            val hasFilter = input.starred || input.unread
            when {
                text.length >= 2 || hasFilter ->
                    repository.search(text, input.starred, input.unread)
                else -> flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentQuery: StateFlow<String> = query

    val isStarredFilter: StateFlow<Boolean> = starredOnly

    val isUnreadFilter: StateFlow<Boolean> = unreadOnly

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun toggleStarredFilter() {
        starredOnly.value = !starredOnly.value
    }

    fun toggleUnreadFilter() {
        unreadOnly.value = !unreadOnly.value
    }
}
