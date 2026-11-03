package com.sms.messages.sms

import android.content.BroadcastReceiver
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import com.sms.messages.di.ApplicationScope
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arms scheduled messages after a reboot or an app update. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: SmsRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope


    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pendingResult = goAsync()
                applicationScope.launch {
                    try {
                        ScheduledMessageScheduler.rescheduleAll(context, repository)
                        val settings = settingsRepository.settings.first()
                        repository.retryUnfinishedMessages(autoRetry = settings.autoRetry)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Boot reschedule failed", t)
                    } finally {
                        runCatching { pendingResult.finish() }
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
