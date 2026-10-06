package pl.usagi.edziennik.ui.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.firstSelectableSchoolDate
import pl.usagi.edziennik.domain.time.isSelectableSchoolDate
import pl.usagi.edziennik.domain.time.selectableSchoolDateAt
import pl.usagi.edziennik.domain.time.selectableSchoolDateIndex
import pl.usagi.edziennik.domain.time.selectableSchoolDayCount
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.components.EmptyState
import pl.usagi.edziennik.ui.components.ErrorState
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import pl.usagi.edziennik.ui.timetable.dayMonthLabel
import pl.usagi.edziennik.ui.timetable.polishName
import pl.usagi.edziennik.ui.timetable.schoolDays
import pl.usagi.edziennik.ui.timetable.schoolWeekLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AttendanceRoute(
    onBack: () -> Unit,
    onOpenExcuse: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    AttendanceScreen(
        state = state,
        onBack = onBack,
        onDateSelected = viewModel::selectDate,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onToday = viewModel::today,
        onRetry = viewModel::retry,
        onStudentSelected = viewModel::selectStudent,
        onViewSelected = viewModel::selectView,
        onSubjectSelected = viewModel::selectSubject,
        onOpenExcuse = {
            viewModel.openExcuseForDisplayedWeek()
            if (viewModel.uiState.value.excuseFormWeek != null) onOpenExcuse()
        },
    )
}

@Composable
private fun AttendanceScreen(
    state: AttendanceUiState,
    onBack: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
    onStudentSelected: (Student) -> Unit,
    onViewSelected: (AttendanceView) -> Unit,
    onSubjectSelected: (Int) -> Unit,
    onOpenExcuse: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    val firstPagerDate = remember(state.schoolYearRange) {
        firstSelectableSchoolDate(state.schoolYearRange)
    }
    val lastPagerDate = state.latestSelectableDate
    val pagerPageCount = remember(firstPagerDate, lastPagerDate) {
        selectableSchoolDayCount(firstPagerDate, lastPagerDate)
    }
    val initialPage = remember(firstPagerDate, state.selectedDate) {
        selectableSchoolDateIndex(firstPagerDate, state.selectedDate) ?: 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { pagerPageCount }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState, firstPagerDate) {
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                if (page in 0 until pagerPageCount) {
                    onDateSelected(selectableSchoolDateAt(firstPagerDate, page))
                }
            }
    }

    LaunchedEffect(state.selectedDate, firstPagerDate) {
        val targetPage = selectableSchoolDateIndex(firstPagerDate, state.selectedDate) ?: return@LaunchedEffect
        if (targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

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
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Frekwencja",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (state.showHeaderExcuseButton) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
                        TextButton(
                            onClick = onOpenExcuse,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Usprawiedliw")
                        }
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = state.view.ordinal) {
                AttendanceView.entries.forEach { view ->
                    Tab(
                        selected = state.view == view,
                        onClick = { onViewSelected(view) },
                        text = {
                            Text(if (view == AttendanceView.ENTRIES) "Dziennik" else "Statystyki")
                        },
                    )
                }
            }

            if (state.view == AttendanceView.STATISTICS) {
                AttendanceStatisticsContent(
                    state = state,
                    onSubjectSelected = onSubjectSelected,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            } else {
                AttendanceJournalControls(
                    displayedWeek = state.displayedWeek,
                    selectedDate = state.selectedDate,
                    schoolYearRange = state.schoolYearRange,
                    latestSelectableDate = state.latestSelectableDate,
                    canGoPreviousWeek = state.canGoPreviousWeek,
                    canGoNextWeek = state.canGoNextWeek,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onToday = onToday,
                    onDayClick = { date ->
                        coroutineScope.launch {
                            selectableSchoolDateIndex(firstPagerDate, date)?.let { page ->
                                pagerState.animateScrollToPage(page)
                            }
                        }
                    },
                )
                if (state.errorMessage != null) {
                    ErrorState(
                        title = "Nie udało się pobrać frekwencji",
                        message = state.errorMessage,
                        actionLabel = "Spróbuj ponownie",
                        onAction = onRetry,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                            key = { page -> selectableSchoolDateAt(firstPagerDate, page) },
                        ) { page ->
                            val pageDate = selectableSchoolDateAt(firstPagerDate, page)
                            DayAttendancePage(
                                date = pageDate,
                                entries = if (pageDate == state.selectedDate) {
                                    state.selectedDayEntries
                                } else {
                                    state.weekEntries.filter { it.date == pageDate }
                                },
                            )
                        }

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

}

@Composable
private fun AttendanceJournalControls(
    displayedWeek: WeekRange,
    selectedDate: LocalDate,
    schoolYearRange: pl.usagi.edziennik.domain.time.SchoolYearRange,
    latestSelectableDate: LocalDate,
    canGoPreviousWeek: Boolean,
    canGoNextWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    val todayTarget = initialAttendanceDate()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                /*Box(
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
                        Text("Dzisiaj")
                    }
                }*/
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
                    text = displayedWeek.schoolWeekLabel(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
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

        DaySelector(
            week = displayedWeek,
            selectedDate = selectedDate,
            todayDate = todayTarget,
            schoolYearRange = schoolYearRange,
            latestSelectableDate = latestSelectableDate,
            onDayClick = onDayClick,
        )
    }
}

@Composable
private fun DaySelector(
    week: WeekRange,
    selectedDate: LocalDate,
    todayDate: LocalDate,
    schoolYearRange: pl.usagi.edziennik.domain.time.SchoolYearRange,
    latestSelectableDate: LocalDate,
    onDayClick: (LocalDate) -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        week.schoolDays().forEach { date ->
            val enabled = date.isSelectableSchoolDate(schoolYearRange) && !date.isAfter(latestSelectableDate)
            DaySelectorItem(
                date = date,
                selected = enabled && date == selectedDate,
                today = date == todayDate,
                enabled = enabled,
                onClick = { onDayClick(date) },
                modifier = Modifier.weight(1f),
            )
            if (date.dayOfWeek != DayOfWeek.FRIDAY) {
                Spacer(modifier = Modifier.width(spacing.xs))
            }
        }
    }
}

@Composable
private fun DaySelectorItem(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = EDziennikTheme.spacing
    val container = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val content =
        when {
            selected -> MaterialTheme.colorScheme.onPrimary
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = date.dayOfWeek.shortPolishName(),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = content,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        today && selected -> MaterialTheme.colorScheme.onPrimary
                        today -> MaterialTheme.colorScheme.secondary
                        else -> Color.Transparent
                    },
                ),
        )
    }
}

