package com.sms.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sms.messages.data.SmsRepository
import kotlinx.coroutines.flow.first

/**
 * Arms [AlarmManager] for every pending scheduled message. Exact alarms are used when the
 * platform grants them and fall back to inexact otherwise, so a missing permission never
 * silently drops a scheduled message.
 */
object ScheduledMessageScheduler {

    private const val TAG = "ScheduledScheduler"

    fun schedule(context: Context, id: Long, atMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, id)
        val canExact = canScheduleExact(alarmManager)
        runCatching {
            when {
                canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)

                else -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        }.onFailure { Log.w(TAG, "Failed to arm alarm for scheduled message $id", it) }
    }

    fun cancel(context: Context, id: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(pendingIntent(context, id)) }
    }

    /** Re-arms every pending message; called at boot and after an app update. */
    suspend fun rescheduleAll(context: Context, repository: SmsRepository) {
        val pending = repository.observeScheduled().first()
        val now = System.currentTimeMillis()
        for (item in pending) {
            if (item.scheduledAt <= now) {
                ScheduledSendReceiver.dispatchNow(context, item.id)
            } else {
                schedule(context, item.id, item.scheduledAt)
            }
        }
        Log.d(TAG, "Re-armed ${pending.size} scheduled messages")
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ScheduledSendReceiver::class.java).apply {
            action = ScheduledSendReceiver.ACTION_SEND_SCHEDULED
            putExtra(ScheduledSendReceiver.EXTRA_SCHEDULED_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
