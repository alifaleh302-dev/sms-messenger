package com.sms.messages.sms

import android.app.Activity
import android.content.BroadcastReceiver
import com.sms.messages.data.SmsRepository
import com.sms.messages.notifications.MessageNotifier
import com.sms.messages.di.ApplicationScope
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Collects the per-part SMS_SENT / SMS_DELIVERED results and only flips the message state
 * once every part of a multipart message has reported, so a partially delivered message is
 * not shown as sent.
 */
@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: SmsRepository

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    @Inject lateinit var notifier: MessageNotifier


    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0) return

        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val partCount = intent.getIntExtra(EXTRA_PART_COUNT, 1).coerceAtLeast(1)
        val result = resultCode.takeIf { it != 0 } ?: Activity.RESULT_OK
        val delivered = action == ACTION_DELIVERED

        val pendingResult = goAsync()

        applicationScope.launch {
            try {
                if (delivered) {
                    val results = deliveredPartResults
                        .getOrPut(messageId) { java.util.concurrent.ConcurrentHashMap() }
                    results[partIndex] = result
                    if (results.size >= partCount) {
                        if (results.values.all { it == Activity.RESULT_OK }) {
                            repository.applyDeliveredResult(messageId)
                        }
                        deliveredPartResults.remove(messageId)
                    }
                } else {
                    val results = sentPartResults
                        .getOrPut(messageId) { java.util.concurrent.ConcurrentHashMap() }
                    results[partIndex] = result
                    if (results.size >= partCount) {
                        val success = results.values.all { it == Activity.RESULT_OK }
                        val errorCode = results.values.firstOrNull { it != Activity.RESULT_OK }
                        repository.applySentResult(messageId, success, errorCode)
                        sentPartResults.remove(messageId)
                        if (!success) {
                            val message = repository.messageById(messageId)
                            if (message != null) {
                                notifier.notifySendFailure(messageId, message.address)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to apply SMS result for $messageId", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        private const val TAG = "SmsStatusReceiver"

        const val ACTION_SENT = "com.sms.messages.SMS_SENT"
        const val ACTION_DELIVERED = "com.sms.messages.SMS_DELIVERED"

        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_PART_COUNT = "part_count"
        const val EXTRA_PART_ID = "part_id"
        const val EXTRA_DELIVERY = "delivery"

        private val sentPartResults = java.util.concurrent.ConcurrentHashMap<Long, MutableMap<Int, Int>>()
        private val deliveredPartResults = java.util.concurrent.ConcurrentHashMap<Long, MutableMap<Int, Int>>()
    }
}
