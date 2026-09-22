package pl.usagi.edziennik.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.usagi.edziennik.ui.attendance.AttendanceRoute
import pl.usagi.edziennik.ui.grades.GradesRoute
import pl.usagi.edziennik.ui.grades.GradesSubjectRoute
import pl.usagi.edziennik.ui.grades.GradesViewModel
import pl.usagi.edziennik.ui.login.LoginRoute
import pl.usagi.edziennik.ui.messages.MessagesRoute
import pl.usagi.edziennik.ui.schooltasks.SchoolTasksRoute
import pl.usagi.edziennik.ui.timetable.TimetableRoute

@Composable
fun EDziennikNavHost() {
    val navController = rememberNavController()

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
                onOpenMessages = {
                    navController.navigate("messages")
                },
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
        composable("grades") {
            GradesRoute(
                onBack = {
                    navController.popBackStack()
                },
                onOpenSubject = { position ->
                    navController.navigate("grades/subject/$position")
                },
            )
        }
        composable("grades/subject/{position}") { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry("grades")
            }
            val viewModel: GradesViewModel = hiltViewModel(parentEntry)
            GradesSubjectRoute(
                subjectPosition = backStackEntry.arguments?.getString("position")?.toIntOrNull() ?: -1,
                onBack = {
                    navController.popBackStack()
                },
                viewModel = viewModel,
            )
        }
        composable("schoolTasks") {
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
    }
}
