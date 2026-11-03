package com.sms.messages

import android.app.Application
import com.sms.messages.data.SmsRepository
import com.sms.messages.di.ApplicationScope
import com.sms.messages.notifications.Notifications
import com.sms.messages.sms.ScheduledMessageScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
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

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        applicationScope.launch { ScheduledMessageScheduler.rescheduleAll(this@SmsApp, repository) }
    }
}
