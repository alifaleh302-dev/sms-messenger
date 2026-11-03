package com.sms.messages.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.sms.messages.MainActivity
import com.sms.messages.R
import com.sms.messages.sms.NotificationActionReceiver
import com.sms.messages.util.PhoneNumbers
import com.sms.messages.util.VerificationCode
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import com.sms.messages.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts the per-conversation notification, including the inline reply action. */
@Singleton
class MessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SmsRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val manager = NotificationManagerCompat.from(context)

    fun notifyIncoming(conversationId: Long, address: String, body: String) {
        scope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.notificationsEnabled) return@launch
            // A muted conversation still records the message, it just must not alert.
            val muted = repository.isConversationMuted(conversationId)
            val name = repository.contactName(address)
            post(
                conversationId = conversationId,
                address = address,
                name = name,
                body = body,
                vibrate = settings.vibrate && !muted,
                sound = settings.soundEnabled && !muted,
                silent = muted,
                // Detected here as well as on arrival so the copy action is available for
                // messages that reached the database through the telephony import.
                code = VerificationCode.extract(body),
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
        code: String? = null,
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) return
        if (!manager.areNotificationsEnabled()) return
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

        // Only offered when a code was actually found, so the action never appears on an
        // ordinary message where it would have nothing to copy.
        val copyCodeAction: NotificationCompat.Action? = code?.let { value ->
            val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_COPY_CODE
                putExtra(NotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
                putExtra(NotificationActionReceiver.EXTRA_CODE, value)
            }
            val copyPending = PendingIntent.getBroadcast(
                context, (conversationId + 2).toInt(), copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_copy, context.getString(R.string.notification_copy_code), copyPending
            ).build()
        }

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

        // The copy action leads when a code is present: it is the reason the user is
        // looking at the notification at all.
        copyCodeAction?.let { builder.addAction(it) }

        if (vibrate) builder.setVibrate(longArrayOf(0, 250, 150, 250))
        if (!sound || silent) builder.setSilent(true)

        runCatching {
            manager.notify(Notifications.idForConversation(conversationId), builder.build())
            postSummary()
        }
    }

    private fun postSummary() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) return
        if (!manager.areNotificationsEnabled()) return
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
            scope.launch {
                val all = repository.observeConversations().first()
                all.forEach { manager.cancel(Notifications.idForConversation(it.id)) }
            }
            manager.cancel(Notifications.SUMMARY_ID)
        }
    }

    fun notifySendFailure(messageId: Long, address: String) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) return
        if (!manager.areNotificationsEnabled()) return
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
