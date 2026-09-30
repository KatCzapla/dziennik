package pl.usagi.edziennik.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import pl.usagi.edziennik.ui.attendance.AttendanceRoute
import pl.usagi.edziennik.ui.grades.GradesRoute
import pl.usagi.edziennik.ui.grades.GradesSubjectRoute
import pl.usagi.edziennik.ui.grades.GradesViewModel
import pl.usagi.edziennik.ui.login.LoginRoute
import pl.usagi.edziennik.ui.messages.MessagesRoute
import pl.usagi.edziennik.ui.payments.PaymentsRoute
import pl.usagi.edziennik.ui.schooltasks.SchoolTasksRoute
import pl.usagi.edziennik.ui.timetable.TimetableRoute

@Composable
fun EDziennikNavHost(
    notificationRoute: String?,
    hasActiveStudent: Boolean,
    onNotificationRouteConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    LaunchedEffect(notificationRoute, hasActiveStudent) {
        if (notificationRoute != null && hasActiveStudent) {
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
            )
        }
        composable("timetable") {
            TimetableRoute(
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable("attendance") {
            AttendanceRoute(
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(
            GRADES_ROUTE,
            arguments = optionalArguments("periodId", "subjectPosition", "columnId", "gradeIndex"),
        ) {
            GradesRoute(
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
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable("messages") {
            MessagesRoute(
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable("payments") {
            PaymentsRoute(onBack = { navController.popBackStack() })
        }
    }
}

private fun optionalArguments(vararg names: String) = names.map { name ->
    navArgument(name) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }
}
