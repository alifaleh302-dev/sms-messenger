package com.sms.messages.sms

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.sms.messages.SmsApp
import kotlinx.coroutines.launch

/**
 * Handles "quick response" requests (RESPOND_VIA_MESSAGE) so the platform can send a reply
 * without launching the full UI, for example from a call notification.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESPOND_VIA_MESSAGE) {
            val uri: Uri? = intent.data
            val address = uri?.schemeSpecificPart
                ?: intent.getStringArrayExtra("addresses")?.firstOrNull()
            val body = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!address.isNullOrBlank() && !body.isNullOrBlank()) {
                val app = SmsApp.from(this)
                app.applicationScope.launch {
                    runCatching {
                        app.repository.sendMessage(address, body, -1, deliveryReport = false)
                    }.onFailure { Log.e(TAG, "Headless reply failed", it) }
                }
            } else {
                Log.w(TAG, "Ignoring respond-via-message with missing address or body")
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "HeadlessSmsSendService"
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
    }
}
