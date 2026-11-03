package com.sms.messages.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import javax.inject.Inject
import com.sms.messages.R
import com.sms.messages.data.AppLanguage
import com.sms.messages.data.AppSettings
import com.sms.messages.data.ThemeMode
import com.sms.messages.util.DefaultSmsRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SmsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _isDefaultSms = MutableStateFlow(DefaultSmsRole.isDefault(context))
    val isDefaultSms: StateFlow<Boolean> = _isDefaultSms.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refreshDefaultRole() {
        _isDefaultSms.value = DefaultSmsRole.isDefault(context)
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setTheme(mode) }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsRepository.setLanguage(language)
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setNotificationsEnabled(enabled)
    }

    fun setVibrate(enabled: Boolean) = viewModelScope.launch { settingsRepository.setVibrate(enabled) }

    fun setSound(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }

    fun setDeliveryReports(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDeliveryReports(enabled)
    }

    fun setAutoRetry(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoRetry(enabled) }

    fun setSignature(value: String) = viewModelScope.launch { settingsRepository.setSignature(value) }

    fun importFromTelephony() = viewModelScope.launch {
        val count = repository.syncFromTelephony()
        _message.value = if (count > 0) {
            context.getString(R.string.settings_import_result, count)
        } else {
            context.getString(R.string.settings_import_none)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
