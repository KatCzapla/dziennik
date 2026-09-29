package pl.usagi.edziennik.ui.dashboard

import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.GradeColumn
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.GradesSettings
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.SubjectGrades
import pl.usagi.edziennik.domain.TimetableChange
import pl.usagi.edziennik.domain.TimetableChangeKind
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.dto.TimetableChangeDto
import pl.usagi.edziennik.dto.TimetableEntryDto
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.ui.navigation.GradeNavigationTarget
import pl.usagi.edziennik.ui.navigation.SchoolTaskNavigationTarget
import pl.usagi.edziennik.ui.messages.dashboardUnreadBadgeLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DashboardPresentationTest {
    @Test
    fun dashboardTasksKeepDistinctHomeworkAndTestNavigationTargets() {
        val date = LocalDate.of(2026, 9, 14)
        val presented = listOf(
            task(date, 72, SchoolTaskType.Homework),
            task(date, 72, SchoolTaskType.Quiz),
        ).toDashboardTasks(date)

        assertEquals(SchoolTaskNavigationTarget(72, 4, date), presented[0].target)
        assertEquals(SchoolTaskNavigationTarget(72, 2, date), presented[1].target)
    }

    @Test
    fun dashboardGradeKeepsOriginalIndexBeforeSortingByDate() {
        val older = grade(LocalDate.of(2026, 9, 10), "3", "Matematyka")
        val newer = grade(LocalDate.of(2026, 9, 14), "5", "Matematyka")
        val data = grades(older)
        val subject = data.subjects.single()
        val column = subject.columns.single().copy(id = 1, grades = listOf(older, newer))
        val presented = data.copy(subjects = listOf(subject.copy(columns = listOf(column))))
            .toDashboardGrades(periodId = 123)

        assertEquals("5", presented.first().value)
        assertEquals(GradeNavigationTarget(123, 0, 1, 1), presented.first().target)
        assertEquals(GradeNavigationTarget(123, 0, 1, 0), presented.last().target)
    }

    @Test
    fun greetingUsesLoggedInUserName() {
        assertEquals("Dzień dobry, Karolina", dashboardGreeting(" Kowalska-Nowak Karolina "))
    }

    @Test
    fun greetingWithoutNameDoesNotShowEmptyName() {
        assertEquals("Dzień dobry", dashboardGreeting(null))
        assertEquals("Dzień dobry", dashboardGreeting("  "))
    }

    @Test
    fun todaysLessonsArePresentedChronologically() {
        val date = LocalDate.of(2026, 9, 14)
        val lessons = listOf(
            lesson(date = date, start = LocalTime.of(10, 10), subject = "Historia"),
            lesson(date = date, start = LocalTime.of(8, 20), subject = "Matematyka"),
        ).toDashboardLessons(
            displayedDate = date,
            now = LocalDateTime.of(date, LocalTime.of(7, 0)),
        )

        assertEquals(listOf("Matematyka", "Historia"), lessons.map { it.subject })
    }

    @Test
    fun dashboardLessonsAreNotLimited() {
        val date = LocalDate.of(2026, 9, 14)
        val lessons = (1..9).map { index ->
            lesson(
                date = date,
                start = LocalTime.of(7, 0).plusMinutes(index.toLong() * 45),
                subject = "Lekcja $index",
            )
        }.toDashboardLessons(
            displayedDate = date,
            now = LocalDateTime.of(date, LocalTime.of(6, 0)),
        )

        assertEquals(9, lessons.size)
    }

    @Test
    fun dashboardKeepsOrdinaryLessonWithoutCancellationMarker() {
        val date = LocalDate.of(2026, 9, 14)
        val presented = listOf(lesson(date = date)).toDashboardLessons(
            displayedDate = date,
            now = LocalDateTime.of(date, LocalTime.of(7, 0)),
        ).single()

        assertEquals("8:20", presented.startTime)
        assertEquals("Matematyka", presented.subject)
        assertEquals("201", presented.room)
        assertFalse(presented.isCancelled)
    }

    @Test
    fun bothMappedCancellationTypesHaveSameDashboardPresentation() {
        val date = LocalDate.of(2026, 9, 14)
        val now = LocalDateTime.of(date, LocalTime.of(7, 0))

        val presented = listOf(1, 4).map { changeType ->
            TimetableEntryDto(
                date = "2026-09-14T00:00:00+02:00",
                timeFrom = "2026-09-14T08:20:00+02:00",
                timeTo = "2026-09-14T09:05:00+02:00",
                subject = "Matematyka",
                room = "201",
                changes = listOf(
                    TimetableChangeDto(
                        changeType = changeType,
                        absenceInformation = "Szczegóły odwołania",
                    ),
                ),
            ).toDomain()
        }.toDashboardLessons(displayedDate = date, now = now)

        assertEquals(2, presented.size)
        assertEquals(presented[0], presented[1])
        presented.forEach { lesson ->
            assertTrue(lesson.isCancelled)
            assertEquals("8:20", lesson.startTime)
            assertEquals("Matematyka", lesson.subject)
            assertEquals("201", lesson.room)
        }
    }

    @Test
    fun currentLessonIsDetectedByStartInclusiveAndEndExclusive() {
        val date = LocalDate.of(2026, 9, 14)
        val entry = lesson(
            date = date,
            start = LocalTime.of(9, 15),
            end = LocalTime.of(10, 0),
        )

        assertTrue(entry.isCurrentLesson(date, LocalDateTime.of(date, LocalTime.of(9, 15))))
        assertTrue(entry.isCurrentLesson(date, LocalDateTime.of(date, LocalTime.of(9, 59))))
        assertFalse(entry.isCurrentLesson(date, LocalDateTime.of(date, LocalTime.of(10, 0))))
    }

    @Test
    fun cancelledFinishedAndFutureLessonsAreNotCurrent() {
        val date = LocalDate.of(2026, 9, 14)
        val now = LocalDateTime.of(date, LocalTime.of(9, 30))

        assertFalse(cancelledLesson(date).isCurrentLesson(date, now))
        assertFalse(lesson(date = date, start = LocalTime.of(8, 0), end = LocalTime.of(8, 45)).isCurrentLesson(date, now))
        assertFalse(lesson(date = date, start = LocalTime.of(10, 0), end = LocalTime.of(10, 45)).isCurrentLesson(date, now))
    }

    @Test
    fun tomorrowPlanUsesTomorrowDate() {
        val today = LocalDate.of(2026, 9, 14)
        val tomorrow = today.plusDays(1)
        val lessons = listOf(
            lesson(date = today, subject = "Dzisiaj"),
            lesson(date = tomorrow, subject = "Jutro"),
        ).toDashboardLessons(
            displayedDate = tomorrow,
            now = LocalDateTime.of(today, LocalTime.of(8, 0)),
        )

        assertEquals(listOf("Jutro"), lessons.map { it.subject })
    }

    @Test
    fun emptyLessonsProduceEmptyPresentationList() {
        val date = LocalDate.of(2026, 9, 14)

        assertEquals(
            emptyList<DashboardLesson>(),
            emptyList<TimetableEntry>().toDashboardLessons(date, LocalDateTime.of(date, LocalTime.NOON)),
        )
    }

    @Test
    fun recentGradesAreLimitedSortedAndKeepStringValue() {
        val grades = grades(
            grade(date = LocalDate.of(2026, 9, 10), value = "5", subject = "Matematyka"),
            grade(date = LocalDate.of(2026, 9, 12), value = "+", subject = "Język polski"),
            grade(date = LocalDate.of(2026, 9, 11), value = "4+", subject = "Historia"),
            grade(date = LocalDate.of(2026, 9, 9), value = "3", subject = "Biologia"),
        ).toDashboardGrades(periodId = 123)

        assertEquals(listOf("+", "4+", "5"), grades.map { it.value })
        assertEquals(listOf("Język polski", "Historia", "Matematyka"), grades.map { it.subjectName })
    }

    @Test
    fun todayAttendanceEntriesAreSelectedAndSorted() {
        val today = LocalDate.of(2026, 9, 14)
        val entries = listOf(
            attendanceEntry(date = today.plusDays(1), start = LocalTime.of(8, 0), subject = "Jutro"),
            attendanceEntry(date = today, start = LocalTime.of(10, 0), subject = "Historia"),
            attendanceEntry(date = today, start = LocalTime.of(9, 0), subject = "Matematyka"),
        ).toDashboardAttendanceEntries(today)

        assertEquals(listOf("Matematyka", "Historia"), entries.map { it.subject })
    }

    @Test
    fun allTodayAttendanceEntriesAreKeptWithoutLimit() {
        val today = LocalDate.of(2026, 9, 14)
        val entries = (1..8).map { index ->
            attendanceEntry(
                date = today,
                start = LocalTime.of(7, 0).plusMinutes(index.toLong() * 45),
                subject = "Lekcja $index",
            )
        }.toDashboardAttendanceEntries(today)

        assertEquals(8, entries.size)
    }

    @Test
    fun noTodayAttendanceEntriesProduceEmptyList() {
        val today = LocalDate.of(2026, 9, 14)
        val entries = listOf(
            attendanceEntry(date = today.minusDays(1), start = LocalTime.of(8, 0)),
            attendanceEntry(date = today.plusDays(1), start = LocalTime.of(9, 0)),
        ).toDashboardAttendanceEntries(today)

        assertEquals(emptyList<DashboardAttendanceEntry>(), entries)
    }

    @Test
    fun unknownAttendanceStatusKeepsExistingDomainSemantics() {
        val today = LocalDate.of(2026, 9, 14)
        val entry = listOf(
            attendanceEntry(
                date = today,
                start = LocalTime.of(8, 0),
                status = AttendanceStatus.Unknown(rawValue = 999),
            ),
        ).toDashboardAttendanceEntries(today).single()

        assertEquals("Status: 999", entry.statusLabel)
    }

    @Test
    fun upcomingTasksAreLimitedFutureOnlyAndSortedAscending() {
        val today = LocalDate.of(2026, 9, 14)
        val tasks = listOf(
            task(date = today.minusDays(1), id = 1),
            task(date = today.plusDays(3), id = 3),
            task(date = today, id = 2),
            task(date = today.plusDays(1), id = 4),
            task(date = today.plusDays(4), id = 5),
        ).toDashboardTasks(today)

        assertEquals(3, tasks.size)
        assertEquals(listOf("Dzisiaj", "Jutro", "Czwartek"), tasks.map { it.dateLabel })
    }

    @Test
    fun unknownTaskAndNullDescriptionAreSafeForDashboard() {
        val today = LocalDate.of(2026, 9, 14)
        val task = listOf(
            task(
                date = today,
                id = 1,
                type = SchoolTaskType.Unknown(999),
                description = null,
            ),
        ).toDashboardTasks(today).single()

        assertEquals("Inne", task.typeLabel)
        assertEquals(null, task.description)
    }

    @Test
    fun headerDateUsesPolishDayAndMonthWithoutYear() {
        assertEquals("Poniedziałek, 14 września", LocalDate.of(2026, 9, 14).dashboardHeaderLabel())
    }

    @Test
    fun unreadBadgeIsHiddenForZeroAndMissingCount() {
        assertEquals(null, null.dashboardUnreadBadgeLabel())
        assertEquals(null, 0.dashboardUnreadBadgeLabel())
    }

    @Test
    fun unreadBadgeShowsPositiveCountAndCapsLargeValues() {
        assertEquals("3", 3.dashboardUnreadBadgeLabel())
        assertEquals("99+", 120.dashboardUnreadBadgeLabel())
    }

    private fun lesson(
        date: LocalDate,
        start: LocalTime = LocalTime.of(8, 20),
        end: LocalTime = start.plusMinutes(45),
        subject: String = "Matematyka",
    ): TimetableEntry =
        TimetableEntry(
            date = date,
            lessonNumber = null,
            startTime = start,
            endTime = end,
            subject = subject,
            teacher = null,
            room = "201",
            changes = emptyList(),
        )

    private fun cancelledLesson(date: LocalDate): TimetableEntry =
        lesson(date = date).copy(
            changes = listOf(
                TimetableChange(
                    changeType = 4,
                    kind = TimetableChangeKind.Cancelled,
                    teacherType = null,
                    day = null,
                    lessonNumber = null,
                    startTime = null,
                    endTime = null,
                    group = null,
                    subject = null,
                    teacher = null,
                    room = null,
                    absenceInformation = null,
                ),
            ),
        )

    private fun attendanceEntry(
        date: LocalDate,
        start: LocalTime,
        subject: String = "Matematyka",
        status: AttendanceStatus = AttendanceStatus.Present,
    ): AttendanceEntry =
        AttendanceEntry(
            lessonTimeId = null,
            classLessonId = null,
            lessonNumber = null,
            status = status,
            date = date,
            startTime = start,
            endTime = start.plusMinutes(45),
            subject = subject,
            teacher = null,
        )

    private fun grades(vararg grades: Grade): Grades =
        Grades(
            subjects = grades.mapIndexed { index, grade ->
                SubjectGrades(
                    subjectName = grade.teacher ?: "Przedmiot $index",
                    position = index,
                    includeGradeWeight = true,
                    teachers = emptyList(),
                    columns = listOf(
                        GradeColumn(
                            id = index,
                            category = "Aktywność",
                            name = "Aktywność",
                            grades = listOf(grade),
                        ),
                    ),
                    practicalExamGrade = null,
                    oralExamGrade = null,
                    proposedExamGrade = null,
                    finalExamGrade = null,
                    pointsSum = null,
                    pointsSumAllTerms = null,
                    average = null,
                    averageAllTerms = null,
                    proposedTermGrade = null,
                    proposedTermPoints = null,
                    termGrade = null,
                    termPoints = null,
                    gradesSummary = JsonNull,
                )
            },
            settings = GradesSettings(
                showAverageAndPoints = false,
                adultStudent = false,
                descriptiveGrade = false,
                lastClassificationPeriod = false,
            ),
        )

    private fun grade(
        date: LocalDate,
        value: String,
        subject: String,
    ): Grade =
        Grade(
            value = value,
            date = date,
            columnId = 1,
            category = "Aktywność",
            columnName = "Aktywność",
            weight = null,
            color = null,
            teacher = subject,
            changedSinceLastLogin = false,
            correctedGradeId = null,
        )

    private fun task(
        date: LocalDate,
        id: Int,
        type: SchoolTaskType = SchoolTaskType.Quiz,
        description: String? = "Równania liniowe",
    ): SchoolTask =
        SchoolTask(
            type = type,
            rawType = when (type) {
                SchoolTaskType.Homework -> 4
                SchoolTaskType.Quiz -> 2
                is SchoolTaskType.Unknown -> type.rawValue
            },
            date = date,
            subject = "Matematyka",
            hasAttachment = false,
            id = id,
            details = description?.let {
                SchoolTaskDetails(
                    description = it,
                    teacher = null,
                    responseDeadline = null,
                )
            },
        )
}
