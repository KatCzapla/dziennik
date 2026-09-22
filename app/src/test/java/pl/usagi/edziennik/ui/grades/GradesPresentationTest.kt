package pl.usagi.edziennik.ui.grades

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.GradeColumn
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.GradesSettings
import pl.usagi.edziennik.domain.SubjectGrades
import java.time.LocalDate
import java.time.OffsetDateTime

class GradesPresentationTest {
    @Test
    fun recentGradesAreSortedByDateDescendingAcrossSubjects() {
        val grades = Grades(
            subjects = listOf(
                subject(position = 2, name = "Matematyka", grade = grade("4", LocalDate.of(2026, 9, 10))),
                subject(position = 1, name = "Polski", grade = grade("5", LocalDate.of(2026, 9, 11))),
            ),
            settings = settings(),
        )

        val recent = grades.recentGrades()

        assertEquals("Polski", recent[0].subjectName)
        assertEquals(LocalDate.of(2026, 9, 11), recent[0].grade.date)
        assertEquals("Matematyka", recent[1].subjectName)
        assertEquals(LocalDate.of(2026, 9, 10), recent[1].grade.date)
    }

    @Test
    fun choosesPeriodContainingNow() {
        val selected = chooseClassificationPeriod(
            periods = periods(),
            now = OffsetDateTime.parse("2027-03-15T12:00:00+01:00"),
        )

        assertEquals(2, selected?.number)
    }

    @Test
    fun fallsBackToFirstPeriodBeforeAllPeriods() {
        val selected = chooseClassificationPeriod(
            periods = periods(),
            now = OffsetDateTime.parse("2026-08-20T12:00:00+02:00"),
        )

        assertEquals(1, selected?.number)
    }

    @Test
    fun fallsBackToLastPeriodAfterAllPeriods() {
        val selected = chooseClassificationPeriod(
            periods = periods(),
            now = OffsetDateTime.parse("2027-09-01T12:00:00+02:00"),
        )

        assertEquals(2, selected?.number)
    }

    @Test
    fun fallsBackToPreviousPeriodBetweenPeriods() {
        val selected = chooseClassificationPeriod(
            periods = listOf(
                period(id = 1, number = 1, from = "2026-09-01T00:00:00+02:00", to = "2026-12-31T23:59:59+01:00"),
                period(id = 2, number = 2, from = "2027-02-01T00:00:00+01:00", to = "2027-06-30T23:59:59+02:00"),
            ),
            now = OffsetDateTime.parse("2027-01-15T12:00:00+01:00"),
        )

        assertEquals(1, selected?.number)
    }

    private fun periods() = listOf(
        period(id = 123, number = 1, from = "2026-09-01T00:00:00+02:00", to = "2027-01-31T23:59:59+01:00"),
        period(id = 124, number = 2, from = "2027-02-01T00:00:00+01:00", to = "2027-08-31T23:59:59+02:00"),
    )

    private fun period(
        id: Int,
        number: Int,
        from: String,
        to: String,
    ) = ClassificationPeriod(
        id = id,
        number = number,
        startDate = OffsetDateTime.parse(from),
        endDate = OffsetDateTime.parse(to),
    )

    private fun subject(
        position: Int,
        name: String,
        grade: Grade,
    ) = SubjectGrades(
        subjectName = name,
        position = position,
        includeGradeWeight = true,
        teachers = emptyList(),
        columns = listOf(GradeColumn(id = position, category = "Kategoria", name = "Projekt", grades = listOf(grade))),
        practicalExamGrade = null,
        oralExamGrade = null,
        proposedExamGrade = null,
        finalExamGrade = null,
        pointsSum = "",
        pointsSumAllTerms = "",
        average = null,
        averageAllTerms = null,
        proposedTermGrade = null,
        proposedTermPoints = "",
        termGrade = null,
        termPoints = "",
        gradesSummary = null,
    )

    private fun grade(
        value: String,
        date: LocalDate,
    ) = Grade(
        value = value,
        date = date,
        columnId = 1,
        category = "Kategoria",
        columnName = "Projekt",
        weight = null,
        color = null,
        teacher = null,
        changedSinceLastLogin = false,
        correctedGradeId = null,
    )

    private fun settings() = GradesSettings(
        showAverageAndPoints = false,
        adultStudent = false,
        descriptiveGrade = false,
        lastClassificationPeriod = false,
    )
}
