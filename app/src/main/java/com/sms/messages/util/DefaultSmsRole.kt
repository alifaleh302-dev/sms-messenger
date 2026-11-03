package com.sms.messages.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/** Helpers for the default SMS application role. */
object DefaultSmsRole {

    fun isDefault(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        } else {
            false
        }

    /**
     * Opens the system dialog that offers this app as the default SMS application. On
     * Android 10+ the role manager requires an activity that declares the SMS_DELIVER
     * intent filter, which MainActivity does.
     */
    fun request(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                // Fall back to the settings screen when the direct intent is not resolvable.
                val fallback = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(fallback) }
            }
    }
}
