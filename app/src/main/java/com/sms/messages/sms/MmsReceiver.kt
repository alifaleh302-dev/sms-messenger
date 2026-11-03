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
 * Receives WAP push messages. For the default SMS app the payload of type
 * application/vnd.wap.mms-message carries a notification that points at the MMS content,
 * which is then downloaded by [MmsDownloadService].
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION
        ) return

        val push = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()
        if (push.isNullOrEmpty()) {
            Log.d(TAG, "WAP push without parseable payload; ignoring")
            return
        }

        val app = SmsApp.from(context)
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                for (message in push) {
                    val body = message.displayMessageBody ?: message.messageBody ?: continue
                    // The payload is a binary MMS notification; surface a placeholder entry
                    // until the download service resolves the real content.
                    val address = message.displayOriginatingAddress
                        ?: message.originatingAddress
                        ?: continue
                    val id = app.repository.persistIncoming(
                        address = address,
                        body = body,
                        timestamp = System.currentTimeMillis(),
                        subscriptionId = intent.getIntExtra("subscription", -1),
                    )
                    if (id > 0) {
                        val conversation = app.repository.ensureConversation(address)
                        MessageNotifier(context).notifyIncoming(conversation.id, address, body)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle WAP push", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private companion object {
        const val TAG = "MmsReceiver"
    }
}
