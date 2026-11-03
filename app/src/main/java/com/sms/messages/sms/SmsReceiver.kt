package com.sms.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sms.messages.SmsApp
import com.sms.messages.notifications.MessageNotifier
import kotlinx.coroutines.launch

/**
 * Receives inbound SMS. When this app holds the default-SMS role the platform sends
 * SMS_DELIVER; SMS_RECEIVED is also handled so the app still shows messages while it is
 * waiting for the role to be granted.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) return

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            ?: return
        if (messages.isEmpty()) return

        val subscriptionId = intent.getIntExtra("subscription", -1)
        val grouped = linkedMapOf<String, Pair<StringBuilder, Long>>()

        for (part in messages) {
            val address = part.displayOriginatingAddress ?: part.originatingAddress ?: continue
            val entry = grouped.getOrPut(address) { StringBuilder() to part.timestampMillis }
            entry.first.append(part.displayMessageBody ?: part.messageBody ?: "")
        }

        if (grouped.isEmpty()) return

        val app = SmsApp.from(context)
        val pendingResult = goAsync()
        app.applicationScope.launch {
            val notifier = MessageNotifier(context)
            try {
                for ((address, payload) in grouped) {
                    val (bodyBuilder, timestamp) = payload
                    val body = bodyBuilder.toString()
                    val id = app.repository.persistIncoming(
                        address = address,
                        body = body,
                        timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
                        subscriptionId = subscriptionId,
                    )
                    if (id > 0) {
                        val conversation = app.repository.ensureConversation(address)
                        notifier.notifyIncoming(conversation.id, address, body)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to persist inbound SMS", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
