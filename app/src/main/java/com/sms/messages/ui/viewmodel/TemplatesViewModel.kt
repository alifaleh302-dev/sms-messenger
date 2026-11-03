package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.sms.messages.data.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.sms.messages.data.TemplateEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: SmsRepository,
) : ViewModel() {

    val templates: StateFlow<List<TemplateEntity>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, title: String, body: String, onDone: () -> Unit = {}) {
        if (title.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            repository.saveTemplate(id, title.trim(), body.trim())
            onDone()
        }
    }

    fun delete(template: TemplateEntity) = viewModelScope.launch {
        repository.deleteTemplate(template)
    }
}
