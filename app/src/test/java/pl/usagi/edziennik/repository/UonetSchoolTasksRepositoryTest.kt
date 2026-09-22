package pl.usagi.edziennik.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.dto.HomeworkDetailsDto
import pl.usagi.edziennik.dto.SchoolTaskDto
import pl.usagi.edziennik.dto.TestDetailsDto
import pl.usagi.edziennik.network.schooltasks.SchoolTasksFetchException
import pl.usagi.edziennik.network.schooltasks.SchoolTasksRemoteDataSource
import java.time.LocalDate

class UonetSchoolTasksRepositoryTest {
    @Test
    fun homeworkFetchesHomeworkDetailsAndQuizFetchesTestDetails() = runTest {
        val activeStudentManager = ActiveStudentManager()
        activeStudentManager.initialize(listOf(student))
        val remote = FakeRemoteDataSource(
            tasks = listOf(
                schoolTaskDto(type = 4, id = 1),
                schoolTaskDto(type = 2, id = 2),
            ),
        )
        val repository = UonetSchoolTasksRepository(activeStudentManager, remote)

        val tasks = repository.getSchoolTasks(
            from = LocalDate.of(2026, 9, 14),
            to = LocalDate.of(2026, 9, 18),
        )

        assertEquals(listOf(1), remote.homeworkDetailRequests)
        assertEquals(listOf(2), remote.testDetailRequests)
        assertEquals("Homework 1", tasks[0].details?.description)
        assertEquals("Quiz 2", tasks[1].details?.description)
    }

    @Test
    fun detailsAreMergedWithMatchingTaskOnly() = runTest {
        val activeStudentManager = ActiveStudentManager()
        activeStudentManager.initialize(listOf(student))
        val repository = UonetSchoolTasksRepository(
            activeStudentManager = activeStudentManager,
            dataSource = FakeRemoteDataSource(
                tasks = listOf(
                    schoolTaskDto(type = 4, id = 10),
                    schoolTaskDto(type = 4, id = 20),
                ),
            ),
        )

        val tasks = repository.getSchoolTasks(
            from = LocalDate.of(2026, 9, 14),
            to = LocalDate.of(2026, 9, 18),
        )

        assertEquals(10, tasks[0].id)
        assertEquals("Homework 10", tasks[0].details?.description)
        assertEquals(20, tasks[1].id)
        assertEquals("Homework 20", tasks[1].details?.description)
    }

    @Test
    fun detailFailureKeepsBaseTaskVisible() = runTest {
        val activeStudentManager = ActiveStudentManager()
        activeStudentManager.initialize(listOf(student))
        val repository = UonetSchoolTasksRepository(
            activeStudentManager = activeStudentManager,
            dataSource = FakeRemoteDataSource(
                tasks = listOf(schoolTaskDto(type = 4, id = 1)),
                failingHomeworkIds = setOf(1),
            ),
        )

        val tasks = repository.getSchoolTasks(
            from = LocalDate.of(2026, 9, 14),
            to = LocalDate.of(2026, 9, 18),
        )

        assertEquals(1, tasks.size)
        assertEquals(SchoolTaskType.Homework, tasks.single().type)
        assertEquals(null, tasks.single().details)
    }

    @Test
    fun unknownTaskDoesNotFetchDetailsAndRemainsVisible() = runTest {
        val activeStudentManager = ActiveStudentManager()
        activeStudentManager.initialize(listOf(student))
        val remote = FakeRemoteDataSource(
            tasks = listOf(schoolTaskDto(type = 999, id = 3)),
        )
        val repository = UonetSchoolTasksRepository(activeStudentManager, remote)

        val tasks = repository.getSchoolTasks(
            from = LocalDate.of(2026, 9, 14),
            to = LocalDate.of(2026, 9, 18),
        )

        assertEquals(emptyList<Int>(), remote.homeworkDetailRequests)
        assertEquals(emptyList<Int>(), remote.testDetailRequests)
        assertEquals(SchoolTaskType.Unknown(999), tasks.single().type)
        assertEquals(null, tasks.single().details)
    }

    private class FakeRemoteDataSource(
        private val tasks: List<SchoolTaskDto>,
        private val failingHomeworkIds: Set<Int> = emptySet(),
    ) : SchoolTasksRemoteDataSource {
        val homeworkDetailRequests = mutableListOf<Int>()
        val testDetailRequests = mutableListOf<Int>()

        override suspend fun getSchoolTasks(
            studentKey: String,
            dateFrom: String,
            dateTo: String,
        ): List<SchoolTaskDto> = tasks

        override suspend fun getHomeworkDetails(
            studentKey: String,
            homeworkId: Int,
        ): HomeworkDetailsDto {
            homeworkDetailRequests += homeworkId
            if (homeworkId in failingHomeworkIds) {
                throw SchoolTasksFetchException.Http(500)
            }
            return HomeworkDetailsDto(
                type = 4,
                date = "2026-09-15T00:00:00+02:00",
                responseDeadline = "2026-09-16T00:00:00+02:00",
                subject = "Matematyka",
                teacher = "Jan Kowalski",
                description = "Homework $homeworkId",
                id = homeworkId,
            )
        }

        override suspend fun getTestDetails(
            studentKey: String,
            testId: Int,
        ): TestDetailsDto {
            testDetailRequests += testId
            return TestDetailsDto(
                type = 2,
                date = "2026-09-15T00:00:00+02:00",
                subject = "Matematyka",
                teacher = "Jan Kowalski",
                description = "Quiz $testId",
                id = testId,
            )
        }
    }

    private companion object {
        val student = Student(
            key = "student-key",
            diaryId = 1,
            name = "Jan Kowalski",
            className = "4A",
            schoolName = "Szkola",
        )

        fun schoolTaskDto(
            type: Int,
            id: Int,
        ): SchoolTaskDto =
            SchoolTaskDto(
                type = type,
                subject = "Matematyka",
                date = "2026-09-15T00:00:00+02:00",
                hasAttachment = false,
                id = id,
            )
    }
}
