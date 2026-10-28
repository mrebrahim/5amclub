package com.fiveam.club.alarm.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fiveam.club.FiveAmApp
import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.ui.theme.FiveAmTheme

/** Full-screen alarm UI shown over the lock screen where the platform allows it (ALM-05). */
class AlarmActivity : ComponentActivity() {
    private val vm: AlarmViewModel by viewModels {
        val container = (application as FiveAmApp).container
        viewModelFactory { initializer { AlarmViewModel(container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vm.resolve(intent.sessionId())
        setContent {
            FiveAmTheme {
                AlarmRoot(vm = vm, onClose = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.resolve(intent.sessionId())
    }

    /**
     * AC-03: volume keys never snooze or dismiss. While this screen has focus we consume them,
     * which on most devices also keeps the alarm stream unchanged. With the screen off the system
     * handles them, which is why the service also restores the target level (ALM-07).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        val ringing = vm.session.value?.let { AlarmState.of(it.state).isRinging } == true
        if (isVolumeKey && ringing) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) vm.onVolumeKey()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun Intent.sessionId(): Long? = getLongExtra(EXTRA_SESSION_ID, -1L).takeIf { it > 0 }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"

        fun intent(context: Context, sessionId: Long?): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .apply { if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId) }
    }
}

