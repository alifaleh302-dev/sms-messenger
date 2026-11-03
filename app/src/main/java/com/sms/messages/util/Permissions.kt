package com.sms.messages.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Small helpers around the runtime permissions the app depends on. */
object Permissions {

    val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
    )

    val NOTIFICATION_PERMISSION = arrayOf(Manifest.permission.POST_NOTIFICATIONS)

    fun hasSms(context: Context): Boolean = SMS_PERMISSIONS.all { granted(context, it) }

    fun hasContacts(context: Context): Boolean = granted(context, Manifest.permission.READ_CONTACTS)

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
