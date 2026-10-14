package pl.usagi.edziennik.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import pl.usagi.edziennik.ui.attendance.AttendanceRoute
import pl.usagi.edziennik.ui.attendance.AttendanceExcuseRoute
import pl.usagi.edziennik.ui.attendance.AttendanceViewModel
import pl.usagi.edziennik.ui.grades.GradesRoute
import pl.usagi.edziennik.ui.grades.GradesSubjectRoute
import pl.usagi.edziennik.ui.grades.GradesViewModel
import pl.usagi.edziennik.ui.login.LoginRoute
import pl.usagi.edziennik.ui.messages.MessagesRoute
import pl.usagi.edziennik.ui.payments.PaymentsRoute
import pl.usagi.edziennik.ui.schooltasks.SchoolTasksRoute
import pl.usagi.edziennik.ui.timetable.TimetableRoute
import pl.usagi.edziennik.ui.settings.SettingsRoute
import pl.usagi.edziennik.ui.settings.SyncDiagnosticsRoute
import pl.usagi.edziennik.ui.login.LoginViewModel

@Composable
fun EDziennikNavHost(
    notificationRoute: String?,
    hasActiveStudent: Boolean,
    onNotificationRouteConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    var logoutPending by rememberSaveable { mutableStateOf(false) }
    val onOpenSettings: () -> Unit = {
        if (hasActiveStudent && !logoutPending) navController.openSettings()
    }

    LaunchedEffect(hasActiveStudent) {
        if (!hasActiveStudent) navController.clearAuthenticatedBackStack()
    }

    LaunchedEffect(notificationRoute, hasActiveStudent, logoutPending) {
        if (notificationRoute != null && hasActiveStudent && !logoutPending) {
            if (notificationRoute != "home") navController.navigate(notificationRoute)
            else navController.popBackStack("home", false)
            onNotificationRouteConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            LoginRoute(
                onOpenTimetable = {
                    navController.navigate("timetable")
                },
                onOpenAttendance = {
                    navController.navigate("attendance")
                },
                onOpenGrades = {
                    navController.navigate("grades")
                },
                onOpenSchoolTasks = {
                    navController.navigate("schoolTasks")
                },
                onOpenGrade = { target -> navController.navigate(target.route()) },
                onOpenTask = { target -> navController.navigate(target.route()) },
                onOpenMessages = {
                    navController.navigate("messages")
                },
                onOpenPayments = { navController.navigate("payments") },
                onOpenSettings = onOpenSettings,
                logoutPending = logoutPending,
                onLogoutFinished = { logoutPending = false },
            )
        }
        composable("timetable") {
            TimetableRoute(
                onOpenSettings = onOpenSettings,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable("attendance") {
            AttendanceRoute(
                onOpenSettings = onOpenSettings,
                onBack = {
                    navController.popBackStack()
                },
                onOpenExcuse = { navController.navigate("attendance/excuse") },
            )
        }
        composable("attendance/excuse") { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry("attendance")
            }
            val viewModel: AttendanceViewModel = hiltViewModel(parentEntry)
            AttendanceExcuseRoute(
                onOpenSettings = onOpenSettings,
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        composable(
            GRADES_ROUTE,
            arguments = optionalArguments("periodId", "subjectPosition", "columnId", "gradeIndex"),
        ) {
            GradesRoute(
                onOpenSettings = onOpenSettings,
                onBack = {
                    navController.popBackStack()
                },
                onOpenSubject = { position ->
                    navController.navigate("grades/subject/$position")
                },
                onOpenTarget = { target -> navController.navigate(target.subjectRoute()) },
            )
        }
        composable(
            "grades/subject/{position}?columnId={columnId}&gradeIndex={gradeIndex}",
            arguments = optionalArguments("columnId", "gradeIndex"),
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(GRADES_ROUTE)
            }
            val viewModel: GradesViewModel = hiltViewModel(parentEntry)
            GradesSubjectRoute(
                subjectPosition = backStackEntry.arguments?.getString("position")?.toIntOrNull() ?: -1,
                onBack = {
                    navController.popBackStack()
                },
                viewModel = viewModel,
                targetColumnId = backStackEntry.arguments?.getString("columnId")?.toIntOrNull(),
                targetGradeIndex = backStackEntry.arguments?.getString("gradeIndex")?.toIntOrNull(),
            )
        }
        composable(
            SCHOOL_TASKS_ROUTE,
            arguments = optionalArguments("taskId", "taskType", "taskDate"),
        ) {
            SchoolTasksRoute(
                onOpenSettings = onOpenSettings,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable("messages") {
            MessagesRoute(
                onOpenSettings = onOpenSettings,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable("payments") {
            PaymentsRoute(onBack = { navController.popBackStack() }, onOpenSettings = onOpenSettings)
        }
        composable("settings") { entry ->
            val homeEntry = remember(entry) { navController.getBackStackEntry("home") }
            val loginViewModel: LoginViewModel = hiltViewModel(homeEntry)
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = { navController.navigate("settings/diagnostics") },
                onLogout = {
                    logoutPending = true
                    loginViewModel.clearSession()
                    navController.clearAuthenticatedBackStack()
                },
                loginViewModel = loginViewModel,
            )
        }
        composable("settings/diagnostics") {
            SyncDiagnosticsRoute(onBack = { navController.popBackStack() })
        }
    }
}

internal fun NavHostController.clearAuthenticatedBackStack() = popBackStack("home", false)

internal fun NavHostController.openSettings() {
    navigate("settings") { launchSingleTop = true }
}

private fun optionalArguments(vararg names: String) = names.map { name ->
    navArgument(name) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }
}
