package com.sms.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sms.messages.SmsApp
import kotlinx.coroutines.launch

/** Re-arms scheduled messages after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val app = SmsApp.from(context)
                val pendingResult = goAsync()
                app.applicationScope.launch {
                    try {
                        ScheduledMessageScheduler.rescheduleAll(app)
                        app.repository.retryUnfinishedMessages()
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
