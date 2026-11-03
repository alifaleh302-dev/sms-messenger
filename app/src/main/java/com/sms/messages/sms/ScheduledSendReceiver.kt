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

/** Fires when a scheduled message is due. */
@AndroidEntryPoint
class ScheduledSendReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: SmsRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope


    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_SCHEDULED) return
        val id = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        if (id <= 0) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                deliver(id)
            } catch (t: Throwable) {
                Log.e(TAG, "Scheduled delivery failed for $id", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private suspend fun deliver(id: Long) {
        val item = repository.scheduledById(id) ?: return
        if (item.completed) return
        val settings = settingsRepository.settings.first()
        val subscriptionId = if (item.subscriptionId >= 0) {
            item.subscriptionId
        } else {
            settings.defaultSubscriptionId
        }
        repository.sendMessage(
            address = item.address,
            body = item.body,
            subscriptionId = subscriptionId,
            deliveryReport = settings.deliveryReports,
        )
        repository.completeScheduled(id)
        Log.i(TAG, "Delivered scheduled message $id")
    }

    companion object {
        private const val TAG = "ScheduledSendReceiver"

        const val ACTION_SEND_SCHEDULED = "com.sms.messages.SEND_SCHEDULED"
        const val EXTRA_SCHEDULED_ID = "scheduled_id"

        /** Used when a scheduled time already passed while the device was off. */
        fun dispatchNow(context: Context, id: Long) {
            ScheduledMessageScheduler.schedule(context, id, System.currentTimeMillis())
        }
    }
}
