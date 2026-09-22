package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.mapper.toDomainDetails
import pl.usagi.edziennik.mapper.toDomainSchoolTasks
import pl.usagi.edziennik.network.schooltasks.SchoolTasksRemoteDataSource
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class UonetSchoolTasksRepository @Inject constructor(
    private val activeStudentManager: ActiveStudentManager,
    private val dataSource: SchoolTasksRemoteDataSource,
) : SchoolTasksRepository {
    override suspend fun getSchoolTasks(
        from: LocalDate,
        to: LocalDate,
    ): List<SchoolTask> {
        val activeStudent = activeStudentManager.requireActiveStudent(SchoolTasksRepositoryException.NoActiveStudent)
        val zoneId = ZoneId.systemDefault()

        return dataSource.getSchoolTasks(
            studentKey = activeStudent.key,
            dateFrom = from.toApiStartOfDay(zoneId),
            dateTo = to.toApiEndOfDay(zoneId),
        ).toDomainSchoolTasks()
            .map { task ->
                task.copy(
                    details = getDetailsSafely(
                        studentKey = activeStudent.key,
                        task = task,
                    ),
                )
            }
    }

    private suspend fun getDetailsSafely(
        studentKey: String,
        task: SchoolTask,
    ): SchoolTaskDetails? =
        try {
            when (task.type) {
                SchoolTaskType.Homework ->
                    dataSource.getHomeworkDetails(studentKey, task.id).toDomainDetails()

                SchoolTaskType.Quiz ->
                    dataSource.getTestDetails(studentKey, task.id).toDomainDetails()

                is SchoolTaskType.Unknown -> null
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
}

sealed class SchoolTasksRepositoryException(message: String) : Exception(message) {
    data object NoActiveStudent : SchoolTasksRepositoryException("No active student selected.")
}
