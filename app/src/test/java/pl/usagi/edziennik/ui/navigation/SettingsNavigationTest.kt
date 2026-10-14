package pl.usagi.edziennik.ui.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SettingsNavigationTest {
    @Test fun eachModuleOpensSettingsOnceAndBackReturnsToThatModule() = withNavigation { nav ->
        listOf("home", "timetable", "attendance", "attendance/excuse", "grades", "schoolTasks", "messages", "payments").forEach { route ->
            if (route != "home") nav.navigate(route)
            repeat(5) { nav.openSettings() }
            assertEquals("settings", nav.currentDestination?.route)
            nav.popBackStack()
            assertEquals(route, nav.currentDestination?.route)
            nav.clearAuthenticatedBackStack()
        }
    }

    @Test fun backFromDiagnosticsReturnsToSettingsThenDashboard() = withNavigation { nav ->
        nav.navigate("settings")
        nav.navigate("settings/diagnostics")
        nav.popBackStack()
        assertEquals("settings", nav.currentDestination?.route)
        nav.popBackStack()
        assertEquals("home", nav.currentDestination?.route)
    }

    @Test fun logoutClearsAllProtectedScreensAndKeepsHomeViewModelOwner() = withNavigation { nav ->
        val home = nav.getBackStackEntry("home")
        nav.navigate("grades")
        nav.navigate("settings")
        nav.navigate("settings/diagnostics")
        nav.clearAuthenticatedBackStack()
        assertEquals("home", nav.currentDestination?.route)
        assertEquals(home, nav.getBackStackEntry("home"))
        listOf("grades", "settings", "settings/diagnostics").forEach { route ->
            assertThrows(IllegalArgumentException::class.java) { nav.getBackStackEntry(route) }
        }
    }

    @Test fun reloginDoesNotRestoreOldProtectedBackStack() = withNavigation { nav ->
        nav.navigate("grades")
        nav.navigate("settings")
        nav.clearAuthenticatedBackStack()
        nav.navigate("settings")
        nav.navigate("settings/diagnostics")
        nav.popBackStack()
        nav.popBackStack()
        assertEquals("home", nav.currentDestination?.route)
        assertThrows(IllegalArgumentException::class.java) { nav.getBackStackEntry("grades") }
    }

    private fun withNavigation(test: (NavHostController) -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val nav = NavHostController(activity.get())
            nav.setLifecycleOwner(activity.get())
            nav.setViewModelStore(activity.get().viewModelStore)
            nav.navigatorProvider.addNavigator(ComposeNavigator())
            nav.graph = nav.createGraph(startDestination = "home") {
                listOf("home", "timetable", "attendance", "attendance/excuse", "grades", "schoolTasks", "messages", "payments",
                    "settings", "settings/diagnostics").forEach { route ->
                    composable(route) { }
                }
            }
            test(nav)
        } finally { activity.pause().stop().destroy() }
    }
}
