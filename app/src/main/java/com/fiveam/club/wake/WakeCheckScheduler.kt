package com.fiveam.club.wake

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fiveam.club.util.ExactAlarms

class WakeCheckScheduler(private val context: Context) {
    fun schedule(sessionId: Long, at: Long) {
        ExactAlarms.setExactWake(context, at, pending(sessionId, PendingIntent.FLAG_UPDATE_CURRENT)!!)
    }

    fun cancel(sessionId: Long) {
        ExactAlarms.cancel(context, pending(sessionId, PendingIntent.FLAG_NO_CREATE))
    }

    private fun pending(sessionId: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, WakeCheckReceiver::class.java)
            .setAction(WakeCheckReceiver.ACTION_CHECK)
            .setData(Uri.parse("fiveam://wake/$sessionId"))
            .putExtra(WakeCheckReceiver.EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
