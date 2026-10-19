package pl.usagi.edziennik.mapper

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.GradeColumn
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.GradesSettings
import pl.usagi.edziennik.domain.SubjectGrades
import pl.usagi.edziennik.dto.ClassificationPeriodDto
import pl.usagi.edziennik.dto.GradeColumnDto
import pl.usagi.edziennik.dto.GradeDto
import pl.usagi.edziennik.dto.GradesResponseDto
import pl.usagi.edziennik.dto.GradesSettingsDto
import pl.usagi.edziennik.dto.SubjectGradesDto
import pl.usagi.edziennik.util.blankToNull
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun ClassificationPeriodDto.toDomain(): ClassificationPeriod =
    ClassificationPeriod(
        id = id,
        number = periodNumber,
        startDate = OffsetDateTime.parse(dateFrom),
        endDate = OffsetDateTime.parse(dateTo),
    )

fun GradeDto.toDomain(): Grade =
    Grade(
        value = value,
        date = LocalDate.parse(date, polishGradeDateFormatter),
        columnId = columnId,
        category = category,
        columnName = columnName,
        weight = weight.toBigDecimalOrNull(),
        color = color,
        teacher = teacher.blankToNull(),
        changedSinceLastLogin = changedSinceLastLogin,
        correctedGradeId = correctedGradeId,
    )

fun GradeColumnDto.toDomain(): GradeColumn =
    GradeColumn(
        id = id,
        category = category,
        name = name,
        grades = grades.map { it.toDomain() },
    )

fun SubjectGradesDto.toDomain(): SubjectGrades =
    SubjectGrades(
        subjectName = subjectName,
        position = position,
        includeGradeWeight = includeGradeWeight,
        teachers = teachers,
        columns = columns.map { it.toDomain() },
        practicalExamGrade = practicalExamGrade.blankToNull(),
        oralExamGrade = oralExamGrade.blankToNull(),
        proposedExamGrade = proposedExamGrade.blankToNull(),
        finalExamGrade = finalExamGrade.blankToNull(),
        pointsSum = pointsSum.blankToNull(),
        pointsSumAllTerms = pointsSumAllTerms.blankToNull(),
        average = average.toBigDecimalOrNull(),
        averageAllTerms = averageAllTerms.toBigDecimalOrNull(),
        proposedTermGrade = proposedTermGrade.blankToNull(),
        proposedTermPoints = proposedTermPoints.blankToNull(),
        termGrade = termGrade.blankToNull(),
        termPoints = termPoints.blankToNull(),
        gradesSummary = gradesSummary,
    )

fun GradesSettingsDto.toDomain(): GradesSettings =
    GradesSettings(
        showAverageAndPoints = showAverageAndPoints,
        adultStudent = adultStudent,
        descriptiveGrade = descriptiveGrade,
        lastClassificationPeriod = lastClassificationPeriod,
    )

fun GradesResponseDto.toDomain(): Grades =
    Grades(
        subjects = subjects.map { it.toDomain() }.sortedBy { it.position },
        settings = settings.toDomain(),
    )

private fun JsonElement?.toBigDecimalOrNull(): BigDecimal? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.contentOrNull?.let(::runCatchingBigDecimal)?.getOrNull()
        ?: primitive.doubleOrNull?.let { BigDecimal.valueOf(it) }
}

private fun runCatchingBigDecimal(value: String): Result<BigDecimal> =
    runCatching { BigDecimal(value) }

private val polishGradeDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
