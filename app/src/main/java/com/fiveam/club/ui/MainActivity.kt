package com.fiveam.club.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.fiveam.club.FiveAmApp
import com.fiveam.club.ui.screens.OnboardingScreen
import com.fiveam.club.ui.theme.FiveAmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A deep link from a notification, consumed by the nav host. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FiveAmApp).container
        handle(intent)
        // Opening the app is a cheap, safe moment to make sure every schedule is registered.
        lifecycleScope.launch {
            container.alarms.rescheduleAll()
            container.taskReminders.rescheduleAll()
        }
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings ?: return@setContent
            FiveAmTheme(mode = s.themeMode) {
                if (!s.onboardingDone) {
                    OnboardingScreen(onDone = {})
                } else {
                    AppNav(pendingRoute = pendingRoute.value, onRouteConsumed = { pendingRoute.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val session = intent.getLongExtra(EXTRA_WAKE_SESSION, -1L)
        if (session > 0) {
            val confirm = intent.getBooleanExtra(EXTRA_WAKE_CONFIRM, false)
            pendingRoute.value = "wake/$session?confirm=$confirm"
            return
        }
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }

    companion object {
        private const val EXTRA_ROUTE = "route"
        private const val EXTRA_WAKE_SESSION = "wake_session"
        private const val EXTRA_WAKE_CONFIRM = "wake_confirm"

        fun routeIntent(context: Context, route: String): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ROUTE, route)

        fun wakeIntent(context: Context, sessionId: Long, confirm: Boolean): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setAction(if (confirm) "confirm" else "open")
                .putExtra(EXTRA_WAKE_SESSION, sessionId)
                .putExtra(EXTRA_WAKE_CONFIRM, confirm)
    }
}
