package com.sms.messages.ui.viewmodel

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sms.messages.SmsApp

/** Builds every screen ViewModel from the application graph held by [SmsApp]. */
object VmFactory {

    val Factory = viewModelFactory {
        initializer { ConversationsViewModel(app()) }
        initializer { ChatViewModel(app(), createSavedStateHandle()) }
        initializer { ComposeMessageViewModel(app(), createSavedStateHandle()) }
        initializer { SearchViewModel(app()) }
        initializer { TemplatesViewModel(app()) }
        initializer { ScheduledViewModel(app()) }
        initializer { BlockedViewModel(app()) }
        initializer { SettingsViewModel(app()) }
    }

    private fun CreationExtras.app(): SmsApp = this[APPLICATION_KEY] as SmsApp
}
