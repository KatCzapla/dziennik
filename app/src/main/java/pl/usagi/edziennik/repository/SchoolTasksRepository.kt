package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.changes.TaskKey
import java.time.LocalDate

interface SchoolTasksRepository {
    suspend fun getSchoolTasks(
        from: LocalDate,
        to: LocalDate,
    ): List<SchoolTask>

    suspend fun getSchoolTasksWithCompleteness(from: LocalDate, to: LocalDate): SchoolTasksFetchResult =
        SchoolTasksFetchResult(getSchoolTasks(from, to), emptySet())
}

data class SchoolTasksFetchResult(val tasks: List<SchoolTask>, val failedDetailKeys: Set<TaskKey>)
