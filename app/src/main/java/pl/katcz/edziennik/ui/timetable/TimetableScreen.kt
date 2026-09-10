package pl.katcz.edziennik.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.katcz.edziennik.domain.Student
import pl.katcz.edziennik.domain.TimetableChange
import pl.katcz.edziennik.domain.TimetableEntry
import pl.katcz.edziennik.ui.components.EmptyState
import pl.katcz.edziennik.ui.components.ErrorState
import pl.katcz.edziennik.ui.components.QuietLoadingState
import pl.katcz.edziennik.ui.components.StudentSwitcher
import pl.katcz.edziennik.ui.theme.EDziennikTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TimetableRoute(
    onBack: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    TimetableScreen(
        state = state,
        onBack = onBack,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onCurrentWeek = viewModel::currentWeek,
        onRetry = viewModel::retry,
        onStudentSelected = viewModel::selectStudent,
    )
}

@Composable
private fun TimetableScreen(
    state: TimetableUiState,
    onBack: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onRetry: () -> Unit,
    onStudentSelected: (Student) -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TimetableChrome(
                activeStudent = state.activeStudent,
                students = state.students,
                onStudentSelected = onStudentSelected,
                onBack = onBack,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            WeekNavigator(
                weekLabel = state.week.label(),
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onCurrentWeek = onCurrentWeek,
            )

            AnimatedContent(
                targetState = state.contentMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "timetable-content",
            ) { mode ->
                when (mode) {
                    TimetableContentMode.Loading -> QuietLoadingState(
                        title = "Pobieram plan",
                        message = "Zostawiam układ na miejscu i tylko odświeżam dane.",
                    )
                    TimetableContentMode.Error -> ErrorState(
                        title = "Plan chwilowo niedostępny",
                        message = state.errorMessage.orEmpty(),
                        actionLabel = "Spróbuj ponownie",
                        onAction = onRetry,
                    )
                    TimetableContentMode.Empty -> EmptyState(
                        title = "Brak zajęć w tym tygodniu",
                        message = "Wygląda na spokojniejszy tydzień albo plan nie został jeszcze opublikowany.",
                    )
                    TimetableContentMode.Content -> TimetableList(
                        weekStart = state.week.start,
                        entries = state.entries,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableChrome(
    activeStudent: Student?,
    students: List<Student>,
    onStudentSelected: (Student) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        TextButton(onClick = onBack) {
            Text("Wróć")
        }
        StudentSwitcher(
            activeStudent = activeStudent,
            students = students,
            onStudentSelected = onStudentSelected,
        )
    }
}

@Composable
private fun WeekNavigator(
    weekLabel: String,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onPreviousWeek) {
                Text("‹")
            }
            Text(
                text = weekLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = onNextWeek) {
                Text("›")
            }
        }
        TextButton(onClick = onCurrentWeek) {
            Text("Wróć do dzisiaj")
        }
    }
}

@Composable
private fun TimetableList(
    weekStart: LocalDate,
    entries: List<TimetableEntry>,
) {
    val entriesByDate = entries.groupBy { it.date }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xl),
    ) {
        items((0L..6L).map { weekStart.plusDays(it) }) { day ->
            DaySection(
                date = day,
                entries = entriesByDate[day].orEmpty(),
            )
        }
    }
}

@Composable
private fun DaySection(
    date: LocalDate,
    entries: List<TimetableEntry>,
) {
    val spacing = EDziennikTheme.spacing
    val isToday = date == LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        DayHeader(date = date, isToday = isToday)
        if (entries.isEmpty()) {
            Text(
                text = "Nic zaplanowanego.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                entries.forEachIndexed { index, entry ->
                    TimetableRow(
                        displayNumber = entry.lessonNumber ?: index + 1,
                        entry = entry,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date.dayOfWeek.polishName(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = date.format(dayFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isToday) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = "dzisiaj",
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun TimetableRow(
    displayNumber: Int,
    entry: TimetableEntry,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(
            modifier = Modifier.width(64.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = entry.startTime.format(timeFormatter),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.endTime.format(timeFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = displayNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = entry.subject,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LessonMeta(entry)
            entry.changes.forEach { change ->
                ChangeNote(change)
            }
        }
    }
}

@Composable
private fun LessonMeta(entry: TimetableEntry) {
    val details = listOfNotNull(
        entry.room?.let { "sala $it" },
        entry.teacher,
    )

    if (details.isNotEmpty()) {
        Text(
            text = details.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChangeNote(change: TimetableChange) {
    val spacing = EDziennikTheme.spacing
    val text = if (change.isSubstitution) {
        "Zastępstwo: ${change.teacher}"
    } else {
        listOfNotNull(
            change.subject,
            change.room?.let { "sala $it" },
            change.teacher,
            change.absenceInformation,
        ).joinToString(" · ").ifBlank { "Zmiana w planie (${change.changeType})" }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private enum class TimetableContentMode {
    Loading,
    Error,
    Empty,
    Content,
}

private val TimetableUiState.contentMode: TimetableContentMode
    get() = when {
        isLoading -> TimetableContentMode.Loading
        errorMessage != null -> TimetableContentMode.Error
        isEmpty -> TimetableContentMode.Empty
        else -> TimetableContentMode.Content
    }

private fun pl.katcz.edziennik.domain.time.WeekRange.label(): String =
    "${start.format(weekFormatter)} - ${end.format(weekFormatter)}"

private val weekFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("pl-PL"))
private val dayFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("pl-PL"))
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun DayOfWeek.polishName(): String =
    when (this) {
        DayOfWeek.MONDAY -> "Poniedziałek"
        DayOfWeek.TUESDAY -> "Wtorek"
        DayOfWeek.WEDNESDAY -> "Środa"
        DayOfWeek.THURSDAY -> "Czwartek"
        DayOfWeek.FRIDAY -> "Piątek"
        DayOfWeek.SATURDAY -> "Sobota"
        DayOfWeek.SUNDAY -> "Niedziela"
    }
