package com.fiveam.club.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fiveam.club.ui.screens.AlarmEditScreen
import com.fiveam.club.ui.screens.AlarmsScreen
import com.fiveam.club.ui.screens.BedtimeScreen
import com.fiveam.club.ui.screens.CoachScreen
import com.fiveam.club.ui.screens.DiagnosticsScreen
import com.fiveam.club.ui.screens.GoalsScreen
import com.fiveam.club.ui.screens.HomeScreen
import com.fiveam.club.ui.screens.ProgressScreen
import com.fiveam.club.ui.screens.ReadinessScreen
import com.fiveam.club.ui.screens.RoutineEditScreen
import com.fiveam.club.ui.screens.RoutineRunScreen
import com.fiveam.club.ui.screens.SettingsScreen
import com.fiveam.club.ui.screens.SleepDiaryScreen
import com.fiveam.club.ui.screens.TaskEditScreen
import com.fiveam.club.ui.screens.TasksScreen
import com.fiveam.club.ui.screens.WakeConfirmScreen
import com.fiveam.club.ui.screens.WeeklyReportScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "الرئيسية", Icons.Filled.Home),
    Tab("alarms", "المنبه", Icons.Filled.Alarm),
    Tab("tasks", "المهام", Icons.Filled.Checklist),
    Tab("progress", "التقدم", Icons.Filled.Insights),
    Tab("coach", "المدرب", Icons.Filled.Lightbulb),
)

@Composable
fun AppNav(pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            nav.navigate(pendingRoute)
            onRouteConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            val back: () -> Unit = { nav.popBackStack() }
            val go: (String) -> Unit = { route -> nav.navigate(route) }
            composable("home") { HomeScreen(navigate = go) }
            composable("alarms") { AlarmsScreen(navigate = go) }
            composable("tasks") { TasksScreen(navigate = go) }
            composable("progress") { ProgressScreen(navigate = go) }
            composable("coach") { CoachScreen(navigate = go) }

            composable("alarm/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                AlarmEditScreen(id = it.arguments?.getLong("id") ?: 0L, onBack = back)
            }
            composable("task/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                TaskEditScreen(id = it.arguments?.getLong("id") ?: 0L, onBack = back)
            }
            composable(
                "wake/{id}?confirm={confirm}",
                listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("confirm") { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                WakeConfirmScreen(
                    sessionId = it.arguments?.getLong("id") ?: 0L,
                    autoConfirm = it.arguments?.getBoolean("confirm") ?: false,
                    navigate = go,
                    onDone = { nav.switchTab("home") },
                )
            }
            composable(
                "routine_run/{id}?session={session}",
                listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("session") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                RoutineRunScreen(
                    routineId = it.arguments?.getLong("id") ?: 0L,
                    sessionId = it.arguments?.getLong("session")?.takeIf { s -> s > 0 },
                    onDone = { nav.switchTab("home") },
                )
            }
            composable("routines") { RoutineEditScreen(onBack = back, navigate = go) }
            composable("readiness") { ReadinessScreen(onBack = back) }
            composable("bedtime") { BedtimeScreen(onBack = back, navigate = go) }
            composable("sleep") { SleepDiaryScreen(onBack = back) }
            composable("goals") { GoalsScreen(onBack = back) }
            composable("report") { WeeklyReportScreen(onBack = back) }
            composable("settings") { SettingsScreen(onBack = back, navigate = go) }
            composable("diagnostics") { DiagnosticsScreen(onBack = back) }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
