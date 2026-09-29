package pl.usagi.edziennik.ui.navigation

import androidx.lifecycle.SavedStateHandle
import pl.usagi.edziennik.domain.SchoolTask
import java.time.LocalDate

const val GRADES_ROUTE = "grades?periodId={periodId}&subjectPosition={subjectPosition}&columnId={columnId}&gradeIndex={gradeIndex}"
const val SCHOOL_TASKS_ROUTE = "schoolTasks?taskId={taskId}&taskType={taskType}&taskDate={taskDate}"

data class GradeNavigationTarget(
    val periodId: Int,
    val subjectPosition: Int,
    val columnId: Int,
    val gradeIndex: Int,
) {
    fun route(): String =
        "grades?periodId=$periodId&subjectPosition=$subjectPosition&columnId=$columnId&gradeIndex=$gradeIndex"

    fun subjectRoute(): String =
        "grades/subject/$subjectPosition?columnId=$columnId&gradeIndex=$gradeIndex"

    companion object {
        fun from(handle: SavedStateHandle): GradeNavigationTarget? {
            val periodId = handle.get<String>("periodId")?.toIntOrNull() ?: return null
            val position = handle.get<String>("subjectPosition")?.toIntOrNull() ?: return null
            val columnId = handle.get<String>("columnId")?.toIntOrNull() ?: return null
            val index = handle.get<String>("gradeIndex")?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return GradeNavigationTarget(periodId, position, columnId, index)
        }
    }
}

data class SchoolTaskNavigationTarget(
    val id: Int,
    val rawType: Int,
    val date: LocalDate,
) {
    fun route(): String = "schoolTasks?taskId=$id&taskType=$rawType&taskDate=$date"

    fun matches(task: SchoolTask): Boolean = task.id == id && task.rawType == rawType && task.date == date

    companion object {
        fun from(handle: SavedStateHandle): SchoolTaskNavigationTarget? {
            val id = handle.get<String>("taskId")?.toIntOrNull() ?: return null
            val type = handle.get<String>("taskType")?.toIntOrNull() ?: return null
            val date = handle.get<String>("taskDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return null
            return SchoolTaskNavigationTarget(id, type, date)
        }
    }
}
