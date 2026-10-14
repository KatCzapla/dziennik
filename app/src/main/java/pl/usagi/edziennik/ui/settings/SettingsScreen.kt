package pl.usagi.edziennik.ui.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier.Companion
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import pl.usagi.edziennik.R
import pl.usagi.edziennik.persistence.changes.SyncDiagnosticEntry
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.ui.login.LoginViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onLogout: () -> Unit,
    loginViewModel: LoginViewModel,
) {
    val state by loginViewModel.uiState.collectAsState()
    val students = remember(state.students) {
        listOf(
            Student(
                key = "student_001",
                diaryId = 1,
                name = "Jan Kowalski",
                className = "1A",
                schoolName = "Szkoła Podstawowa nr 1",
                organizationalUnitId = 101
            ),
            Student(
                key = "student_002",
                diaryId = 2,
                name = "Anna Nowak",
                className = "2B",
                schoolName = "Szkoła Podstawowa nr 2",
                organizationalUnitId = 102
            ),
            Student(
                key = "student_003",
                diaryId = 3,
                name = "Piotr Wiśniewski",
                className = "3C",
                schoolName = "Szkoła Podstawowa nr 1",
                organizationalUnitId = 101
            )
        )

    }
    SettingsScreen(state.activeStudent, /*state.students*/students, loginViewModel::selectStudent,
        onOpenDiagnostics, onLogout, onBack)
}

@Composable
internal fun SettingsScreen(
    student: Student?,
    students: List<Student>,
    onStudentSelected: (Student) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SettingsTopBar(onBack, stringResource(R.string.settings_title)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    SettingsSectionTitle(stringResource(R.string.settings_student_section))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.md),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.School, contentDescription = null,
                                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                                    Text(
                                        stringResource(R.string.settings_current_student),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = student?.name
                                                ?: stringResource(R.string.settings_no_student),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        student?.className
                                            ?.takeIf { it.isNotEmpty() }
                                            ?.let { className ->
                                                Text(
                                                    text = "($className)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                    }
                                }
                            }
                            students.filter { it.diaryId != student?.diaryId }.forEach { other ->
                                HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.sm),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) { onStudentSelected(other) }
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                                        Text(other.name, style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        val details = listOf(other.className, other.schoolName).filter(String::isNotBlank).joinToString(" · ")
                                        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                                        modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    SettingsSectionTitle(stringResource(R.string.settings_sync_section))
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = onOpenDiagnostics)
                                .padding(spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                                Text(stringResource(R.string.sync_diagnostics_title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.settings_diagnostics_description), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    SettingsSectionTitle(stringResource(R.string.settings_account_section))
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = onLogout)
                                .heightIn(min = 52.dp)
                                .padding(horizontal = spacing.md, vertical = spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null,
                                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.settings_logout), style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit, title: String? = null) {
    val spacing = EDziennikTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
        }
        if (title != null) Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SyncDiagnosticsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        while (isActive) {
            viewModel.refresh()
            delay(2_000)
        }
    }
    SyncDiagnosticsScreen(state, onBack)
}

@Composable
internal fun SyncDiagnosticsScreen(state: SettingsUiState, onBack: () -> Unit) {
    val spacing = EDziennikTheme.spacing
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SettingsTopBar(onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.sync_diagnostics_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            item {
                SettingsSectionTitle(stringResource(R.string.sync_diagnostics_current_state))
            }
            when {
                state.loading -> item { CircularProgressIndicator() }
                !state.hasAccount -> item { Text(stringResource(R.string.sync_diagnostics_no_account)) }
                state.failed -> item { Text(stringResource(R.string.sync_diagnostics_unavailable)) }
                else -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            DiagnosticValue(stringResource(R.string.sync_diagnostics_periodic),
                                stringResource(if (state.periodicRegistered) R.string.sync_diagnostics_registered
                                    else R.string.sync_diagnostics_not_registered))
                            if (state.periodicStates.isNotEmpty()) {
                                DiagnosticValue(stringResource(R.string.sync_diagnostics_work_state),
                                    state.periodicStates.map { stringResource(it.labelResource()) }.joinToString(", "))
                            }
                            DiagnosticValue(stringResource(R.string.sync_diagnostics_pending), state.pendingCount.toString())
                            state.notifications?.let { notifications ->
                                DiagnosticValue(stringResource(R.string.sync_diagnostics_permission),
                                    enabledLabel(notifications.permissionGranted))
                                DiagnosticValue(stringResource(R.string.sync_diagnostics_app_notifications),
                                    enabledLabel(notifications.appEnabled))
                                DiagnosticValue(stringResource(R.string.sync_diagnostics_channel),
                                    stringResource(when (notifications.channel) {
                                        DiagnosticChannelState.NOT_CREATED -> R.string.sync_diagnostics_channel_not_created
                                        DiagnosticChannelState.BLOCKED -> R.string.sync_diagnostics_disabled
                                        DiagnosticChannelState.ENABLED -> R.string.sync_diagnostics_enabled
                                        DiagnosticChannelState.NOT_REQUIRED -> R.string.sync_diagnostics_channel_not_required
                                    }))
                            }
                        }
                    }
                    item {
                        Text(stringResource(R.string.sync_diagnostics_last_attempt), style = MaterialTheme.typography.titleSmall)
                        state.history.firstOrNull()?.let { entry ->
                            Text(stringResource(R.string.sync_diagnostics_attempt_summary,
                                entry.startedAt.displayTime(), stringResource(diagnosticResultResource(entry.result))),
                                style = MaterialTheme.typography.bodyMedium)
                        } ?: Text(stringResource(R.string.sync_diagnostics_never_started),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.history.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.sync_diagnostics_history), style = MaterialTheme.typography.titleSmall)
                        }
                        items(state.history, key = { it.id }) { DiagnosticHistoryEntry(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun enabledLabel(enabled: Boolean): String =
    stringResource(if (enabled) R.string.sync_diagnostics_enabled else R.string.sync_diagnostics_disabled)

@Composable
private fun DiagnosticValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End)
    }
}

@Composable
private fun DiagnosticHistoryEntry(entry: SyncDiagnosticEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs)) {
        Text(stringResource(if (entry.source == "MANUAL") R.string.sync_diagnostics_manual else R.string.sync_diagnostics_periodic_source),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        DiagnosticValue(stringResource(R.string.sync_diagnostics_start), entry.startedAt.displayTime())
        DiagnosticValue(stringResource(R.string.sync_diagnostics_end),
            entry.finishedAt?.displayTime() ?: stringResource(R.string.sync_diagnostics_unfinished))
        DiagnosticValue(stringResource(R.string.sync_diagnostics_result),
            stringResource(diagnosticResultResource(entry.result)))
        entry.reason?.takeIf { it != "NONE" }?.let {
            DiagnosticValue(stringResource(R.string.sync_diagnostics_reason), it)
        }
        DiagnosticValue(stringResource(R.string.sync_diagnostics_events),
            entry.detectedEvents?.toString() ?: stringResource(R.string.sync_diagnostics_count_unknown))
        DiagnosticValue(stringResource(R.string.sync_diagnostics_handed_off),
            if (entry.finishedAt != null) entry.handedOffNotifications.toString()
            else stringResource(R.string.sync_diagnostics_count_unknown))
    }
}

private val diagnosticTimeFormat = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss", Locale.forLanguageTag("pl-PL"))
private fun Long.displayTime(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(diagnosticTimeFormat)
