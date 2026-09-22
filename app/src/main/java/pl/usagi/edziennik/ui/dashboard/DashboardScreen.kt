package pl.usagi.edziennik.ui.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.network.messages.MessagesDiagnostics
import pl.usagi.edziennik.network.session.LoggedInUser
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.messages.dashboardUnreadBadgeLabel
import pl.usagi.edziennik.ui.theme.EDziennikTheme

@Composable
fun DashboardRoute(
    loggedInUser: LoggedInUser?,
    onOpenTimetable: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenSchoolTasks: () -> Unit,
    onOpenMessages: () -> Unit,
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    DashboardScreen(
        state = state,
        loggedInUser = loggedInUser,
        onStudentSelected = viewModel::selectStudent,
        onShowTodayTimetable = viewModel::showTodayTimetable,
        onShowTomorrowTimetable = viewModel::showTomorrowTimetable,
        onOpenTimetable = onOpenTimetable,
        onOpenAttendance = onOpenAttendance,
        onOpenGrades = onOpenGrades,
        onOpenSchoolTasks = onOpenSchoolTasks,
        onOpenMessages = onOpenMessages,
        onLogout = onLogout,
    )
}

@Composable
private fun DashboardScreen(
    state: DashboardUiState,
    loggedInUser: LoggedInUser?,
    onStudentSelected: (Student) -> Unit,
    onShowTodayTimetable: () -> Unit,
    onShowTomorrowTimetable: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenSchoolTasks: () -> Unit,
    onOpenMessages: () -> Unit,
    onLogout: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    MessagesDiagnostics.debug("Logged user: ${loggedInUser}")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EDziennikTopBar(
                activeStudent = state.activeStudent,
                students = state.students,
                onStudentSelected = onStudentSelected,
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Wyloguj")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(
                        text = dashboardGreeting(loggedInUser?.name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = state.today.dashboardHeaderLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                DashboardQuickNavigation(
                    onOpenTimetable = onOpenTimetable,
                    onOpenAttendance = onOpenAttendance,
                    onOpenGrades = onOpenGrades,
                    onOpenSchoolTasks = onOpenSchoolTasks,
                    onOpenMessages = onOpenMessages,
                    unreadMessagesBadge = state.unreadMessagesCount.data.dashboardUnreadBadgeLabel(),
                )
            }

            item {
                TimetableSection(
                    selectedDay = state.selectedTimetableDay,
                    selectedDate = state.selectedTimetableDate,
                    section = state.selectedTimetable,
                    onToggleDay = when (state.selectedTimetableDay) {
                        DashboardTimetableDay.Today -> onShowTomorrowTimetable
                        DashboardTimetableDay.Tomorrow -> onShowTodayTimetable
                    },
                )
            }

            state.todayAttendance.data?.takeIf { it.isNotEmpty() }?.let { entries ->
                item {
                    AttendanceSection(
                        entries = entries,
                        onOpenAttendance = onOpenAttendance,
                    )
                }
            }

            item {
                GradesSection(
                    section = state.recentGrades,
                    onOpenGrades = onOpenGrades,
                )
            }

            item {
                SchoolTasksSection(
                    section = state.upcomingTasks,
                    onOpenSchoolTasks = onOpenSchoolTasks,
                )
            }

            item {
                Spacer(modifier = Modifier.height(spacing.md))
            }
        }
    }
}

@Composable
private fun DashboardQuickNavigation(
    onOpenTimetable: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenSchoolTasks: () -> Unit,
    onOpenMessages: () -> Unit,
    unreadMessagesBadge: String?,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.md),
    ) {
        item {
            QuickNavigationItem(
                label = "Wiadomości",
                icon = Icons.Outlined.Mail,
                badge = unreadMessagesBadge,
                onClick = onOpenMessages,
            )
        }
        item {
            QuickNavigationItem(
                label = "Plan",
                icon = Icons.Outlined.CalendarMonth,
                onClick = onOpenTimetable,
            )
        }
        item {
            QuickNavigationItem(
                label = "Frekwencja",
                icon = Icons.Outlined.CheckCircle,
                onClick = onOpenAttendance,
            )
        }
        item {
            QuickNavigationItem(
                label = "Zadania",
                icon = Icons.AutoMirrored.Outlined.Assignment,
                onClick = onOpenSchoolTasks,
            )
        }
        item {
            QuickNavigationItem(
                label = "Oceny",
                icon = Icons.Outlined.Star,
                onClick = onOpenGrades,
            )
        }
    }
}

