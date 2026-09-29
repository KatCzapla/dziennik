package pl.usagi.edziennik.ui.grades

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.GradeColumn
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.GradesSettings
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.SubjectGrades
import pl.usagi.edziennik.repository.GradesRepository
import pl.usagi.edziennik.network.grades.GradesFetchException
import pl.usagi.edziennik.ui.navigation.GradeNavigationTarget
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class GradesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun navigationTargetSelectsItsPeriodAndOpensExactGradeOnlyOnce() = runTest {
        val manager = ActiveStudentManager()
        val repository = FakeGradesRepository()
        val handle = SavedStateHandle(mapOf(
            "periodId" to "123", "subjectPosition" to "1", "columnId" to "1", "gradeIndex" to "0",
        ))
        val viewModel = GradesViewModel(repository, manager, fixedClock("2027-03-15T12:00:00+01:00"), handle)
        assertNull(viewModel.consumeNavigationTarget())
        manager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(listOf(firstStudent.key to 123), repository.gradeRequests)
        assertEquals(GradeNavigationTarget(123, 1, 1, 0), viewModel.consumeNavigationTarget())
        assertNull(viewModel.consumeNavigationTarget())
        assertEquals(true, handle.get<Boolean>("gradeTargetConsumed"))
    }

    @Test
    fun ordinaryNavigationDoesNotAutoOpenGrade() = runTest {
        val manager = ActiveStudentManager()
        val viewModel = GradesViewModel(FakeGradesRepository(), manager, fixedClock())
        manager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        assertNull(viewModel.consumeNavigationTarget())
    }

    @Test
    fun missingGradeDoesNotOpenAnotherRecord() = runTest {
        val manager = ActiveStudentManager()
        val viewModel = GradesViewModel(FakeGradesRepository(), manager, fixedClock(), SavedStateHandle(mapOf(
            "periodId" to "123", "subjectPosition" to "1", "columnId" to "1", "gradeIndex" to "999",
        )))
        manager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        assertNull(viewModel.consumeNavigationTarget())
    }

    @Test
    fun initialLoadFetchesPeriodsSelectsCurrentPeriodAndFetchesGrades() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository()
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock("2027-03-15T12:00:00+01:00"))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(listOf(firstStudent), repository.periodRequests)
        assertEquals(2, viewModel.uiState.value.selectedPeriod?.number)
        assertEquals(listOf(firstStudent.key to 124), repository.gradeRequests)
    }

    @Test
    fun fallbackBeforeFirstPeriodSelectsFirstPeriod() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository()
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock("2026-08-20T12:00:00+02:00"))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.selectedPeriod?.number)
        assertEquals(listOf(firstStudent.key to 123), repository.gradeRequests)
    }

    @Test
    fun fallbackAfterLastPeriodSelectsLastPeriod() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository()
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock("2027-09-01T12:00:00+02:00"))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.selectedPeriod?.number)
        assertEquals(listOf(firstStudent.key to 124), repository.gradeRequests)
    }

    @Test
    fun changingPeriodFetchesSelectedGrades() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository()
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock("2027-03-15T12:00:00+01:00"))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.selectPeriod(repository.periodsByStudent.getValue(firstStudent.key).first())
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.selectedPeriod?.number)
        assertEquals(listOf(firstStudent.key to 124, firstStudent.key to 123), repository.gradeRequests)
    }

    @Test
    fun changingStudentFetchesNewPeriodsAndDoesNotReuseOldPeriodId() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository(
            periodsByStudent = mapOf(
                firstStudent.key to defaultPeriods,
                secondStudent.key to listOf(
                    period(id = 223, number = 1, from = "2026-09-01T00:00:00+02:00", to = "2027-01-31T23:59:59+01:00"),
                    period(id = 224, number = 2, from = "2027-02-01T00:00:00+01:00", to = "2027-08-31T23:59:59+02:00"),
                ),
            ),
        )
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock("2027-03-15T12:00:00+01:00"))

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(listOf(firstStudent, secondStudent), repository.periodRequests)
        assertEquals(224, viewModel.uiState.value.selectedPeriod?.id)
        assertEquals(firstStudent.key to 124, repository.gradeRequests[0])
        assertEquals(secondStudent.key to 224, repository.gradeRequests[1])
        assertNotEquals(repository.gradeRequests[0].second, repository.gradeRequests[1].second)
    }

    @Test
    fun periodLoadingErrorShowsSafeMessage() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository(periodError = IOException())
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals("Nie udało się pobrać ocen.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.selectedPeriod)
    }

    @Test
    fun gradesLoadingErrorKeepsPeriodsAndShowsSafeMessage() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository(gradesError = IOException())
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals("Nie udało się pobrać ocen.", viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.periods.size)
        assertEquals(1, repository.gradeRequests.size)
    }

    @Test
    fun serializationErrorWhileLoadingGradesShowsSafeMessage() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository(
            gradesError = GradesFetchException.UnexpectedResponse(SerializationException("bad payload")),
        )
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals("Nie udało się pobrać ocen.", viewModel.uiState.value.errorMessage)
        assertEquals(1, repository.gradeRequests.size)
    }

    @Test
    fun httpErrorWhileLoadingGradesShowsSafeMessage() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository(
            gradesError = GradesFetchException.Http(500),
        )
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals("Nie udało się pobrać ocen.", viewModel.uiState.value.errorMessage)
        assertEquals(1, repository.gradeRequests.size)
    }

    @Test
    fun retryAfterGradesErrorRepeatsGradesRequest() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository(gradesError = IOException())
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.gradeRequests.size)
    }

    @Test
    fun futurePeriodWithSubjectsAndNoGradesIsValidState() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeGradesRepository(
            gradesByPeriod = mapOf(123 to grades(subjects = listOf(subjectWithoutGrades()))),
        )
        val viewModel = GradesViewModel(repository, activeStudentManager, fixedClock("2026-09-15T12:00:00+02:00"))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.grades?.subjects?.size)
        assertEquals(0, viewModel.uiState.value.grades?.subjects?.first()?.gradeCount())
        assertNull(viewModel.uiState.value.errorMessage)
    }

    private class FakeGradesRepository(
        val periodsByStudent: Map<String, List<ClassificationPeriod>> = mapOf(firstStudent.key to defaultPeriods),
        private val gradesByPeriod: Map<Int, Grades> = mapOf(
            123 to grades(),
            124 to grades(),
            223 to grades(),
            224 to grades(),
        ),
        private val periodError: Throwable? = null,
        private val gradesError: Throwable? = null,
    ) : GradesRepository {
        val periodRequests = mutableListOf<Student>()
        val gradeRequests = mutableListOf<Pair<String, Int>>()

        override suspend fun getClassificationPeriods(student: Student): List<ClassificationPeriod> {
            periodRequests += student
            periodError?.let { throw it }
            return periodsByStudent.getValue(student.key)
        }

        override suspend fun getGrades(
            student: Student,
            period: ClassificationPeriod,
        ): Grades {
            gradeRequests += student.key to period.id
            gradesError?.let { throw it }
            return gradesByPeriod.getValue(period.id)
        }
    }

    private companion object {
        val firstStudent = Student(
            key = "first-key",
            diaryId = 1,
            name = "Jan Kowalski",
            className = "4A",
            schoolName = "Szkola",
        )

        val secondStudent = Student(
            key = "second-key",
            diaryId = 2,
            name = "Anna Nowak",
            className = "2B",
            schoolName = "Szkola",
        )

        val defaultPeriods = listOf(
            period(id = 123, number = 1, from = "2026-09-01T00:00:00+02:00", to = "2027-01-31T23:59:59+01:00"),
            period(id = 124, number = 2, from = "2027-02-01T00:00:00+01:00", to = "2027-08-31T23:59:59+02:00"),
        )

        fun period(
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

        fun fixedClock(value: String = "2026-09-15T12:00:00+02:00"): Clock {
            val dateTime = OffsetDateTime.parse(value)
            return Clock.fixed(dateTime.toInstant(), ZoneId.of("Europe/Warsaw"))
        }

        fun grades(subjects: List<SubjectGrades> = listOf(subjectWithGrade())) = Grades(
            subjects = subjects,
            settings = GradesSettings(
                showAverageAndPoints = false,
                adultStudent = false,
                descriptiveGrade = false,
                lastClassificationPeriod = false,
            ),
        )

        fun subjectWithGrade() = SubjectGrades(
            subjectName = "Matematyka",
            position = 1,
            includeGradeWeight = true,
            teachers = emptyList(),
            columns = listOf(
                GradeColumn(
                    id = 1,
                    category = "Aktywność",
                    name = "Aktywność",
                    grades = listOf(
                        Grade(
                            value = "5",
                            date = LocalDate.of(2026, 9, 10),
                            columnId = 1,
                            category = "Aktywność",
                            columnName = "Aktywność",
                            weight = null,
                            color = null,
                            teacher = null,
                            changedSinceLastLogin = false,
                            correctedGradeId = null,
                        ),
                    ),
                ),
            ),
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

        fun subjectWithoutGrades() = subjectWithGrade().copy(columns = emptyList())
    }
}
