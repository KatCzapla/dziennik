package pl.usagi.edziennik.domain

import java.time.LocalDate

data class SchoolTask(
    val type: SchoolTaskType,
    val rawType: Int,
    val date: LocalDate,
    val subject: String?,
    val hasAttachment: Boolean,
    val id: Int,
    val details: SchoolTaskDetails? = null,
)

data class SchoolTaskDetails(
    val description: String?,
    val teacher: String?,
    val responseDeadline: LocalDate?,
)

sealed interface SchoolTaskType {
    data object Test : SchoolTaskType

    data object Homework : SchoolTaskType

    data object Quiz : SchoolTaskType

    data class Unknown(val rawValue: Int) : SchoolTaskType

    companion object {
        fun fromRawValue(value: Int): SchoolTaskType = when (value) {
            1 -> Test
            2 -> Quiz
            4 -> Homework
            else -> Unknown(value)
        }
    }
}
