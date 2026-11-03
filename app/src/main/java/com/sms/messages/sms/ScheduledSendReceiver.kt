package com.sms.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sms.messages.SmsApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fires when a scheduled message is due. */
class ScheduledSendReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_SCHEDULED) return
        val id = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        if (id <= 0) return

        val app = SmsApp.from(context)
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                deliver(app, id)
            } catch (t: Throwable) {
                Log.e(TAG, "Scheduled delivery failed for $id", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private suspend fun deliver(app: SmsApp, id: Long) {
        val item = app.repository.scheduledById(id) ?: return
        if (item.completed) return
        val settings = app.settings.settings.first()
        val subscriptionId = if (item.subscriptionId >= 0) {
            item.subscriptionId
        } else {
            settings.defaultSubscriptionId
        }
        app.repository.sendMessage(
            address = item.address,
            body = item.body,
            subscriptionId = subscriptionId,
            deliveryReport = settings.deliveryReports,
        )
        app.repository.completeScheduled(id)
        Log.i(TAG, "Delivered scheduled message $id")
    }

    companion object {
        private const val TAG = "ScheduledSendReceiver"

        const val ACTION_SEND_SCHEDULED = "com.sms.messages.SEND_SCHEDULED"
        const val EXTRA_SCHEDULED_ID = "scheduled_id"

        /** Used when a scheduled time already passed while the device was off. */
        fun dispatchNow(app: SmsApp, id: Long) {
            ScheduledMessageScheduler.schedule(app, id, System.currentTimeMillis())
        }
    }
}
