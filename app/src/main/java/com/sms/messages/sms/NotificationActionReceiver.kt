package com.sms.messages.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.sms.messages.SmsApp
import com.sms.messages.notifications.MessageNotifier
import com.sms.messages.notifications.Notifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Handles the inline reply and "mark as read" actions attached to message notifications. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1L)
        if (conversationId <= 0) return

        val app = SmsApp.from(context)
        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) {
                    pendingResult.finish()
                    return
                }
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                app.applicationScope.launch {
                    try {
                        val settings = app.settings.settings.first()
                        val target = address ?: app.repository.conversationAddress(conversationId)
                        if (target.isNullOrBlank()) return@launch
                        app.repository.sendMessage(
                            address = target,
                            body = text,
                            subscriptionId = settings.defaultSubscriptionId,
                            deliveryReport = settings.deliveryReports,
                        )
                        MessageNotifier(context).cancel(conversationId)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Inline reply failed", t)
                    } finally {
                        runCatching { pendingResult.finish() }
                    }
                }
            }

            ACTION_MARK_READ -> {
                app.applicationScope.launch {
                    try {
                        app.repository.markConversationRead(conversationId)
                        context.getSystemService(NotificationManager::class.java)
                            ?.cancel(Notifications.idForConversation(conversationId))
                    } catch (t: Throwable) {
                        Log.e(TAG, "Mark as read failed", t)
                    } finally {
                        runCatching { pendingResult.finish() }
                    }
                }
            }

            else -> pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"

        const val ACTION_REPLY = "com.sms.messages.NOTIFICATION_REPLY"
        const val ACTION_MARK_READ = "com.sms.messages.NOTIFICATION_MARK_READ"

        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_ADDRESS = "address"
        const val KEY_REPLY_TEXT = "reply_text"
    }
}
