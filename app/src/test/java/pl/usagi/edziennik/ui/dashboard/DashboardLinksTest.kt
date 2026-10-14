package pl.usagi.edziennik.ui.dashboard

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h2000dp")
class DashboardLinksTest {
    @Test fun linksInvokeExistingModuleNavigationCallbacks() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val destinations = mutableListOf<String>()
        val view = ComposeView(activity)
        activity.setContentView(view)
        view.setContent {
            MaterialTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        activeStudent = pl.usagi.edziennik.domain.Student("unused", 1, "Wybrany uczeń testowy", "1A", "Szkoła"),
                    ),
                    loggedInUser = null,
                    onShowTodayTimetable = {},
                    onShowTomorrowTimetable = {},
                    onOpenTimetable = { destinations += "timetable" },
                    onOpenAttendance = { destinations += "attendance" },
                    onOpenGrades = { destinations += "grades" },
                    onOpenSchoolTasks = { destinations += "tasks" },
                    onOpenGrade = {},
                    onOpenTask = {},
                    onOpenMessages = {},
                    onOpenPayments = {},
                    showEnableNotifications = false,
                    onEnableNotifications = {},
                    onOpenSettings = { destinations += "settings" },
                )
            }
        }
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val root = view.getChildAt(0) as ViewRootForTest
            root.measureAndLayoutForTest()
            val nodes = descendants(root.semanticsOwner.rootSemanticsNode)
            assertFalse(nodes.any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any {
                    it.text in setOf("Wyloguj", "Wyloguj się", "Wybrany uczeń testowy")
                } == true
            })
            fun clickable(text: String) = nodes.filter { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            assertTrue(clickable("Pełny plan").single().config[SemanticsActions.OnClick].action!!.invoke())
            assertTrue(clickable("Frekwencja i usprawiedliwienia").single()
                .config[SemanticsActions.OnClick].action!!.invoke())
            val allLinks = clickable("Wszystkie")
            assertEquals(2, allLinks.size)
            allLinks.forEach { assertTrue(it.config[SemanticsActions.OnClick].action!!.invoke()) }
            val settings = nodes.single { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Ustawienia") == true &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            assertTrue(settings.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(listOf("timetable", "attendance", "grades", "tasks", "settings"), destinations)
        } finally {
            view.disposeComposition()
            controller.pause().stop().destroy()
        }
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap(::descendants)
}
