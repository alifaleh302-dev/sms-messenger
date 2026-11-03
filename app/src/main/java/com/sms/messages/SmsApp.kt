package com.sms.messages

import android.app.Application
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import com.sms.messages.di.ApplicationScope
import com.sms.messages.notifications.Notifications
import com.sms.messages.sms.ScheduledMessageScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The application entry point. Dependencies come from Hilt; this class only owns the work
 * that has to happen once per process.
 */
@HiltAndroidApp
class SmsApp : Application() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var repository: SmsRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        applicationScope.launch { ScheduledMessageScheduler.rescheduleAll(this@SmsApp, repository) }
        applicationScope.launch { runStartupMaintenance() }
    }

    /**
     * Housekeeping that has to survive the process being killed mid-task. Both sweeps are
     * idempotent, so running them on every start is safe and keeps the database from
     * growing without the user ever having to ask.
     */
    private suspend fun runStartupMaintenance() {
        repository.purgeExpiredTrash()
        if (settingsRepository.settings.first().autoDeleteOtp) {
            repository.purgeExpiredOtp()
        }
    }
}
