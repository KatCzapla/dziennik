package pl.usagi.edziennik.ui.dashboard

import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.ui.attendance.label
import pl.usagi.edziennik.ui.grades.recentGrades
import pl.usagi.edziennik.ui.schooltasks.typeLabel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun dashboardGreeting(name: String?): String =
    name?.trim()
        ?.split(Regex("\\s+"))
        ?.getOrNull(1)
        ?.takeIf(String::isNotEmpty)
        ?.let { "Dzień dobry, $it" }
        ?: "Dzień dobry"

data class DashboardLesson(
    val startTime: String,
    val subject: String,
    val room: String?,
    val isCurrent: Boolean,
    val isCancelled: Boolean,
)

data class DashboardGrade(
    val value: String,
    val subjectName: String,
    val changedSinceLastLogin: Boolean
)

data class DashboardAttendanceEntry(
    val startTime: String,
    val subject: String,
    val statusLabel: String,
)

data class DashboardTask(
    val dateLabel: String,
    val typeLabel: String,
    val subject: String?,
    val description: String?,
)

fun List<TimetableEntry>.toDashboardLessons(
    displayedDate: LocalDate,
    now: LocalDateTime,
): List<DashboardLesson> =
    filter { it.date == displayedDate }
        .sortedBy { it.startTime }
        .map { entry ->
            DashboardLesson(
                startTime = entry.startTime.format(timeFormatter),
                subject = entry.subject,
                room = entry.room,
                isCurrent = entry.isCurrentLesson(displayedDate, now),
                isCancelled = entry.isCancelled,
            )
        }

fun TimetableEntry.isCurrentLesson(
    displayedDate: LocalDate,
    now: LocalDateTime,
): Boolean =
    !isCancelled &&
        date == displayedDate &&
        now.toLocalDate() == displayedDate &&
        !now.toLocalTime().isBefore(startTime) &&
        now.toLocalTime().isBefore(endTime)

fun Grades.toDashboardGrades(limit: Int = 3): List<DashboardGrade> =
    recentGrades(limit)
        .map { recentGrade ->
            DashboardGrade(
                value = recentGrade.grade.value,
                subjectName = recentGrade.subjectName,
                changedSinceLastLogin = recentGrade.grade.changedSinceLastLogin,
            )
        }

fun List<AttendanceEntry>.toDashboardAttendanceEntries(
    today: LocalDate,
): List<DashboardAttendanceEntry> =
    filter { it.date == today }
        .sortedBy { it.startTime }
        .map { entry ->
            DashboardAttendanceEntry(
                startTime = entry.startTime.format(timeFormatter),
                subject = entry.subject ?: "Zajęcia",
                statusLabel = entry.status.dashboardLabel(),
            )
        }

fun List<SchoolTask>.toDashboardTasks(
    today: LocalDate,
    limit: Int = 3,
): List<DashboardTask> =
    filter { !it.date.isBefore(today) }
        .sortedWith(compareBy<SchoolTask> { it.date }.thenBy { it.id })
        .take(limit)
        .map { task ->
            DashboardTask(
                dateLabel = task.date.dashboardRelativeLabel(today),
                typeLabel = task.type.typeLabel(),
                subject = task.subject,
                description = task.details?.description?.takeIf { it.isNotBlank() },
            )
        }

fun LocalDate.dashboardRelativeLabel(today: LocalDate): String =
    when (this) {
        today -> "Dzisiaj"
        today.plusDays(1) -> "Jutro"
        else -> dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, polishLocale)
            .replaceFirstChar { char -> char.titlecase(polishLocale) }
    }

fun LocalDate.dashboardHeaderLabel(): String =
    "${dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, polishLocale).replaceFirstChar { it.titlecase(polishLocale) }}, ${format(dayMonthFormatter)}"

fun SchoolTaskType.dashboardLabel(): String = typeLabel()

fun AttendanceStatus.dashboardLabel(): String = label()

private val polishLocale = Locale.forLanguageTag("pl-PL")
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", polishLocale)
private val timeFormatter = DateTimeFormatter.ofPattern("H:mm")
