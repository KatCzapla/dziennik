package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.changes.TaskKey
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
    ): List<SchoolTask> = getSchoolTasksWithCompleteness(from, to).tasks

    override suspend fun getSchoolTasksWithCompleteness(
        from: LocalDate,
        to: LocalDate,
    ): SchoolTasksFetchResult {
        val activeStudent = activeStudentManager.requireActiveStudent(SchoolTasksRepositoryException.NoActiveStudent)
        val zoneId = ZoneId.systemDefault()

        val failedDetailKeys = mutableSetOf<TaskKey>()
        val detailFailures = mutableMapOf<TaskKey, Exception>()
        val tasks = dataSource.getSchoolTasks(
            studentKey = activeStudent.key,
            dateFrom = from.toApiStartOfDay(zoneId),
            dateTo = to.toApiEndOfDay(zoneId),
        ).toDomainSchoolTasks()
            .map { task ->
                val details = getDetailsSafely(studentKey = activeStudent.key, task = task)
                if (details.failed) failedDetailKeys += TaskKey(task.rawType, task.id)
                details.failure?.let { detailFailures[TaskKey(task.rawType, task.id)] = it }
                task.copy(
                    details = details.value,
                )
            }
        return SchoolTasksFetchResult(tasks, failedDetailKeys, detailFailures)
    }

    private suspend fun getDetailsSafely(
        studentKey: String,
        task: SchoolTask,
    ): TaskDetailsFetch =
        try {
            TaskDetailsFetch(when (task.type) {
                SchoolTaskType.Homework ->
                    dataSource.getHomeworkDetails(studentKey, task.id).toDomainDetails()

                SchoolTaskType.Test, SchoolTaskType.Quiz ->
                    dataSource.getTestDetails(studentKey, task.id).toDomainDetails()

                is SchoolTaskType.Unknown -> null
            }, failed = false)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            TaskDetailsFetch(null, failed = true, failure = exception)
        }

    private data class TaskDetailsFetch(val value: SchoolTaskDetails?, val failed: Boolean, val failure: Exception? = null)
}

sealed class SchoolTasksRepositoryException(message: String) : Exception(message) {
    data object NoActiveStudent : SchoolTasksRepositoryException("No active student selected.")
}
