package com.sms.messages.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.sms.messages.MainActivity
import com.sms.messages.R
import com.sms.messages.SmsApp
import com.sms.messages.sms.NotificationActionReceiver
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Builds and posts the per-conversation notification, including the inline reply action. */
class MessageNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun notifyIncoming(conversationId: Long, address: String, body: String) {
        val app = SmsApp.from(context)
        scope.launch {
            val settings = app.settings.settings.first()
            if (!settings.notificationsEnabled) return@launch
            // A muted conversation still records the message, it just must not alert.
            val muted = app.repository.isConversationMuted(conversationId)
            val name = app.repository.contactName(address)
            post(
                conversationId = conversationId,
                address = address,
                name = name,
                body = body,
                vibrate = settings.vibrate && !muted,
                sound = settings.soundEnabled && !muted,
                silent = muted,
            )
        }
    }

    private suspend fun post(
        conversationId: Long,
        address: String,
        name: String?,
        body: String,
        vibrate: Boolean,
        sound: Boolean,
        silent: Boolean = false,
    ) {
        val title = name ?: PhoneNumbers.format(address).ifEmpty { address }
        val content = buildContentIntent(conversationId)

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
        }
        val replyPending = PendingIntent.getBroadcast(
            context, conversationId.toInt(), replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.message_hint))
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_send, context.getString(R.string.notification_reply), replyPending
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()

        val readIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
        }
        val readPending = PendingIntent.getBroadcast(
            context, (conversationId + 1).toInt(), readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(
            R.drawable.ic_done_all, context.getString(R.string.notification_mark_read), readPending
        ).build()

        val person = Person.Builder().setName(title).setImportant(true).build()

        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.MessagingStyle(
                Person.Builder().setName(context.getString(R.string.notification_me)).build()
            )
                    .addMessage(body, System.currentTimeMillis(), person)
            )
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(Notifications.GROUP_KEY)
            .setOnlyAlertOnce(false)
            .addAction(replyAction)
            .addAction(readAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (vibrate) builder.setVibrate(longArrayOf(0, 250, 150, 250))
        if (!sound || silent) builder.setSilent(true)

        runCatching {
            manager.notify(Notifications.idForConversation(conversationId), builder.build())
            postSummary()
        }
    }

    private fun postSummary() {
        val summary = NotificationCompat.Builder(context, Notifications.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(context.getString(R.string.notification_new_message))
            .setContentText(context.getString(R.string.app_name))
            .setGroup(Notifications.GROUP_KEY)
            .setGroupSummary(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(-1L))
            .build()
        runCatching { manager.notify(Notifications.SUMMARY_ID, summary) }
    }

    fun cancel(conversationId: Long) {
        runCatching { manager.cancel(Notifications.idForConversation(conversationId)) }
    }

    fun cancelAll() {
        runCatching {
            val app = SmsApp.from(context)
            scope.launch {
                val all = app.repository.observeConversations().first()
                all.forEach { manager.cancel(Notifications.idForConversation(it.id)) }
            }
            manager.cancel(Notifications.SUMMARY_ID)
        }
    }

    fun notifySendFailure(messageId: Long, address: String) {
        val title = PhoneNumbers.format(address).ifEmpty { address }
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_SENDING)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle(context.getString(R.string.message_failed))
            .setContentText(title)
            .setAutoCancel(true)
        runCatching { manager.notify(messageId.toInt(), builder.build()) }
    }

    private fun buildContentIntent(conversationId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, conversationId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