@Composable
private fun DayAttendancePage(
    date: LocalDate,
    entries: List<AttendanceEntry>,
) {
    val spacing = EDziennikTheme.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item {
            Text(
                text = "${date.dayOfWeek.polishName()} · ${date.dayMonthLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entries.isEmpty()) {
            item {
                EmptyState(
                    title = "Brak danych o frekwencji tego dnia",
                    message = "",
                )
            }
        } else {
            items(
                entries,
                key = { entry -> "${entry.date}-${entry.lessonTimeId}-${entry.classLessonId}-${entry.startTime}" }) { entry ->
                AttendanceRow(entry = entry)
            }
            item {
                Spacer(modifier = Modifier.height(spacing.md))
            }
        }
    }
}

@Composable
private fun AttendanceRow(entry: AttendanceEntry) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(
            modifier = Modifier.width(56.dp),
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
                text = entry.lessonNumber?.toString().orEmpty(),
                modifier = Modifier.widthIn(min = 28.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        AttendanceTimeline()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = entry.subject ?: "Zajęcia",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.teacher
                ?.takeIf { it.isNotBlank() }
                ?.let { teacher ->
                Text(
                    text = teacher,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = entry.status.label(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = entry.status.color(),
            )
            entry.excuseStatus?.let { excuseStatus ->
                Text(
                    text = excuseStatus.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttendanceTimeline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(64.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
    )
}

@Composable
private fun AttendanceStatus.color() =
    if (this == AttendanceStatus.Present) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun DayOfWeek.shortPolishName(): String =
    when (this) {
        DayOfWeek.MONDAY -> "Pn"
        DayOfWeek.TUESDAY -> "Wt"
        DayOfWeek.WEDNESDAY -> "Śr"
        DayOfWeek.THURSDAY -> "Czw"
        DayOfWeek.FRIDAY -> "Pt"
        DayOfWeek.SATURDAY -> "Sb"
        DayOfWeek.SUNDAY -> "Nd"
    }

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
