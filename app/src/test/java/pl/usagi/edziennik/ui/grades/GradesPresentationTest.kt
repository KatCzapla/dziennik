package pl.usagi.edziennik.ui.grades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    @Test fun subjectGradesAreGloballySortedAcrossColumns() {
        val older = grade("3", LocalDate.of(2026, 9, 10))
        val newest = grade("5", LocalDate.of(2026, 9, 14))
        val middle = grade("4", LocalDate.of(2026, 9, 12))
        val data = subject(1, "Matematyka", older).copy(columns = listOf(
            GradeColumn(1, "Sprawdzian", "Algebra", listOf(older, newest)),
            GradeColumn(2, "Kartkówka", "Geometria", listOf(middle)),
        ))

        val sorted = data.gradesByDate()
        assertEquals(listOf(newest, middle, older), sorted.map { it.second.value })
        assertEquals(listOf(1, 2, 1), sorted.map { it.first.id })
        assertEquals(listOf(1, 0, 0), sorted.map { it.second.index })
        assertEquals(listOf(older, newest), data.columns[0].grades)
    }

    @Test fun equalDatesPreserveApiColumnAndGradeOrderIncludingDuplicates() {
        val date = LocalDate.of(2026, 9, 10)
        val first = grade("4", date)
        val second = grade("5", date)
        val data = subject(1, "Matematyka", first).copy(columns = listOf(
            GradeColumn(9, "A", "Pierwsza", listOf(first, second, first)),
            GradeColumn(2, "B", "Druga", listOf(second, first)),
        ))

        val sorted = data.gradesByDate()
        assertEquals(listOf(first, second, first, second, first), sorted.map { it.second.value })
        assertEquals(listOf(9 to 0, 9 to 1, 9 to 2, 2 to 0, 2 to 1),
            sorted.map { it.first.id to it.second.index })
    }

    @Test fun descriptiveAndNonNumericGradesAreSortedOnlyByDate() {
        val descriptive = grade("Bardzo dobrze opanował materiał", LocalDate.of(2026, 9, 11))
        val nonNumeric = grade("np", LocalDate.of(2026, 9, 14))
        val symbolic = grade("+", LocalDate.of(2026, 9, 12))
        val data = subject(1, "Polski", descriptive).copy(columns = listOf(
            GradeColumn(1, "Opisowa", "Praca", listOf(descriptive, nonNumeric)),
            GradeColumn(2, "Aktywność", "Lekcja", listOf(symbolic)),
        ))
        assertEquals(listOf(nonNumeric, symbolic, descriptive), data.gradesByDate().map { it.second.value })
    }

    @Test fun sortingPreservesEntireGradeAndColumnWithoutChangingAverages() {
        val complete = grade("4+", LocalDate.of(2026, 9, 11)).copy(
            columnId = 8, category = "Kategoria API", columnName = "Opis API",
            weight = java.math.BigDecimal("3.5"), color = 123, teacher = "Nauczyciel",
            changedSinceLastLogin = true, correctedGradeId = 42,
        )
        val column = GradeColumn(8, "Kategoria kolumny", "Opis kolumny", listOf(complete))
        val data = subject(1, "Polski", complete).copy(columns = listOf(column),
            average = java.math.BigDecimal("4.2"), averageAllTerms = java.math.BigDecimal("4.1"))
        val sorted = data.gradesByDate().single()
        assertSame(column, sorted.first)
        assertSame(complete, sorted.second.value)
        assertEquals(java.math.BigDecimal("4.2"), data.average)
        assertEquals(java.math.BigDecimal("4.1"), data.averageAllTerms)
    }

    @Test fun emptyColumnsProduceEmptyGradeList() {
        val data = subject(1, "Polski", grade("4", LocalDate.of(2026, 9, 10)))
        assertEquals(emptyList<Any>(), data.copy(columns = emptyList()).gradesByDate())
        assertEquals(emptyList<Any>(), data.copy(columns = data.columns.map { it.copy(grades = emptyList()) }).gradesByDate())
    }

    @Test
    fun subjectWithGradeIsVisible() {
        val subject = subject(position = 1, name = "Matematyka", grade = grade("4", LocalDate.of(2026, 9, 10)))

        assertEquals(true, subject.hasPresentableGrades(showAverageAndPoints = false))
    }

    @Test
    fun completelyEmptySubjectIsHidden() {
        val subject = subject(position = 1, name = "Matematyka", grade = grade("4", LocalDate.of(2026, 9, 10)))
            .copy(columns = emptyList())

        assertEquals(false, subject.hasPresentableGrades(showAverageAndPoints = false))
    }

    @Test
    fun subjectWithTermGradeButNoRegularGradesIsVisible() {
        val subject = subject(position = 1, name = "Matematyka", grade = grade("4", LocalDate.of(2026, 9, 10)))
            .copy(columns = emptyList(), termGrade = "5")

        assertEquals(true, subject.hasPresentableGrades(showAverageAndPoints = false))
    }

    @Test
    fun averageOnlyCountsWhenShownOnSubjectScreen() {
        val subject = subject(position = 1, name = "Matematyka", grade = grade("4", LocalDate.of(2026, 9, 10)))
            .copy(columns = emptyList(), average = java.math.BigDecimal("4.5"))

        assertEquals(false, subject.hasPresentableGrades(showAverageAndPoints = false))
        assertEquals(true, subject.hasPresentableGrades(showAverageAndPoints = true))
    }

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
