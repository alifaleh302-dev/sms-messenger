package com.sms.messages

import android.app.Application
import android.content.Context
import com.sms.messages.data.AppDatabase
import com.sms.messages.data.ContactResolver
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import com.sms.messages.notifications.Notifications
import com.sms.messages.sms.ScheduledMessageScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: SmsRepository by lazy { SmsRepository(this, database) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val contacts: ContactResolver by lazy { ContactResolver(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.createChannels(this)
        applicationScope.launch { ScheduledMessageScheduler.rescheduleAll(this@SmsApp) }
    }

    companion object {
        lateinit var instance: SmsApp
            private set

        fun from(context: Context): SmsApp = context.applicationContext as SmsApp
    }
}
