package com.sms.messages.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Notifications {

    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_SENDING = "sending"

    const val GROUP_KEY = "com.sms.messages.MESSAGES"

    /** Notification id is derived from the conversation so newer messages replace older ones. */
    fun idForConversation(conversationId: Long): Int = (conversationId % Int.MAX_VALUE).toInt()

    const val SUMMARY_ID = 0x5A17

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(com.sms.messages.R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.sms.messages.R.string.notification_channel_messages_desc)
            enableLights(true)
            enableVibration(true)
            setSound(sound, attributes)
        }

        val sending = NotificationChannel(
            CHANNEL_SENDING,
            context.getString(com.sms.messages.R.string.notification_channel_sending),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(com.sms.messages.R.string.notification_channel_sending_desc)
            setSound(null, null)
        }

        manager.createNotificationChannels(listOf(messages, sending))
    }
}
