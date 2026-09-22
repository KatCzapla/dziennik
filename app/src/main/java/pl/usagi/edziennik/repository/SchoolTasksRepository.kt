package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.SchoolTask
import java.time.LocalDate

interface SchoolTasksRepository {
    suspend fun getSchoolTasks(
        from: LocalDate,
        to: LocalDate,
    ): List<SchoolTask>
}
