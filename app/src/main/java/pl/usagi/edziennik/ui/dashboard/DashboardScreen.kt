package pl.usagi.edziennik.ui.dashboard

import androidx.compose.ui.res.stringResource
import pl.usagi.edziennik.R
import pl.usagi.edziennik.domain.SchoolDayOff
import pl.usagi.edziennik.ui.components.DaysOffNotice

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.network.messages.MessagesDiagnostics
import pl.usagi.edziennik.network.session.LoggedInUser
import pl.usagi.edziennik.ui.messages.dashboardUnreadBadgeLabel
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import pl.usagi.edziennik.ui.navigation.GradeNavigationTarget
import pl.usagi.edziennik.ui.navigation.SchoolTaskNavigationTarget
import pl.usagi.edziennik.ui.timetable.dayMonthLabel

@Composable
fun DashboardRoute(
    loggedInUser: LoggedInUser?,
    onOpenTimetable: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenSchoolTasks: () -> Unit,
    onOpenGrade: (GradeNavigationTarget) -> Unit,
    onOpenTask: (SchoolTaskNavigationTarget) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenPayments: () -> Unit,
    showEnableNotifications: Boolean = false,
    onEnableNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    DashboardScreen(
        state = state,
        loggedInUser = loggedInUser,
        onShowTodayTimetable = viewModel::showTodayTimetable,
        onShowTomorrowTimetable = viewModel::showTomorrowTimetable,
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
}

@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    loggedInUser: LoggedInUser?,
    onShowTodayTimetable: () -> Unit,
    onShowTomorrowTimetable: () -> Unit,
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
    onOpenSettings: () -> Unit = {},
) {
    val spacing = EDziennikTheme.spacing

    MessagesDiagnostics.debug("Logged user: ${loggedInUser}")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = spacing.lg, end = spacing.md, top = spacing.sm, bottom = spacing.md),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    val name = dashboardGreetingName(loggedInUser?.name)
                    Text(
                        text = if (name == null) stringResource(R.string.dashboard_greeting_default)
                            else stringResource(R.string.dashboard_greeting_named, name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(state.today.dashboardHeaderLabel(), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            contentPadding = PaddingValues(vertical = spacing.sm),
        ) {
            item {
                DashboardQuickNavigation(
                    onOpenTimetable = onOpenTimetable,
                    onOpenAttendance = onOpenAttendance,
                    onOpenGrades = onOpenGrades,
                    onOpenSchoolTasks = onOpenSchoolTasks,
                    onOpenMessages = onOpenMessages,
                    onOpenPayments = onOpenPayments,
                    unreadMessagesBadge = state.unreadMessagesCount.data.dashboardUnreadBadgeLabel(),
                )
            }

            if (showEnableNotifications) {
                item {
                    TextButton(onClick = onEnableNotifications) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text("Włącz powiadomienia")
                    }
                }
            }

            item {
                TimetableSection(
                    selectedDay = state.selectedTimetableDay,
                    selectedDate = state.selectedTimetableDate,
                    section = state.selectedTimetable,
                    daysOff = state.selectedDaysOff,
                    daysOffLoading = state.daysOffLoading,
                    daysOffFailed = state.daysOffFailed,
                    showNoLessons = state.showNoLessons,
                    onOpenTimetable = onOpenTimetable,
                    onOpenAttendance = onOpenAttendance,
                    onToggleDay = when (state.selectedTimetableDay) {
                        DashboardTimetableDay.Today -> onShowTomorrowTimetable
                        DashboardTimetableDay.Tomorrow -> onShowTodayTimetable
                    },
                )
            }

            item {
                GradesSection(
                    section = state.recentGrades,
                    onOpenGrades = onOpenGrades,
                    onOpenGrade = onOpenGrade,
                )
            }

            item {
                SchoolTasksSection(
                    section = state.upcomingTasks,
                    onOpenSchoolTasks = onOpenSchoolTasks,
                    onOpenTask = onOpenTask,
                )
            }

            if (state.information.isLoading || state.information.errorMessage != null || !state.information.data.isNullOrEmpty()) {
                item(key = "information-${state.activeStudent?.diaryId}") {
                    InformationSection(section = state.information)
                }
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
    onOpenPayments: () -> Unit,
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
                label = "Opłaty",
                icon = Icons.Outlined.AccountBalance,
                onClick = onOpenPayments,
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
    daysOff: List<SchoolDayOff>,
    daysOffLoading: Boolean,
    daysOffFailed: Boolean,
    showNoLessons: Boolean,
    onOpenTimetable: () -> Unit,
    onOpenAttendance: () -> Unit,
    onToggleDay: () -> Unit,
) {
    DashboardSectionLayout(
        title = stringResource(R.string.dashboard_timetable_title),
        icon = Icons.Outlined.CalendarMonth,
        action = stringResource(R.string.dashboard_full_timetable),
        onAction = onOpenTimetable,
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
        if (daysOff.isNotEmpty() || daysOffLoading || daysOffFailed) {
            DaysOffNotice(daysOff, daysOffLoading, daysOffFailed)
        }
        if (section.isLoading || section.errorMessage != null || !section.data.isNullOrEmpty() || showNoLessons) {
            SectionBody(
                section = section,
                emptyText = stringResource(R.string.dashboard_no_lessons),
            ) { lessons ->
                Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm)) {
                    lessons.forEach { lesson ->
                        DashboardLessonRow(lesson = lesson)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ){
        DashboardTextLink(
            text = stringResource(R.string.dashboard_attendance_link),
            onClick = onOpenAttendance,
        )
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
            label = stringResource(R.string.dashboard_today),
            selected = selectedDay == DashboardTimetableDay.Today,
            onClick = onTodaySelected,
            modifier = Modifier.weight(1f),
        )
        DashboardTimetableDaySelectorItem(
            label = stringResource(R.string.dashboard_tomorrow),
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lesson.subject,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (lesson.isCancelled) FontWeight.Normal else FontWeight.Medium,
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
//            Spacer(modifier = Modifier.height(spacing.xs))
            if (lesson.isCancelled) {
                Text(
                    text = stringResource(R.string.dashboard_lesson_cancelled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (lesson.isSubstitution) {
                Text(
                    text = stringResource(R.string.dashboard_lesson_substitution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            lesson.attendanceStatus?.let { DashboardAttendanceStatus(it) }
        }
        Text(
            text = lesson.room.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun DashboardAttendanceStatus(status: AttendanceStatus) {
    val isPresent = status == AttendanceStatus.Present
    val isLate = status == AttendanceStatus.Late || status == AttendanceStatus.ExcusedLate
    val isAbsent = status == AttendanceStatus.Absent || status == AttendanceStatus.ExcusedAbsence ||
        status == AttendanceStatus.SchoolRelatedAbsence
    val color = when {
        isAbsent -> MaterialTheme.colorScheme.error
        isLate -> MaterialTheme.colorScheme.secondary
        isPresent -> Color(0xFF3AB36C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when (status) {
        AttendanceStatus.Present -> stringResource(R.string.dashboard_attendance_present)
        AttendanceStatus.Absent -> stringResource(R.string.dashboard_attendance_absent)
        AttendanceStatus.ExcusedAbsence -> stringResource(R.string.dashboard_attendance_excused_absence)
        AttendanceStatus.Late -> stringResource(R.string.dashboard_attendance_late)
        AttendanceStatus.ExcusedLate -> stringResource(R.string.dashboard_attendance_excused_late)
        AttendanceStatus.SchoolRelatedAbsence -> stringResource(R.string.dashboard_attendance_school_absence)
        AttendanceStatus.Released -> stringResource(R.string.dashboard_attendance_released)
        is AttendanceStatus.Unknown -> status.description ?: status.rawValue?.let {
            stringResource(R.string.dashboard_attendance_unknown_value, it)
        } ?: stringResource(R.string.dashboard_attendance_unknown)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                isLate -> Icons.Outlined.Schedule
                isAbsent -> Icons.Outlined.Cancel
                status is AttendanceStatus.Unknown -> Icons.AutoMirrored.Outlined.HelpOutline
                else -> Icons.Outlined.CheckCircle
            },
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun GradesSection(
    section: DashboardSection<List<DashboardGrade>>,
    onOpenGrades: () -> Unit,
    onOpenGrade: (GradeNavigationTarget) -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    DashboardSectionLayout(
        title = "NAJNOWSZE OCENY",
        icon = Icons.Outlined.Star,
        action = stringResource(R.string.dashboard_all),
        onAction = onOpenGrades,
    ) {
        SectionBody(
            section = section,
            emptyText = "Brak ocen",
        ) { grades ->
            Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm)) {
                grades.forEach { grade ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenGrade(grade.target) },
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
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InformationSection(section: DashboardSection<List<DashboardInformation>>) {
    val spacing = EDziennikTheme.spacing
    DashboardSectionLayout(title = "INFORMACJE", icon = Icons.Outlined.Info) {
        SectionBody(section = section, emptyText = "") { information ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                information.forEach { entry ->
                    var expanded by rememberSaveable(entry.subject, entry.date.toString(), entry.content) {
                        mutableStateOf(false)
                    }

                    Column(
                        modifier = Modifier.padding(bottom = if (expanded) spacing.md else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded },
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.width(68.dp)) {
                                Text(
                                    text = entry.date.dayMonthLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = entry.date.year.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(44.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
                            )
                            Text(
                                text = entry.subject,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (expanded) "Zwiń treść" else "Rozwiń treść",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (expanded) {
                            Text(
                                text = entry.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolTasksSection(
    section: DashboardSection<List<DashboardTask>>,
    onOpenSchoolTasks: () -> Unit,
    onOpenTask: (SchoolTaskNavigationTarget) -> Unit,
) {
    DashboardSectionLayout(
        title = "SPRAWDZIANY I ZADANIA",
        icon = Icons.AutoMirrored.Outlined.Assignment,
        action = stringResource(R.string.dashboard_all),
        onAction = onOpenSchoolTasks,
    ) {
        SectionBody(
            section = section,
            emptyText = "Brak sprawdzianów i zadań",
        ) { tasks ->
            Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.md)) {
                tasks.forEach { task ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTask(task.target) },
                        verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs),
                    ) {
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
                modifier = Modifier.weight(1f),
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
                DashboardTextLink(text = action, onClick = onAction)
            }
        }
        content()
    }
}

@Composable
internal fun DashboardTextLink(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Outlined.ArrowOutward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(11.dp),
        )
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
