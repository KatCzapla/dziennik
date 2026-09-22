package pl.usagi.edziennik.domain

import kotlinx.serialization.json.JsonElement
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class ClassificationPeriod(
    val id: Int,
    val number: Int,
    val startDate: OffsetDateTime,
    val endDate: OffsetDateTime,
)

data class Grades(
    val subjects: List<SubjectGrades>,
    val settings: GradesSettings,
)

data class SubjectGrades(
    val subjectName: String,
    val position: Int,
    val includeGradeWeight: Boolean,
    val teachers: List<String>,
    val columns: List<GradeColumn>,
    val practicalExamGrade: String?,
    val oralExamGrade: String?,
    val proposedExamGrade: String?,
    val finalExamGrade: String?,
    val pointsSum: String?,
    val pointsSumAllTerms: String?,
    val average: BigDecimal?,
    val averageAllTerms: BigDecimal?,
    val proposedTermGrade: String?,
    val proposedTermPoints: String?,
    val termGrade: String?,
    val termPoints: String?,
    val gradesSummary: JsonElement?,
)

data class GradeColumn(
    val id: Int,
    val category: String,
    val name: String,
    val grades: List<Grade>,
)

data class Grade(
    val value: String,
    val date: LocalDate,
    val columnId: Int,
    val category: String,
    val columnName: String,
    val weight: BigDecimal?,
    val color: Int?,
    val teacher: String?,
    val changedSinceLastLogin: Boolean,
    val correctedGradeId: Int?,
)

data class GradesSettings(
    val showAverageAndPoints: Boolean,
    val adultStudent: Boolean,
    val descriptiveGrade: Boolean,
    val lastClassificationPeriod: Boolean,
)
