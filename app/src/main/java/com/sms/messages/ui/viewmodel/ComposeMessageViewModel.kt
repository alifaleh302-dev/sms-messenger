package com.sms.messages.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.messages.R
import com.sms.messages.SmsApp
import com.sms.messages.data.AppSettings
import com.sms.messages.data.Contact
import com.sms.messages.data.TemplateEntity
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipientSuggestion(
    val name: String,
    val number: String,
    val isContact: Boolean,
)

data class ComposeUiState(
    val recipients: List<RecipientSuggestion> = emptyList(),
    val query: String = "",
    val body: String = "",
    val suggestions: List<RecipientSuggestion> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val sending: Boolean = false,
    val error: String? = null,
    val scheduledAt: Long? = null,
)

class ComposeMessageViewModel(
    private val app: SmsApp,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val presetAddress = savedStateHandle.get<String>("address").orEmpty()

    private val recipients = MutableStateFlow<List<RecipientSuggestion>>(emptyList())
    private val query = MutableStateFlow("")
    private val body = MutableStateFlow("")
    private val suggestions = MutableStateFlow<List<RecipientSuggestion>>(emptyList())
    private val sending = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val scheduledAt = MutableStateFlow<Long?>(null)
    private val settings = MutableStateFlow(AppSettings())
    private var allContacts: List<Contact> = emptyList()

    val uiState: StateFlow<ComposeUiState> = combine(
        recipients, query, body, suggestions,
        app.repository.observeTemplates(), settings,
        sending, error, scheduledAt,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ComposeUiState(
            recipients = values[0] as List<RecipientSuggestion>,
            query = values[1] as String,
            body = values[2] as String,
            suggestions = values[3] as List<RecipientSuggestion>,
            templates = values[4] as List<TemplateEntity>,
            settings = values[5] as AppSettings,
            sending = values[6] as Boolean,
            error = values[7] as String?,
            scheduledAt = values[8] as Long?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComposeUiState())

    init {
        viewModelScope.launch { app.settings.settings.collect { settings.value = it } }
        viewModelScope.launch {
            allContacts = app.repository.loadContacts()
            query.value = ""
            suggestions.value = allContacts.take(40).map { it.toSuggestion() }
        }
        if (presetAddress.isNotBlank()) {
            addRecipient(presetAddress, null)
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        val text = value.trim()
        suggestions.value = when {
            text.isEmpty() -> allContacts.take(40).map { it.toSuggestion() }
            else -> allContacts
                .asSequence()
                .filter {
                    it.name.contains(text, ignoreCase = true) ||
                        it.normalized.contains(text) ||
                        PhoneNumbers.format(it.number).contains(text)
                }
                .take(40)
                .map { it.toSuggestion() }
                .toList()
        }
    }

    fun onBodyChange(value: String) {
        body.value = value
    }

    fun addRecipient(number: String, name: String?) {
        if (!PhoneNumbers.isValid(number)) {
            error.value = app.getString(R.string.invalid_recipient)
            return
        }
        val normalized = PhoneNumbers.normalize(number).ifEmpty { number.trim() }
        if (normalized.isEmpty()) return
        if (recipients.value.any { it.number == normalized }) return
        recipients.value = recipients.value + RecipientSuggestion(
            name = name ?: PhoneNumbers.format(normalized),
            number = normalized,
            isContact = name != null,
        )
        query.value = ""
        error.value = null
    }

    fun removeRecipient(number: String) {
        recipients.value = recipients.value.filterNot { it.number == number }
    }

    fun applyTemplate(template: TemplateEntity) {
        body.value = template.body
        viewModelScope.launch { app.repository.markTemplateUsed(template.id) }
    }

    fun schedule(atMillis: Long?) {
        scheduledAt.value = atMillis
    }

    fun send() {
        val text = body.value.trim()
        if (recipients.value.isEmpty()) {
            error.value = app.getString(R.string.error_add_recipient)
            return
        }
        if (text.isEmpty() && scheduledAt.value == null) {
            error.value = app.getString(R.string.error_message_empty)
            return
        }
        val targets = recipients.value.map { it.number }
        val settingsValue = settings.value
        val scheduledTime = scheduledAt.value
        sending.value = true
        viewModelScope.launch {
            try {
                val signature = settingsValue.signature.trim()
                val outgoing = if (signature.isEmpty()) text else "$text\n$signature"
                if (scheduledTime != null) {
                    targets.forEach { target ->
                        app.repository.scheduleMessage(
                            address = target,
                            body = outgoing,
                            atMillis = scheduledTime,
                            subscriptionId = settingsValue.defaultSubscriptionId,
                        )
                    }
                    scheduledAt.value = null
                } else {
                    targets.forEach { target ->
                        app.repository.sendMessage(
                            address = target,
                            body = outgoing,
                            subscriptionId = settingsValue.defaultSubscriptionId,
                            deliveryReport = settingsValue.deliveryReports,
                        )
                    }
                }
                body.value = ""
                recipients.value = emptyList()
                error.value = null
            } catch (t: Throwable) {
                error.value = t.message ?: app.getString(R.string.error_unable_send)
            } finally {
                sending.value = false
            }
        }
    }

    fun clearError() {
        error.value = null
    }

    private fun Contact.toSuggestion() = RecipientSuggestion(name = name, number = normalized, isContact = true)
}
