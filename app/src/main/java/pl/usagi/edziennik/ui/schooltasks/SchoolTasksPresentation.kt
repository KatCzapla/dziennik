package pl.usagi.edziennik.ui.schooltasks

import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.time.SchoolYearRange
import pl.usagi.edziennik.domain.time.coerceInSchoolYear
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.ui.timetable.dayMonthLabel
import java.time.DayOfWeek
import java.time.LocalDate

data class SchoolTasksDayGroup(
    val date: LocalDate,
    val tasks: List<SchoolTask>,
)

data class SchoolTaskDetailLine(
    val label: String,
    val value: String,
)

fun initialSchoolTasksDate(
    today: LocalDate = LocalDate.now(),
    schoolYearRange: SchoolYearRange = currentSchoolYearRange(today),
): LocalDate {
    val effectiveDate = when (today.dayOfWeek) {
        DayOfWeek.SATURDAY -> today.plusDays(2)
        DayOfWeek.SUNDAY -> today.plusDays(1)
        else -> today
    }
    return effectiveDate.coerceInSchoolYear(schoolYearRange)
}

fun List<SchoolTask>.groupBySchoolDay(): List<SchoolTasksDayGroup> =
    groupBy { it.date }
        .toSortedMap()
        .map { (date, tasks) ->
            SchoolTasksDayGroup(
                date = date,
                tasks = tasks.sortedWith(compareBy<SchoolTask> { it.type.sortOrder() }.thenBy { it.id }),
            )
        }

fun SchoolTaskType.typeLabel(): String =
    when (this) {
        SchoolTaskType.Homework -> "Zadanie domowe"
        SchoolTaskType.Quiz -> "Kartkówka"
        is SchoolTaskType.Unknown -> "Inne"
    }

fun SchoolTask.presentationDetailLines(): List<SchoolTaskDetailLine> {
    val details = details ?: return emptyList()

    return listOfNotNull(
        details.description?.takeIf { it.isNotBlank() }?.let { description ->
            SchoolTaskDetailLine(
                label = "Opis",
                value = description,
            )
        },
        details.responseDeadline?.let { deadline ->
            SchoolTaskDetailLine(
                label = "Termin",
                value = deadline.dayMonthLabel(),
            )
        },
    )
}

private fun SchoolTaskType.sortOrder(): Int =
    when (this) {
        SchoolTaskType.Quiz -> 0
        SchoolTaskType.Homework -> 1
        is SchoolTaskType.Unknown -> 2
    }
