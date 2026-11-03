package com.sms.messages.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.sms.messages.data.MessageEntity

/**
 * Wraps [SmsManager] so that the rest of the app never has to think about SIM slots,
 * multipart splitting or PendingIntent wiring.
 */
class SmsSender(private val context: Context) {

    /** Splits the message and hands every part to the platform, reporting progress per part. */
    fun send(message: MessageEntity, subscriptionId: Int, requestDeliveryReport: Boolean): Result<Unit> {
        val manager = resolveManager(subscriptionId)
            ?: return Result.failure(IllegalStateException("No SMS capable subscription"))

        val parts = runCatching { manager.divideMessage(message.body) }.getOrNull()
            ?: return Result.failure(IllegalStateException("Unable to split message"))

        val sentIntents = ArrayList<PendingIntent>(parts.size)
        val deliveryIntents = ArrayList<PendingIntent>(parts.size)
        val totalParts = parts.size

        for (index in parts.indices) {
            val partId = "$index/$totalParts"
            sentIntents += statusIntent(
                SmsStatusReceiver.ACTION_SENT,
                message.id,
                index,
                totalParts,
                partId,
                delivery = false
            )
            deliveryIntents += statusIntent(
                SmsStatusReceiver.ACTION_DELIVERED,
                message.id,
                index,
                totalParts,
                partId,
                delivery = true
            )
        }

        return runCatching {
            if (parts.size == 1) {
                manager.sendTextMessage(
                    message.address,
                    null,
                    parts[0],
                    sentIntents[0],
                    if (requestDeliveryReport) deliveryIntents[0] else null
                )
            } else {
                manager.sendMultipartTextMessage(
                    message.address,
                    null,
                    parts,
                    sentIntents,
                    if (requestDeliveryReport) deliveryIntents else null
                )
            }
        }
    }

    /** Persists the parts and status codes so the UI can resolve them after a process death. */
    fun partCount(body: String, subscriptionId: Int): Int {
        val manager = resolveManager(subscriptionId) ?: return 1
        return runCatching { manager.divideMessage(body).size }.getOrDefault(1)
    }

    fun hasTelephony(): Boolean = runCatching {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)
    }.getOrDefault(false)

    private fun resolveManager(subscriptionId: Int): SmsManager? = runCatching {
        if (subscriptionId >= 0 && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }
    }.onFailure { Log.w(TAG, "Failed to resolve SmsManager for sub $subscriptionId", it) }
        .getOrNull()

    private fun statusIntent(
        action: String,
        messageId: Long,
        index: Int,
        total: Int,
        partId: String,
        delivery: Boolean,
    ): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, index)
            putExtra(SmsStatusReceiver.EXTRA_PART_COUNT, total)
            putExtra(SmsStatusReceiver.EXTRA_PART_ID, partId)
            putExtra(SmsStatusReceiver.EXTRA_DELIVERY, delivery)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = (messageId.hashCode() * 31 + index * 7 + if (delivery) 1 else 0)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    companion object {
        private const val TAG = "SmsSender"
    }
}
