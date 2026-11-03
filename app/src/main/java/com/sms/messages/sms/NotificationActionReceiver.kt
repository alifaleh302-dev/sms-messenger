package com.sms.messages.sms

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import com.sms.messages.di.ApplicationScope
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.sms.messages.R
import com.sms.messages.notifications.MessageNotifier
import com.sms.messages.notifications.Notifications
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Handles the inline reply and "mark as read" actions attached to message notifications. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: SmsRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    @Inject lateinit var notifier: MessageNotifier


    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1L)
        if (conversationId <= 0) return

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
                applicationScope.launch {
                    try {
                        val settings = settingsRepository.settings.first()
                        val target = address ?: repository.conversationAddress(conversationId)
                        if (target.isNullOrBlank()) return@launch
                        repository.sendMessage(
                            address = target,
                            body = text,
                            subscriptionId = settings.defaultSubscriptionId,
                            deliveryReport = settings.deliveryReports,
                        )
                        notifier.cancel(conversationId)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Inline reply failed", t)
                    } finally {
                        runCatching { pendingResult.finish() }
                    }
                }
            }

            ACTION_MARK_READ -> {
                applicationScope.launch {
                    try {
                        repository.markConversationRead(conversationId)
                        context.getSystemService(NotificationManager::class.java)
                            ?.cancel(Notifications.idForConversation(conversationId))
                    } catch (t: Throwable) {
                        Log.e(TAG, "Mark as read failed", t)
                    } finally {
                        runCatching { pendingResult.finish() }
                    }
                }
            }

            ACTION_COPY_CODE -> {
                val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
                if (code.isEmpty()) {
                    pendingResult.finish()
                    return
                }
                applicationScope.launch {
                    try {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, code))
                        // Copying the code means the message has been dealt with, so the
                        // notification goes away and the thread stops showing as unread.
                        repository.markConversationRead(conversationId)
                        notifier.cancel(conversationId)
                        Toast.makeText(context, R.string.toast_code_copied, Toast.LENGTH_SHORT).show()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Copying the code failed", t)
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
        const val ACTION_COPY_CODE = "com.sms.messages.NOTIFICATION_COPY_CODE"

        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_CODE = "code"
        const val KEY_REPLY_TEXT = "reply_text"
        const val CLIP_LABEL = "verification code"
    }
}
