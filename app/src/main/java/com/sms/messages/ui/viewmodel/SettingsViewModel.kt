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
import kotlinx.coroutines.flow.first
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
            context.resources.getQuantityString(R.plurals.settings_import_result, count, count)
        } else {
            context.getString(R.string.settings_import_none)
        }
    }

    fun setUndoSendSeconds(seconds: Int) = viewModelScope.launch {
        settingsRepository.setUndoSendSeconds(seconds)
    }

    fun setAutoDeleteOtp(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoDeleteOtp(enabled)
        // Turning the option on should clear the backlog straight away rather than waiting
        // for the next message to arrive.
        if (enabled) {
            val removed = repository.purgeExpiredOtp()
            if (removed > 0) {
                _message.value = context.resources.getQuantityString(
                    R.plurals.otp_deleted_result, removed, removed,
                )
            }
        }
    }

    /**
     * Turns app lock on or off. Refuses to enable it without a PIN, because that would
     * leave the user with a lock they cannot open.
     */
    fun setAppLock(enabled: Boolean) = viewModelScope.launch {
        if (enabled && !settingsRepository.settings.first().hasPin) {
            _message.value = context.getString(R.string.settings_pin_required)
            return@launch
        }
        settingsRepository.setAppLockEnabled(enabled)
    }

    fun setBiometricUnlock(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBiometricUnlock(enabled)
    }

    fun setPin(pin: String) = viewModelScope.launch {
        if (pin.length !in MIN_PIN..MAX_PIN || !pin.all(Char::isDigit)) {
            _message.value = context.getString(R.string.settings_pin_invalid)
            return@launch
        }
        settingsRepository.setPin(pin)
        _message.value = context.getString(R.string.settings_change_pin)
    }

    /** Used by the lock screen; exposed here so the screen has no repository dependency. */
    suspend fun verifyPin(pin: String): Boolean = settingsRepository.verifyPin(pin)

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val MIN_PIN = 4
        const val MAX_PIN = 6
    }
}
