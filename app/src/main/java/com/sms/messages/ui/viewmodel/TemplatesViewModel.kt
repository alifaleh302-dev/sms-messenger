package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.messages.SmsApp
import com.sms.messages.data.TemplateEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(private val app: SmsApp) : ViewModel() {

    val templates: StateFlow<List<TemplateEntity>> = app.repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, title: String, body: String, onDone: () -> Unit = {}) {
        if (title.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            app.repository.saveTemplate(id, title.trim(), body.trim())
            onDone()
        }
    }

    fun delete(template: TemplateEntity) = viewModelScope.launch {
        app.repository.deleteTemplate(template)
    }
}
