package pl.usagi.edziennik.ui.grades

import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.SubjectGrades
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RecentGrade(
    val subjectPosition: Int,
    val subjectName: String,
    val columnName: String,
    val grade: Grade,
    val gradeIndex: Int,
)

fun Grades.recentGrades(limit: Int = 4): List<RecentGrade> =
    subjects
        .flatMap { subject ->
            subject.columns.flatMap { column ->
                column.grades.mapIndexed { index, grade ->
                    RecentGrade(
                        subjectPosition = subject.position,
                        subjectName = subject.subjectName,
                        columnName = column.name,
                        grade = grade,
                        gradeIndex = index,
                    )
                }
            }
        }
        .sortedByDescending { it.grade.date }
        .take(limit)

fun Grades.subjectByPosition(position: Int): SubjectGrades? =
    subjects.firstOrNull { it.position == position }

fun SubjectGrades.gradeCount(): Int =
    columns.sumOf { it.grades.size }

fun SubjectGrades.hasPresentableGrades(showAverageAndPoints: Boolean): Boolean =
    gradeCount() > 0 ||
        listOf(
            proposedTermGrade,
            termGrade,
            practicalExamGrade,
            oralExamGrade,
            proposedExamGrade,
            finalExamGrade,
        ).any { !it.isNullOrBlank() } ||
        (showAverageAndPoints && (
            average != null || averageAllTerms != null ||
                !pointsSum.isNullOrBlank() || !pointsSumAllTerms.isNullOrBlank()
            ))

fun SubjectGrades.latestGrades(limit: Int): List<Grade> =
    columns.flatMap { it.grades }
        .sortedByDescending { it.date }
        .take(limit)

fun chooseClassificationPeriod(
    periods: List<ClassificationPeriod>,
    now: OffsetDateTime,
): ClassificationPeriod? {
    val sortedPeriods = periods.sortedBy { it.startDate }
    return sortedPeriods.firstOrNull { period ->
        !period.startDate.isAfter(now) && !period.endDate.isBefore(now)
    } ?: when {
        sortedPeriods.isEmpty() -> null
        now.isBefore(sortedPeriods.first().startDate) -> sortedPeriods.first()
        now.isAfter(sortedPeriods.last().endDate) -> sortedPeriods.last()
        else -> sortedPeriods.lastOrNull { !it.startDate.isAfter(now) } ?: sortedPeriods.first()
    }
}

fun ClassificationPeriod.shortLabel(): String =
    when (number) {
        1 -> "I okres"
        2 -> "II okres"
        else -> "$number okres"
    }

fun BigDecimal.formatGradeNumber(): String =
    stripTrailingZeros().toPlainString()

private val polishLocale = Locale.forLanguageTag("pl-PL")
val gradeDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", polishLocale)
val shortGradeDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", polishLocale)
