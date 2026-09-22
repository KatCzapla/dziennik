package pl.usagi.edziennik.ui.dashboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.GradeColumn
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.GradesSettings
import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.SubjectGrades
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.repository.AttendanceRepository
import pl.usagi.edziennik.repository.GradesRepository
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.ReceivedMessagesPage
import pl.usagi.edziennik.repository.SchoolTasksRepository
import pl.usagi.edziennik.repository.TimetableRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsAllSectionsForActiveStudent() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val timetableRepository = FakeTimetableRepository(activeStudentManager)
        val attendanceRepository = FakeAttendanceRepository(activeStudentManager)
        val gradesRepository = FakeGradesRepository()
        val schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager)
        val messagesRepository = FakeMessagesRepository()
        val viewModel = DashboardViewModel(
            timetableRepository = timetableRepository,
            attendanceRepository = attendanceRepository,
            gradesRepository = gradesRepository,
            schoolTasksRepository = schoolTasksRepository,
            messagesRepository = messagesRepository,
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(2, timetableRepository.requests.size)
        assertEquals(listOf("first-key"), attendanceRepository.requests.map { it.studentKey })
        assertEquals(listOf(firstStudent), gradesRepository.periodRequests)
        assertEquals(listOf(firstStudent), gradesRepository.gradeRequests.map { it.first })
        assertEquals(listOf("first-key"), schoolTasksRepository.requests.map { it.studentKey })
        assertEquals(1, messagesRepository.unreadRequests)
        assertEquals("Matematyka", viewModel.uiState.value.todayTimetable.data?.single()?.subject)
        assertEquals("5", viewModel.uiState.value.recentGrades.data?.single()?.value)
        assertEquals("Kartkówka", viewModel.uiState.value.upcomingTasks.data?.single()?.typeLabel)
        assertEquals(3, viewModel.uiState.value.unreadMessagesCount.data)
    }

    @Test
    fun oneSectionErrorDoesNotRemoveOtherSections() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val timetableRepository = FakeTimetableRepository(activeStudentManager)
        val attendanceRepository = FakeAttendanceRepository(activeStudentManager)
        val gradesRepository = FakeGradesRepository(failGrades = true)
        val schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager)
        val viewModel = DashboardViewModel(
            timetableRepository = timetableRepository,
            attendanceRepository = attendanceRepository,
            gradesRepository = gradesRepository,
            schoolTasksRepository = schoolTasksRepository,
            messagesRepository = FakeMessagesRepository(),
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals("Matematyka", viewModel.uiState.value.todayTimetable.data?.single()?.subject)
        assertEquals("Matematyka", viewModel.uiState.value.todayAttendance.data?.single()?.subject)
        assertEquals("Nie udało się pobrać ocen", viewModel.uiState.value.recentGrades.errorMessage)
        assertEquals("Równania liniowe", viewModel.uiState.value.upcomingTasks.data?.single()?.description)
    }

    @Test
    fun activeStudentChangeReloadsAllSectionsAndClearsPreviousState() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val timetableRepository = FakeTimetableRepository(activeStudentManager).apply {
            timetableByStudent["first-key"] = listOf(lesson(subject = "Pierwszy plan"))
            timetableByStudent["second-key"] = listOf(lesson(subject = "Drugi plan"))
        }
        val gradesRepository = FakeGradesRepository().apply {
            gradesByStudent[firstStudent] = grades(grade("5", "Pierwsze oceny"))
            gradesByStudent[secondStudent] = grades(grade("4+", "Drugie oceny"))
        }
        val attendanceRepository = FakeAttendanceRepository(activeStudentManager).apply {
            attendanceByStudent["first-key"] = listOf(attendanceEntry(subject = "Pierwsza frekwencja"))
            attendanceByStudent["second-key"] = listOf(attendanceEntry(subject = "Druga frekwencja"))
        }
        val schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager).apply {
            tasksByStudent["first-key"] = listOf(task(description = "Pierwsze zadanie"))
            tasksByStudent["second-key"] = listOf(task(description = "Drugie zadanie"))
        }
        val messagesRepository = FakeMessagesRepository()
        val viewModel = DashboardViewModel(
            timetableRepository = timetableRepository,
            attendanceRepository = attendanceRepository,
            gradesRepository = gradesRepository,
            schoolTasksRepository = schoolTasksRepository,
            messagesRepository = messagesRepository,
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(listOf("first-key", "first-key", "second-key", "second-key"), timetableRepository.requests.map { it.studentKey })
        assertEquals(listOf("first-key", "second-key"), attendanceRepository.requests.map { it.studentKey })
        assertEquals(listOf(firstStudent, secondStudent), gradesRepository.periodRequests)
        assertEquals(listOf("first-key", "second-key"), schoolTasksRepository.requests.map { it.studentKey })
        assertEquals(2, messagesRepository.unreadRequests)
        assertEquals("Drugi plan", viewModel.uiState.value.todayTimetable.data?.single()?.subject)
        assertEquals("Druga frekwencja", viewModel.uiState.value.todayAttendance.data?.single()?.subject)
        assertEquals("4+", viewModel.uiState.value.recentGrades.data?.single()?.value)
        assertEquals("Drugie zadanie", viewModel.uiState.value.upcomingTasks.data?.single()?.description)
    }

    @Test
    fun switchingTodayTomorrowOnlyChangesVisibleTimetableSection() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val timetableRepository = FakeTimetableRepository(activeStudentManager).apply {
            timetableByDate[today] = listOf(lesson(subject = "Dzisiaj"))
            timetableByDate[tomorrow] = listOf(lesson(date = tomorrow, subject = "Jutro"))
        }
        val gradesRepository = FakeGradesRepository()
        val attendanceRepository = FakeAttendanceRepository(activeStudentManager)
        val schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager)
        val viewModel = DashboardViewModel(
            timetableRepository = timetableRepository,
            attendanceRepository = attendanceRepository,
            gradesRepository = gradesRepository,
            schoolTasksRepository = schoolTasksRepository,
            messagesRepository = FakeMessagesRepository(),
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val gradeRequests = gradesRepository.gradeRequests.size
        val attendanceRequests = attendanceRepository.requests.size
        val schoolTaskRequests = schoolTasksRepository.requests.size

        viewModel.showTomorrowTimetable()
        advanceUntilIdle()

        assertEquals(DashboardTimetableDay.Tomorrow, viewModel.uiState.value.selectedTimetableDay)
        assertEquals("Jutro", viewModel.uiState.value.selectedTimetable.data?.single()?.subject)
        assertEquals(gradeRequests, gradesRepository.gradeRequests.size)
        assertEquals(attendanceRequests, attendanceRepository.requests.size)
        assertEquals(schoolTaskRequests, schoolTasksRepository.requests.size)
        assertEquals(2, timetableRepository.requests.size)

        viewModel.showTodayTimetable()
        advanceUntilIdle()

        assertEquals(DashboardTimetableDay.Today, viewModel.uiState.value.selectedTimetableDay)
        assertEquals("Dzisiaj", viewModel.uiState.value.selectedTimetable.data?.single()?.subject)
        assertEquals(gradeRequests, gradesRepository.gradeRequests.size)
        assertEquals(attendanceRequests, attendanceRepository.requests.size)
        assertEquals(schoolTaskRequests, schoolTasksRepository.requests.size)
    }

    @Test
    fun outsideSchoolYearTimetableDateDoesNotRequestNetwork() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val timetableRepository = FakeTimetableRepository(activeStudentManager)
        val viewModel = DashboardViewModel(
            timetableRepository = timetableRepository,
            attendanceRepository = FakeAttendanceRepository(activeStudentManager),
            gradesRepository = FakeGradesRepository(),
            schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager),
            messagesRepository = FakeMessagesRepository(),
            activeStudentManager = activeStudentManager,
            clock = fixedClock(LocalDate.of(2027, 6, 30)),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(listOf(LocalDate.of(2027, 6, 30)), timetableRepository.requests.map { it.from })
        assertEquals(emptyList<DashboardLesson>(), viewModel.uiState.value.tomorrowTimetable.data)
    }

    @Test
    fun todayAttendanceKeepsOnlyTodayEntriesWithoutLimit() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val attendanceRepository = FakeAttendanceRepository(activeStudentManager).apply {
            response = listOf(attendanceEntry(date = today.minusDays(1))) +
                (1..6).map { index ->
                    attendanceEntry(
                        date = today,
                        start = LocalTime.of(7, 0).plusMinutes(index.toLong() * 45),
                        subject = "Frekwencja $index",
                    )
                } +
                listOf(attendanceEntry(date = tomorrow))
        }
        val viewModel = DashboardViewModel(
            timetableRepository = FakeTimetableRepository(activeStudentManager),
            attendanceRepository = attendanceRepository,
            gradesRepository = FakeGradesRepository(),
            schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager),
            messagesRepository = FakeMessagesRepository(),
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(6, viewModel.uiState.value.todayAttendance.data?.size)
        assertEquals((1..6).map { "Frekwencja $it" }, viewModel.uiState.value.todayAttendance.data?.map { it.subject })
    }

    @Test
    fun emptyTodayAttendanceProducesEmptySectionData() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val attendanceRepository = FakeAttendanceRepository(activeStudentManager).apply {
            response = listOf(attendanceEntry(date = tomorrow))
        }
        val viewModel = DashboardViewModel(
            timetableRepository = FakeTimetableRepository(activeStudentManager),
            attendanceRepository = attendanceRepository,
            gradesRepository = FakeGradesRepository(),
            schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager),
            messagesRepository = FakeMessagesRepository(),
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(emptyList<DashboardAttendanceEntry>(), viewModel.uiState.value.todayAttendance.data)
    }

    @Test
    fun attendanceErrorDoesNotAffectOtherDashboardSections() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val viewModel = DashboardViewModel(
            timetableRepository = FakeTimetableRepository(activeStudentManager),
            attendanceRepository = FakeAttendanceRepository(activeStudentManager, failAttendance = true),
            gradesRepository = FakeGradesRepository(),
            schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager),
            messagesRepository = FakeMessagesRepository(),
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals("Nie udało się pobrać frekwencji", viewModel.uiState.value.todayAttendance.errorMessage)
        assertEquals("Matematyka", viewModel.uiState.value.todayTimetable.data?.single()?.subject)
        assertEquals("5", viewModel.uiState.value.recentGrades.data?.single()?.value)
        assertEquals("Równania liniowe", viewModel.uiState.value.upcomingTasks.data?.single()?.description)
    }

    @Test
    fun unreadMessagesErrorDoesNotAffectOtherDashboardSections() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val viewModel = DashboardViewModel(
            timetableRepository = FakeTimetableRepository(activeStudentManager),
            attendanceRepository = FakeAttendanceRepository(activeStudentManager),
            gradesRepository = FakeGradesRepository(),
            schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager),
            messagesRepository = FakeMessagesRepository(failUnread = true),
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.unreadMessagesCount.data)
        assertEquals(null, viewModel.uiState.value.unreadMessagesCount.errorMessage)
        assertEquals("Matematyka", viewModel.uiState.value.todayTimetable.data?.single()?.subject)
        assertEquals("Matematyka", viewModel.uiState.value.todayAttendance.data?.single()?.subject)
        assertEquals("5", viewModel.uiState.value.recentGrades.data?.single()?.value)
        assertEquals("Równania liniowe", viewModel.uiState.value.upcomingTasks.data?.single()?.description)
    }

    @Test
    fun unreadCountInvalidationReloadsDashboardBadge() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val messagesRepository = FakeMessagesRepository()
        val viewModel = DashboardViewModel(
            timetableRepository = FakeTimetableRepository(activeStudentManager),
            attendanceRepository = FakeAttendanceRepository(activeStudentManager),
            gradesRepository = FakeGradesRepository(),
            schoolTasksRepository = FakeSchoolTasksRepository(activeStudentManager),
            messagesRepository = messagesRepository,
            activeStudentManager = activeStudentManager,
            clock = fixedClock(),
        )

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        messagesRepository.emitUnreadInvalidation()
        advanceUntilIdle()

        assertEquals(2, messagesRepository.unreadRequests)
        assertEquals(3, viewModel.uiState.value.unreadMessagesCount.data)
    }

    private class FakeTimetableRepository(
        private val activeStudentManager: ActiveStudentManager,
    ) : TimetableRepository {
        val requests = mutableListOf<TimetableRequest>()
        val timetableByDate = mutableMapOf<LocalDate, List<TimetableEntry>>()
        val timetableByStudent = mutableMapOf<String, List<TimetableEntry>>()

        override suspend fun getTimetable(
            from: LocalDate,
            to: LocalDate,
        ): List<TimetableEntry> {
            val studentKey = activeStudentManager.activeStudent.value?.key
            requests += TimetableRequest(studentKey, from, to)
            return timetableByStudent[studentKey]
                ?: timetableByDate[from]
                ?: listOf(lesson(date = from))
        }
    }

    private class FakeAttendanceRepository(
        private val activeStudentManager: ActiveStudentManager,
        private val failAttendance: Boolean = false,
    ) : AttendanceRepository {
        val requests = mutableListOf<AttendanceRequest>()
        var response = listOf(attendanceEntry())
        val attendanceByStudent = mutableMapOf<String, List<AttendanceEntry>>()

        override suspend fun getAttendance(
            from: LocalDate,
            to: LocalDate,
        ): List<AttendanceEntry> {
            if (failAttendance) error("attendance failed")
            val studentKey = activeStudentManager.activeStudent.value?.key
            requests += AttendanceRequest(studentKey, from, to)
            return attendanceByStudent[studentKey] ?: response
        }
    }

    private class FakeGradesRepository(
        private val failGrades: Boolean = false,
    ) : GradesRepository {
        val periodRequests = mutableListOf<Student>()
        val gradeRequests = mutableListOf<Pair<Student, ClassificationPeriod>>()
        val gradesByStudent = mutableMapOf<Student, Grades>()

        override suspend fun getClassificationPeriods(student: Student): List<ClassificationPeriod> {
            periodRequests += student
            return listOf(period)
        }

        override suspend fun getGrades(
            student: Student,
            period: ClassificationPeriod,
        ): Grades {
            if (failGrades) error("grades failed")
            gradeRequests += student to period
            return gradesByStudent[student] ?: grades(grade("5", "Matematyka"))
        }
    }

    private class FakeSchoolTasksRepository(
        private val activeStudentManager: ActiveStudentManager,
    ) : SchoolTasksRepository {
        val requests = mutableListOf<SchoolTaskRequest>()
        val tasksByStudent = mutableMapOf<String, List<SchoolTask>>()

        override suspend fun getSchoolTasks(
            from: LocalDate,
            to: LocalDate,
        ): List<SchoolTask> {
            val studentKey = activeStudentManager.activeStudent.value?.key
            requests += SchoolTaskRequest(studentKey, from, to)
            return tasksByStudent[studentKey] ?: listOf(task())
        }
    }

    private class FakeMessagesRepository(
        private val failUnread: Boolean = false,
    ) : MessagesRepository {
        private val mutableUnreadCountInvalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val unreadCountInvalidations: Flow<Unit> = mutableUnreadCountInvalidations
        var unreadRequests = 0

        override suspend fun getMailboxes(): List<pl.usagi.edziennik.domain.Mailbox> = emptyList()

        override suspend fun getUnreadCount(): Int {
            unreadRequests += 1
            if (failUnread) error("messages failed")
            return 3
        }

        override suspend fun getReceivedMessagesPage(
            lastMessageId: Int,
            pageSize: Int,
        ): ReceivedMessagesPage =
            getMessagesPage(
                mailboxType = MessageMailboxType.Received,
                lastMessageId = lastMessageId,
                pageSize = pageSize,
            )

        override suspend fun getMessagesPage(
            mailboxType: MessageMailboxType,
            lastMessageId: Int,
            pageSize: Int,
        ): ReceivedMessagesPage =
            ReceivedMessagesPage(messages = emptyList(), endReached = true)

        override suspend fun getMessageDetails(apiGlobalKey: String): MessageDetails =
            error("not used")

        override suspend fun markMessageAsRead(apiGlobalKey: String) = Unit

        fun emitUnreadInvalidation() {
            mutableUnreadCountInvalidations.tryEmit(Unit)
        }
    }

    private data class TimetableRequest(
        val studentKey: String?,
        val from: LocalDate,
        val to: LocalDate,
    )

    private data class AttendanceRequest(
        val studentKey: String?,
        val from: LocalDate,
        val to: LocalDate,
    )

    private data class SchoolTaskRequest(
        val studentKey: String?,
        val from: LocalDate,
        val to: LocalDate,
    )

    private companion object {
        val today: LocalDate = LocalDate.of(2026, 9, 14)
        val tomorrow: LocalDate = LocalDate.of(2026, 9, 15)

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

        val period = ClassificationPeriod(
            id = 1,
            number = 1,
            startDate = OffsetDateTime.parse("2026-09-01T00:00:00+02:00"),
            endDate = OffsetDateTime.parse("2027-01-31T00:00:00+01:00"),
        )

        fun fixedClock(date: LocalDate = today): Clock {
            val zoneId = ZoneId.of("Europe/Warsaw")
            return Clock.fixed(date.atTime(9, 30).atZone(zoneId).toInstant(), zoneId)
        }

        fun lesson(
            date: LocalDate = today,
            subject: String = "Matematyka",
        ): TimetableEntry =
            TimetableEntry(
                date = date,
                lessonNumber = null,
                startTime = LocalTime.of(9, 15),
                endTime = LocalTime.of(10, 0),
                subject = subject,
                teacher = null,
                room = "201",
                changes = emptyList(),
            )

        fun attendanceEntry(
            date: LocalDate = today,
            start: LocalTime = LocalTime.of(9, 15),
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

        fun grades(grade: Grade): Grades =
            Grades(
                subjects = listOf(
                    SubjectGrades(
                        subjectName = grade.teacher ?: "Matematyka",
                        position = 1,
                        includeGradeWeight = true,
                        teachers = emptyList(),
                        columns = listOf(
                            GradeColumn(
                                id = 1,
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
                    ),
                ),
                settings = GradesSettings(
                    showAverageAndPoints = false,
                    adultStudent = false,
                    descriptiveGrade = false,
                    lastClassificationPeriod = false,
                ),
            )

        fun grade(
            value: String,
            subject: String,
        ): Grade =
            Grade(
                value = value,
                date = today,
                columnId = 1,
                category = "Aktywność",
                columnName = "Aktywność",
                weight = null,
                color = null,
                teacher = subject,
                changedSinceLastLogin = false,
                correctedGradeId = null,
            )

        fun task(description: String = "Równania liniowe"): SchoolTask =
            SchoolTask(
                type = SchoolTaskType.Quiz,
                rawType = 2,
                date = tomorrow,
                subject = "Matematyka",
                hasAttachment = false,
                id = 1,
                details = SchoolTaskDetails(
                    description = description,
                    teacher = null,
                    responseDeadline = null,
                ),
            )
    }
}
