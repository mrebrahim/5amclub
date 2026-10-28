package com.fiveam.club.alarm.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fiveam.club.FiveAmApp
import kotlinx.coroutines.launch

/** Restores every schedule after reboot, app update, clock/timezone change (ALM-03, TSK-13). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as FiveAmApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.diag.log("boot", "received ${intent.action}")
                container.restoreAfterSystemEvent()
            } finally {
                pending.finish()
            }
        }
    }
}
