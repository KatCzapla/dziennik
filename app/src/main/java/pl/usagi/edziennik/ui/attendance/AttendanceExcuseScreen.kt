package pl.usagi.edziennik.ui.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.components.EmptyState
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import pl.usagi.edziennik.ui.timetable.dayMonthLabel
import pl.usagi.edziennik.ui.timetable.polishName
import pl.usagi.edziennik.ui.timetable.schoolWeekLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AttendanceExcuseRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val week = state.excuseFormWeek

    BackHandler(enabled = week != null) {
        if (!state.isSubmittingExcuse) viewModel.dismissExcuse()
    }
    LaunchedEffect(week) {
        if (week == null) onBack()
    }
    if (week == null) return

    val spacing = EDziennikTheme.spacing
    val visibleDays = visibleExcuseDays(state)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EDziennikTopBar(
                onOpenSettings = onOpenSettings,
                showBack = true,
                onBack = {
                    if (!state.isSubmittingExcuse) {
                        viewModel.dismissExcuse()
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .imePadding()
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Zaznaczono: ${state.selectedExcuseOptions.size}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = viewModel::submitExcuse,
                    enabled = state.canSubmitSelectedExcuses,
                ) {
                    if (state.isSubmittingExcuse) {
                        CircularProgressIndicator(modifier = Modifier.size(spacing.md))
                        Spacer(modifier = Modifier.size(spacing.xs))
                    }
                    Text("Wyślij")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text("Usprawiedliw", style = MaterialTheme.typography.titleLarge)
                    Text(
                        week.schoolWeekLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (visibleDays.isEmpty()) {
                item {
                    if (state.isSubmittingExcuse) {
                        CircularProgressIndicator()
                    } else {
                        EmptyState(
                            title = "Brak dostępnych lekcji do usprawiedliwienia",
                            message = "",
                        )
                    }
                }
            }
            visibleDays.forEach { (date, options) ->
                item(key = "day-$date") {
                    Text(
                        "${date.dayOfWeek.polishName()} · ${date.dayMonthLabel()}",
                        modifier = Modifier.padding(top = spacing.sm),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(options, key = { "${it.date}-${it.lessonTimeId ?: "day"}" }) { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(
                            enabled = !state.isSubmittingExcuse,
                            onClick = { viewModel.toggleExcuseOption(option) },
                        ).padding(vertical = spacing.xxs),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (option) {
                            is ExcuseOption.WholeDay -> Text(
                                "Usprawiedliw cały dzień",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            is ExcuseOption.Lesson -> {
                                Column(modifier = Modifier.width(56.dp)) {
                                    Text(
                                        option.entry.startTime.format(timeFormatter),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        option.entry.endTime.format(timeFormatter),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(
                                    modifier = Modifier.width(1.dp).height(58.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                                ) {
                                    Text(
                                        option.entry.subject ?: "Zajęcia",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        option.entry.status.label(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Checkbox(
                            checked = option in state.selectedExcuseOptions,
                            onCheckedChange = { viewModel.toggleExcuseOption(option) },
                            enabled = !state.isSubmittingExcuse,
                        )
                    }
                }
            }
            if (visibleDays.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = state.excuseContent,
                        onValueChange = viewModel::changeExcuseContent,
                        enabled = !state.isSubmittingExcuse,
                        label = { Text("Uzasadnienie") },
                        supportingText = {
                            Text("Opcjonalne · ${state.excuseContent.codePointCount(0, state.excuseContent.length)}/256")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                    )
                }
            }
            state.excuseErrorMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

internal fun visibleExcuseDays(state: AttendanceUiState): List<Pair<LocalDate, List<ExcuseOption>>> =
    state.availableExcuseOptions
        .filter { !it.date.isBefore(state.schoolYearRange.start) && !it.date.isAfter(state.schoolYearRange.end) }
        .groupBy { it.date }
        .toSortedMap()
        .map { it.key to it.value }

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
