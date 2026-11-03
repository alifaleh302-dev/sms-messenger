package com.sms.messages.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sms.messages.R
import com.sms.messages.SmsApp
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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app: SmsApp = application as SmsApp

    val settings: StateFlow<AppSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _isDefaultSms = MutableStateFlow(DefaultSmsRole.isDefault(app))
    val isDefaultSms: StateFlow<Boolean> = _isDefaultSms.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refreshDefaultRole() {
        _isDefaultSms.value = DefaultSmsRole.isDefault(app)
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { app.settings.setTheme(mode) }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        app.settings.setLanguage(language)
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        app.settings.setNotificationsEnabled(enabled)
    }

    fun setVibrate(enabled: Boolean) = viewModelScope.launch { app.settings.setVibrate(enabled) }

    fun setSound(enabled: Boolean) = viewModelScope.launch { app.settings.setSoundEnabled(enabled) }

    fun setDeliveryReports(enabled: Boolean) = viewModelScope.launch {
        app.settings.setDeliveryReports(enabled)
    }

    fun setAutoRetry(enabled: Boolean) = viewModelScope.launch { app.settings.setAutoRetry(enabled) }

    fun setSignature(value: String) = viewModelScope.launch { app.settings.setSignature(value) }

    fun importFromTelephony() = viewModelScope.launch {
        val count = app.repository.syncFromTelephony()
        _message.value = if (count > 0) {
            app.getString(R.string.settings_import_result, count)
        } else {
            app.getString(R.string.settings_import_none)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
