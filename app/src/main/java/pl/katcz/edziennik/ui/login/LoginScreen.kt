package pl.katcz.edziennik.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.katcz.edziennik.domain.Student
import pl.katcz.edziennik.ui.components.EmptyState
import pl.katcz.edziennik.ui.components.QuietLoadingState
import pl.katcz.edziennik.ui.components.StudentSwitcher
import pl.katcz.edziennik.ui.theme.EDziennikTheme

@Composable
fun LoginRoute(
    onOpenTimetable: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LoginScreen(
        state = state,
        onLoginChanged = viewModel::onLoginChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onSubmit = viewModel::login,
        onClearSession = viewModel::clearSession,
        onStudentSelected = viewModel::selectStudent,
        onOpenTimetable = onOpenTimetable,
    )
}

@Composable
private fun LoginScreen(
    state: LoginUiState,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearSession: () -> Unit,
    onStudentSelected: (Student) -> Unit,
    onOpenTimetable: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.activeStudent != null) {
                HomeChrome(
                    activeStudent = state.activeStudent,
                    students = state.students,
                    onStudentSelected = onStudentSelected,
                    onClearSession = onClearSession,
                )
            }
        },
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnimatedContent(
                targetState = state.screenMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "login-content",
            ) { mode ->
                when (mode) {
                    LoginScreenMode.CheckingSession -> CheckingSessionContent()
                    LoginScreenMode.LoadingStudents -> LoadingStudentsContent()
                    LoginScreenMode.Home -> HomeContent(
                        student = state.activeStudent,
                        onOpenTimetable = onOpenTimetable,
                    )
                    LoginScreenMode.PickStudent -> StudentPickerContent(
                        students = state.students,
                        errorMessage = state.errorMessage,
                        onStudentSelected = onStudentSelected,
                        onClearSession = onClearSession,
                    )
                    LoginScreenMode.Login -> LoginForm(
                        state = state,
                        onLoginChanged = onLoginChanged,
                        onPasswordChanged = onPasswordChanged,
                        onSubmit = onSubmit,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeChrome(
    activeStudent: Student,
    students: List<Student>,
    onStudentSelected: (Student) -> Unit,
    onClearSession: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudentSwitcher(
            activeStudent = activeStudent,
            students = students,
            onStudentSelected = onStudentSelected,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClearSession) {
            Text("Wyloguj")
        }
    }
}

@Composable
private fun CheckingSessionContent() {
    ScreenColumn(verticalArrangement = Arrangement.Center) {
        QuietLoadingState(
            title = "Chwileczkę",
            message = "Sprawdzam, czy możesz wejść bez ponownego logowania.",
        )
    }
}

@Composable
private fun LoadingStudentsContent() {
    ScreenColumn(verticalArrangement = Arrangement.Center) {
        QuietLoadingState(
            title = "Prawie gotowe",
            message = "Pobieram listę uczniów z UONET+.",
        )
    }
}

@Composable
private fun HomeContent(
    student: Student?,
    onOpenTimetable: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    ScreenColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Spacer(modifier = Modifier.height(spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = "Dzień dobry",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = student?.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = "Na teraz",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onOpenTimetable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Otwórz plan zajęć")
            }
        }
    }
}

@Composable
private fun StudentPickerContent(
    students: List<Student>,
    errorMessage: String?,
    onStudentSelected: (Student) -> Unit,
    onClearSession: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    ScreenColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Spacer(modifier = Modifier.height(spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = "Kto dzisiaj?",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Wybierz ucznia, dla którego chcesz zobaczyć dane.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            students.forEach { student ->
                Surface(
                    onClick = { onStudentSelected(student) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${student.className} · ${student.schoolName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        errorMessage?.let { LoginErrorText(it) }
        TextButton(onClick = onClearSession) {
            Text("Wróć do logowania")
        }
    }
}

@Composable
private fun LoginForm(
    state: LoginUiState,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    ScreenColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Spacer(modifier = Modifier.height(spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = "eDziennik",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Sprawdź plan i szkolne sprawy bez przedzierania się przez portal.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = state.login,
                onValueChange = onLoginChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                singleLine = true,
                label = { Text("Login") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                singleLine = true,
                label = { Text("Hasło") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSubmit() },
                ),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
            ) {
                Text(if (state.isLoading) "Logowanie..." else "Zaloguj")
            }
            Text(
                text = "Login i hasło służą tylko do bieżącego logowania.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.errorMessage?.let { LoginErrorText(it) }
        }
    }
}

@Composable
private fun ScreenColumn(
    verticalArrangement: Arrangement.Vertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.Start,
        content = content,
    )
}

@Composable
private fun LoginErrorText(message: String) {
    EmptyState(
        title = "Nie udało się wejść",
        message = message,
    )
}

private enum class LoginScreenMode {
    CheckingSession,
    LoadingStudents,
    Home,
    PickStudent,
    Login,
}

private val LoginUiState.screenMode: LoginScreenMode
    get() = when {
        isCheckingSession -> LoginScreenMode.CheckingSession
        isSessionValid && isLoadingStudents -> LoginScreenMode.LoadingStudents
        isSessionValid && activeStudent != null -> LoginScreenMode.Home
        isSessionValid && students.size > 1 -> LoginScreenMode.PickStudent
        else -> LoginScreenMode.Login
    }
