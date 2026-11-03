package com.sms.messages.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Confirms that this app is able to act as the default SMS application when the system
 * asks. Without this receiver the role request is rejected.
 */
class DefaultSmsRoleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED) return
        val isDefault = intent.getBooleanExtra(
            Telephony.Sms.Intents.EXTRA_IS_DEFAULT_SMS_APP, false
        )
        Log.i(TAG, "Default SMS package changed, isDefault=$isDefault")
    }

    companion object {
        private const val TAG = "DefaultSmsRoleReceiver"

        /** The component the platform uses to verify the default SMS role holder. */
        fun component(context: Context): ComponentName =
            ComponentName(context, DefaultSmsRoleReceiver::class.java)

        const val RESULT_OK = Activity.RESULT_OK
    }
}
