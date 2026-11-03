package com.sms.messages.sms

import android.app.Service
import dagger.hilt.android.AndroidEntryPoint
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Placeholder for MMS content retrieval. The download itself requires the carrier MMSC
 * configuration; this service keeps the manifest contract complete and logs the request so
 * a future implementation has a defined entry point.
 */
@AndroidEntryPoint
class MmsDownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "MMS download requested (uri=${intent?.data})")
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "MmsDownloadService"
    }
}
