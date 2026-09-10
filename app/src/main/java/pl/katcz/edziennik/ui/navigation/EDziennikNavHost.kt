package pl.katcz.edziennik.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.katcz.edziennik.ui.login.LoginRoute
import pl.katcz.edziennik.ui.timetable.TimetableRoute

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
            )
        }
        composable("timetable") {
            TimetableRoute(
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
