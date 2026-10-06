package pl.usagi.edziennik.ui.schooltasks

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.components.EmptyState
import pl.usagi.edziennik.ui.components.ErrorState
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import pl.usagi.edziennik.ui.timetable.dayMonthLabel
import pl.usagi.edziennik.ui.timetable.polishName
import pl.usagi.edziennik.ui.timetable.schoolWeekLabel
import java.time.LocalDate
import pl.usagi.edziennik.ui.navigation.SchoolTaskNavigationTarget

@Composable
fun SchoolTasksRoute(
    onBack: () -> Unit,
    viewModel: SchoolTasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    SchoolTasksScreen(
        state = state,
        onBack = onBack,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onToday = viewModel::today,
        onRetry = viewModel::retry,
        onStudentSelected = viewModel::selectStudent,
        onTargetShown = viewModel::navigationTargetShown,
    )
}

@Composable
private fun SchoolTasksScreen(
    state: SchoolTasksUiState,
    onBack: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
    onStudentSelected: (Student) -> Unit,
    onTargetShown: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EDziennikTopBar(
                activeStudent = state.activeStudent,
                students = state.students,
                onStudentSelected = onStudentSelected,
                showBack = true,
                onBack = onBack,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SchoolTasksHeader(
                selectedDate = state.selectedDate,
                weekLabel = state.displayedWeek.schoolWeekLabel(),
                canGoPreviousWeek = state.canGoPreviousWeek,
                canGoNextWeek = state.canGoNextWeek,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onToday = onToday,
            )

            if (state.errorMessage != null) {
                ErrorState(
                    title = "Sprawdziany i zadania chwilowo niedostępne",
                    message = state.errorMessage,
                    actionLabel = "Spróbuj ponownie",
                    onAction = onRetry,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    SchoolTasksList(
                        dayGroups = state.dayGroups,
                        isEmpty = state.isEmpty,
                        target = state.navigationTarget,
                        isLoading = state.isLoading,
                        onTargetShown = onTargetShown,
                    )

                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = spacing.sm)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolTasksHeader(
    selectedDate: LocalDate,
    weekLabel: String,
    canGoPreviousWeek: Boolean,
    canGoNextWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    val todayTarget = initialSchoolTasksDate()
    val showToday = selectedDate != todayTarget

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sprawdziany i zadania",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
            ) {
                TextButton(
                    onClick = onToday,
                    border = BorderStroke(
                        1.dp,
                        when {
                            selectedDate != todayTarget -> MaterialTheme.colorScheme.primary
                            else -> Color.Transparent
                        }
                    ),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = when {
                            selectedDate != todayTarget -> MaterialTheme.colorScheme.primary
                            else -> Color.Transparent
                        },
                        contentColor = when {
                            selectedDate != todayTarget -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text("Bieżące")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPreviousWeek,
                enabled = canGoPreviousWeek,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Poprzedni tydzień",
                )
            }
            Text(
                text = weekLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onNextWeek,
                enabled = canGoNextWeek,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Następny tydzień",
                )
            }
        }
    }
}

@Composable
private fun SchoolTasksList(
    dayGroups: List<SchoolTasksDayGroup>,
    isEmpty: Boolean,
    target: SchoolTaskNavigationTarget?,
    isLoading: Boolean,
    onTargetShown: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    val listState = rememberLazyListState()
    val targetIndex = target?.let(dayGroups::targetItemIndex)
    LaunchedEffect(target, targetIndex, isLoading) {
        if (!isLoading && targetIndex != null) {
            listState.scrollToItem(targetIndex)
            onTargetShown()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        if (isEmpty) {
            item {
                EmptyState(
                    title = "Brak sprawdzianów i zadań",
                    message = "",
                )
            }
        } else {
            dayGroups.forEach { group ->
                item(key = "day-${group.date}") {
                    DayHeader(date = group.date)
                }
                items(
                    items = group.tasks,
                    key = { task -> "${task.rawType}-${task.id}-${task.date}" },
                ) { task ->
                    SchoolTaskRow(task = task)
                }
            }
            item {
                Spacer(modifier = Modifier.height(spacing.md))
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        text = "${date.dayOfWeek.polishName()} · ${date.dayMonthLabel()}",
        style = MaterialTheme.typography.titleMedium, //.bodyMedium, //.titleSmall,
        color = MaterialTheme.colorScheme.onSurface //.onSurfaceVariant,
    )
}

@Composable
private fun SchoolTaskRow(task: SchoolTask) {
    val spacing = EDziennikTheme.spacing
    val detailLines = task.presentationDetailLines()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        TimelineAccent(type = task.type)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            TypeStatus(type = task.type)
            Text(
                text = task.subject ?: "Bez przedmiotu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            detailLines.forEach { line ->
                DetailLine(line = line)
            }
            if (task.hasAttachment) {
                Text(
                    text = "Załącznik",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DetailLine(line: SchoolTaskDetailLine) {
    val spacing = EDziennikTheme.spacing
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(line, linkColor) {
        buildAnnotatedString {
            append(line.value)
            if (line.label == "Opis") {
                val linkedText = SpannableString(line.value)
                Linkify.addLinks(linkedText, Linkify.WEB_URLS)
                linkedText.getSpans(0, linkedText.length, URLSpan::class.java).forEach { span ->
                    addLink(
                        LinkAnnotation.Url(
                            url = span.url,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            ),
                        ),
                        start = linkedText.getSpanStart(span),
                        end = linkedText.getSpanEnd(span),
                    )
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = line.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimelineAccent(type: SchoolTaskType) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(64.dp)
            .background(type.accentColor().copy(alpha = 0.34f)),
    )
}

@Composable
private fun TypeStatus(type: SchoolTaskType) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)),
        color = type.containerColor(),
        contentColor = type.contentColor(),
    ) {
        Text(
            text = type.typeLabel(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun SchoolTaskType.containerColor(): Color =
    when (this) {
        SchoolTaskType.Test -> MaterialTheme.colorScheme.secondaryContainer
        SchoolTaskType.Homework -> MaterialTheme.colorScheme.primaryContainer
        SchoolTaskType.Quiz -> MaterialTheme.colorScheme.secondaryContainer
        is SchoolTaskType.Unknown -> MaterialTheme.colorScheme.surfaceVariant
    }

@Composable
private fun SchoolTaskType.contentColor(): Color =
    when (this) {
        SchoolTaskType.Test -> MaterialTheme.colorScheme.onSecondaryContainer
        SchoolTaskType.Homework -> MaterialTheme.colorScheme.onPrimaryContainer
        SchoolTaskType.Quiz -> MaterialTheme.colorScheme.onSecondaryContainer
        is SchoolTaskType.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun SchoolTaskType.accentColor(): Color =
    when (this) {
        SchoolTaskType.Test -> MaterialTheme.colorScheme.secondary
        SchoolTaskType.Homework -> MaterialTheme.colorScheme.primary
        SchoolTaskType.Quiz -> MaterialTheme.colorScheme.secondary
        is SchoolTaskType.Unknown -> MaterialTheme.colorScheme.outline
    }
