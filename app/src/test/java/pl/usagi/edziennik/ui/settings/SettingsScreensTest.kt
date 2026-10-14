package pl.usagi.edziennik.ui.settings

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.work.WorkInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.persistence.changes.SyncDiagnosticEntry
import pl.usagi.edziennik.ui.components.EDziennikTopBar

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h2000dp")
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsScreensTest {
    private val first = Student("unused-first", 1, "Pierwszy uczeń", "1A", "Szkoła")
    private val second = Student("unused-second", 2, "Drugi uczeń", "2B", "Szkoła")

    @Test fun sharedTopBarOpensSettingsAndKeepsBackWithoutStudentSwitcher() {
        val actions = mutableListOf<String>()
        withScreen({ EDziennikTopBar(onOpenSettings = { actions += "settings" }, showBack = true,
            onBack = { actions += "back" }) }) { nodes ->
            clickDescription(nodes(), "Ustawienia")
            clickDescription(nodes(), "Wróć")
            assertEquals(listOf("settings", "back"), actions)
            assertFalse(nodes().any {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Zmień ucznia") == true
            })
        }
    }

    @Test fun sharedTopBarWithoutBackContainsOnlyOneSettingsAction() {
        withScreen({ EDziennikTopBar(onOpenSettings = {}) }) { nodes ->
            val descriptions = nodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
                .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription) }.flatten()
            assertEquals(listOf("Ustawienia"), descriptions)
        }
    }

    @Test fun settingsHasThreeSectionsAndExistingActions() {
        val actions = mutableListOf<String>()
        withScreen({ SettingsScreen(first, listOf(first, second), {},
            { actions += "diagnostics" }, { actions += "logout" }, { actions += "back" }) }) { nodes ->
            listOf("UCZEŃ", "SYNCHRONIZACJA", "KONTO", first.name).forEach { label ->
                assertTrue(nodes().hasText(label))
            }
            clickText(nodes(), "Diagnostyka synchronizacji")
            clickText(nodes(), "Wyloguj się")
            clickDescription(nodes(), "Wróć")
            assertEquals(listOf("diagnostics", "logout", "back"), actions)
            assertFalse(nodes().hasText("Ostatnie 20 wykonań"))
        }
    }

    @Test fun inactiveStudentRowPassesChosenStudentThroughExistingCallback() {
        var chosen: Student? = null
        withScreen({ SettingsScreen(first, listOf(first, second), { chosen = it }, {}, {}, {}) }) { nodes ->
            assertFalse(nodes().hasText("Zmień ucznia"))
            clickText(nodes(), second.name)
            assertEquals(second, chosen)
        }
    }

    @Test fun onlyActiveStudentDoesNotProduceAnyStudentSelectionRow() {
        withScreen({ SettingsScreen(first, listOf(first), { fail("Active student must not be selectable") }, {}, {}, {}) }) { nodes ->
            assertTrue(nodes().hasText("Aktualnie wybrany uczeń"))
            assertTrue(nodes().hasText(first.name))
            assertFalse(nodes().any { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == first.name } == true
            })
        }
    }

    @Test fun inactiveStudentsAreFilteredByIdAndKeepTheirListOrder() {
        val third = first.copy(diaryId = 3, name = "Trzeci uczeń")
        val selected = first.copy(schoolName = "Zmieniona nazwa szkoły")
        val students = listOf(second, first, third)
        withScreen({ SettingsScreen(selected, students, {}, {}, {}, {}) }) { nodes ->
            val names = nodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                .map { it.text }.filter { it in students.map(Student::name) }
            assertEquals(listOf(second.name, third.name), names)
        }
    }

    @Test fun newActiveStudentIsRemovedFromSelectionAndPreviousStudentBecomesSelectable() {
        var active by mutableStateOf(first)
        withScreen({ SettingsScreen(active, listOf(first, second), { active = it }, {}, {}, {}) }) { nodes ->
            clickText(nodes(), second.name)
            val refreshed = nodes()
            assertFalse(refreshed.any { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == second.name } == true
            })
            clickText(refreshed, first.name)
            assertEquals(first, active)
        }
    }

    @Test fun diagnosticScreenShowsPolishStatesAndFailureDetails() {
        val state = SettingsUiState(hasAccount = true, loading = false,
            periodicStates = listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING),
            history = listOf(SyncDiagnosticEntry(1, "local-account", 1, "PERIODIC", 2,
                "RETRY", "SESSION_TRANSPORT_ERROR", 3, 1)))
        withScreen({ SyncDiagnosticsScreen(state, {}) }) { nodes ->
            assertTrue(nodes().hasText("Oczekuje na uruchomienie, W trakcie"))
            assertTrue(nodes().hasText("Do ponowienia"))
            assertTrue(nodes().hasText("SESSION_TRANSPORT_ERROR"))
            assertFalse(nodes().hasText("local-account"))
        }
    }

    @Test fun loggedOutDiagnosticScreenDoesNotDisplayPreviousHistory() {
        val state = SettingsUiState(loading = false, history = listOf(
            SyncDiagnosticEntry(1, "old-account", 1, "MANUAL", 2, "FAILURE", "LOGIN_REJECTED")))
        withScreen({ SyncDiagnosticsScreen(state, {}) }) { nodes ->
            assertTrue(nodes().hasText("Zaloguj się, aby zobaczyć diagnostykę"))
            assertFalse(nodes().hasText("LOGIN_REJECTED"))
        }
    }

    @Test fun allWorkManagerStatesAndRunResultsHavePolishLabels() {
        withScreen({ }) { _ ->
            val context = org.robolectric.RuntimeEnvironment.getApplication()
            assertEquals(listOf("Oczekuje na uruchomienie", "W trakcie", "Zakończono pomyślnie",
                "Zakończono błędem", "Zablokowane", "Anulowane"),
                WorkInfo.State.entries.map { context.getString(it.labelResource()) })
            assertEquals(listOf("Sukces", "Częściowy sukces", "Do ponowienia", "Niepowodzenie"),
                listOf("SUCCESS", "PARTIAL", "RETRY", "FAILURE").map {
                    context.getString(diagnosticResultResource(it))
                })
        }
    }

    private fun withScreen(content: @Composable () -> Unit, test: (() -> List<SemanticsNode>) -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val view = ComposeView(activity.get())
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(UnconfinedTestDispatcher() + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view.setParentCompositionContext(recomposer)
        activity.get().setContentView(view)
        view.setContent { MaterialTheme { content() } }
        var frame = 0L
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val root = view.getChildAt(0) as ViewRootForTest
            test {
                Snapshot.sendApplyNotifications()
                clock.sendFrame(++frame * 16_000_000)
                shadowOf(Looper.getMainLooper()).idle()
                root.measureAndLayoutForTest()
                descendants(root.semanticsOwner.rootSemanticsNode)
            }
        } finally {
            view.disposeComposition()
            recomposer.cancel()
            scope.cancel()
            activity.pause().stop().destroy()
        }
    }

    private fun List<SemanticsNode>.hasText(text: String) = any {
        it.config.getOrNull(SemanticsProperties.Text)?.any { label -> label.text == text } == true
    }

    private fun clickText(nodes: List<SemanticsNode>, text: String) {
        val node = nodes.singleOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { label -> label.text == text } == true &&
                it.config.getOrNull(SemanticsActions.OnClick) != null
        } ?: error("Missing clickable text: $text; visible texts: ${nodes.mapNotNull { it.config.getOrNull(SemanticsProperties.Text) }}")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }

    private fun clickDescription(nodes: List<SemanticsNode>, text: String) {
        val node = nodes.single {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true &&
                it.config.getOrNull(SemanticsActions.OnClick) != null
        }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)

}
