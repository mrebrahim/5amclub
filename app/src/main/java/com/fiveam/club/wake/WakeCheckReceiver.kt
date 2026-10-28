package com.fiveam.club.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fiveam.club.FiveAmApp
import kotlinx.coroutines.launch

class WakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (id < 0) return
        val container = (context.applicationContext as FiveAmApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_CHECK -> container.sessions.onWakeCheckDue(id)
                    ACTION_MORE_TIME -> container.sessions.needMoreTime(id)
                    ACTION_BACK_TO_SLEEP -> container.sessions.reportBackToSleep(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "com.fiveam.club.action.WAKE_CHECK"
        const val ACTION_MORE_TIME = "com.fiveam.club.action.WAKE_MORE_TIME"
        const val ACTION_BACK_TO_SLEEP = "com.fiveam.club.action.WAKE_BACK_TO_SLEEP"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