@Composable
private fun QuickNavigationItem(
    label: String,
    icon: ImageVector,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    TextButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
                badge?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(start = 10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun TimetableSection(
    selectedDay: DashboardTimetableDay,
    selectedDate: java.time.LocalDate,
    section: DashboardSection<List<DashboardLesson>>,
    onToggleDay: () -> Unit,
) {
    DashboardSectionLayout(
        title = "PLAN LEKCJI",
        icon = Icons.Outlined.CalendarMonth,
    ) {
        DashboardTimetableDaySelector(
            selectedDay = selectedDay,
            onTodaySelected = {
                if (selectedDay != DashboardTimetableDay.Today) {
                    onToggleDay()
                }
            },
            onTomorrowSelected = {
                if (selectedDay != DashboardTimetableDay.Tomorrow) {
                    onToggleDay()
                }
            },
        )
//        if (selectedDay == DashboardTimetableDay.Tomorrow) {
//            Text(
//                text = selectedDate.dashboardHeaderLabel(),
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant,
//            )
//        }
        SectionBody(
            section = section,
            emptyText = "Brak lekcji",
        ) { lessons ->
            Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm)) {
                lessons.forEach { lesson ->
                    DashboardLessonRow(lesson = lesson)
                }
            }
        }
    }
}

@Composable
private fun DashboardTimetableDaySelector(
    selectedDay: DashboardTimetableDay,
    onTodaySelected: () -> Unit,
    onTomorrowSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DashboardTimetableDaySelectorItem(
            label = "Dzisiaj",
            selected = selectedDay == DashboardTimetableDay.Today,
            onClick = onTodaySelected,
            modifier = Modifier.weight(1f),
        )
        DashboardTimetableDaySelectorItem(
            label = "Jutro",
            selected = selectedDay == DashboardTimetableDay.Tomorrow,
            onClick = onTomorrowSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DashboardTimetableDaySelectorItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = EDziennikTheme.spacing

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                enabled = !selected,
                onClick = onClick,
            )
            .padding(
                horizontal = spacing.md,
                vertical = spacing.xs,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                lineHeight = MaterialTheme.typography.labelLarge.fontSize,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun DashboardLessonRow(lesson: DashboardLesson) {
    val spacing = EDziennikTheme.spacing
    val contentColor = if (lesson.isCancelled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = lesson.startTime,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (lesson.isCurrent) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.54f)),
                )
            }
        }
        Text(
            text = lesson.subject,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (lesson.isCancelled) FontWeight.Normal else FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = lesson.room.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun AttendanceSection(
    entries: List<DashboardAttendanceEntry>,
    onOpenAttendance: () -> Unit,
) {
    DashboardSectionLayout(
        title = "FREKWENCJA",
        icon = Icons.Outlined.CheckCircle,
        action = "Wszystkie",
        onAction = onOpenAttendance,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm)) {
            entries.forEach { entry ->
                DashboardAttendanceRow(entry = entry)
            }
        }
    }
}

@Composable
private fun DashboardAttendanceRow(entry: DashboardAttendanceEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.startTime,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.subject,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.statusLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun GradesSection(
    section: DashboardSection<List<DashboardGrade>>,
    onOpenGrades: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    DashboardSectionLayout(
        title = "NAJNOWSZE OCENY",
        icon = Icons.Outlined.Star,
        action = "Wszystkie",
        onAction = onOpenGrades,
    ) {
        SectionBody(
            section = section,
            emptyText = "Brak ocen",
        ) { grades ->
            Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm)) {
                grades.forEach { grade ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DashboardGradeBadge(value = grade.value)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (grade.changedSinceLastLogin) {
                                Box(
                                    modifier = Modifier
                                        .padding(spacing.xs)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                )
                            }
                            Text(
                                text = grade.subjectName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardGradeBadge(value: String) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SchoolTasksSection(
    section: DashboardSection<List<DashboardTask>>,
    onOpenSchoolTasks: () -> Unit,
) {
    DashboardSectionLayout(
        title = "SPRAWDZIANY I ZADANIA",
        icon = Icons.AutoMirrored.Outlined.Assignment,
        action = "Wszystkie",
        onAction = onOpenSchoolTasks,
    ) {
        SectionBody(
            section = section,
            emptyText = "Brak sprawdzianów i zadań",
        ) { tasks ->
            Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.md)) {
                tasks.forEach { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs)) {
                        Text(
                            text = task.dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DashboardTaskRow(task = task)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTaskRow(
    task: DashboardTask,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(44.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs),
        ) {
            Text(
                text = listOfNotNull(task.typeLabel, task.subject).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            task.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DashboardSectionLayout(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (action != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary
                    ),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(action)
                }

            }
        }
        content()
    }
}

@Composable
private fun <T> SectionBody(
    section: DashboardSection<List<T>>,
    emptyText: String,
    content: @Composable (List<T>) -> Unit,
) {
    when {
        section.isLoading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
        section.errorMessage != null -> {
            Text(
                text = section.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        section.data.isNullOrEmpty() -> {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> content(section.data)
    }
}
