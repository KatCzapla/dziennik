package pl.usagi.edziennik.ui.login

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.network.messages.MessagesDiagnostics
import pl.usagi.edziennik.ui.components.EmptyState
import pl.usagi.edziennik.ui.components.QuietLoadingState
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.dashboard.DashboardRoute
import pl.usagi.edziennik.ui.navigation.GradeNavigationTarget
import pl.usagi.edziennik.ui.navigation.SchoolTaskNavigationTarget
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import androidx.compose.ui.res.stringResource
import pl.usagi.edziennik.R

@Composable
fun LoginRoute(
    onOpenTimetable: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenSchoolTasks: () -> Unit,
    onOpenGrade: (GradeNavigationTarget) -> Unit,
    onOpenTask: (SchoolTaskNavigationTarget) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenPayments: () -> Unit,
    onOpenSettings: () -> Unit = {},
    logoutPending: Boolean = false,
    onLogoutFinished: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(logoutPending, state.isSessionValid) {
        if (logoutPending && !state.isSessionValid) onLogoutFinished()
    }
    if (logoutPending) {
        QuietLoadingState(
            title = stringResource(R.string.settings_logging_out),
            message = "",
            modifier = Modifier.fillMaxSize().padding(EDziennikTheme.spacing.lg),
        )
        return
    }
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }


    LoginScreen(
        state = state,
        onLoginChanged = viewModel::onLoginChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onRememberCredentialsChanged = viewModel::onRememberCredentialsChanged,
        onSubmit = viewModel::login,
        onClearSession = viewModel::clearSession,
        onStudentSelected = viewModel::selectStudent,
        onOpenTimetable = onOpenTimetable,
        onOpenAttendance = onOpenAttendance,
        onOpenGrades = onOpenGrades,
        onOpenSchoolTasks = onOpenSchoolTasks,
        onOpenGrade = onOpenGrade,
        onOpenTask = onOpenTask,
        onOpenMessages = onOpenMessages,
        onOpenPayments = onOpenPayments,
        onOpenSettings = onOpenSettings,
        showEnableNotifications = !notificationsGranted,
        onEnableNotifications = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
    )
}

@Composable
private fun LoginScreen(
    state: LoginUiState,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRememberCredentialsChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClearSession: () -> Unit,
    onStudentSelected: (Student) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenSchoolTasks: () -> Unit,
    onOpenGrade: (GradeNavigationTarget) -> Unit,
    onOpenTask: (SchoolTaskNavigationTarget) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenPayments: () -> Unit,
    showEnableNotifications: Boolean,
    onEnableNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.activeStudent != null && state.screenMode != LoginScreenMode.Home) {
                HomeChrome(
                    onOpenSettings = onOpenSettings,
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
                    LoginScreenMode.Home -> DashboardRoute(
                        loggedInUser = state.loggedInUser,
                        onOpenTimetable = onOpenTimetable,
                        onOpenAttendance = onOpenAttendance,
                        onOpenGrades = onOpenGrades,
                        onOpenSchoolTasks = onOpenSchoolTasks,
                        onOpenGrade = onOpenGrade,
                        onOpenTask = onOpenTask,
                        onOpenMessages = onOpenMessages,
                        onOpenPayments = onOpenPayments,
                        showEnableNotifications = showEnableNotifications,
                        onEnableNotifications = onEnableNotifications,
                        onOpenSettings = onOpenSettings,
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
                        onRememberCredentialsChanged = onRememberCredentialsChanged,
                        onSubmit = onSubmit,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeChrome(
    onOpenSettings: () -> Unit,
) {
    EDziennikTopBar(onOpenSettings = onOpenSettings)
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
    onOpenAttendance: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenSchoolTasks: () -> Unit,
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
            Button(
                onClick = onOpenAttendance,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Otwórz frekwencję")
            }
            Button(
                onClick = onOpenGrades,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Otwórz oceny")
            }
            Button(
                onClick = onOpenSchoolTasks,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Otwórz sprawdziany i zadania")
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
                    shape = MaterialTheme.shapes.small,
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
    onRememberCredentialsChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    var passwordVisible by remember { mutableStateOf(false) }

    ScreenColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Spacer(modifier = Modifier.height(spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = "Dziennik VULCAN",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Zaloguj się swoim kontem miejskim",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.isLoading) {
                    onRememberCredentialsChanged(!state.rememberCredentials)
                },
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.rememberCredentials,
                onCheckedChange = if (state.isLoading) null else onRememberCredentialsChanged,
            )
            Text(
                text = "Zapamiętaj dane logowania",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
                shape = MaterialTheme.shapes.small,
                colors = loginTextFieldColors(),
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
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                "Ukryj hasło"
                            } else {
                                "Pokaż hasło"
                            },
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = loginTextFieldColors(),
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
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = spacing.sm)
                            .height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text("Zaloguj się")
            }
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
        title = "Nie udało się zalogować",
        message = message,
    )
}

@Composable
private fun loginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
)

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
