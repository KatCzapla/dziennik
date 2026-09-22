package pl.usagi.edziennik.domain

import java.time.LocalDate
import java.time.LocalTime

data class AttendanceEntry(
    val lessonTimeId: Int?,
    val classLessonId: Int?,
    val lessonNumber: Int?,
    val status: AttendanceStatus,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String?,
    val teacher: String?,
)

sealed interface AttendanceStatus {
    data object Present : AttendanceStatus

    data class Unknown(
        val rawValue: Int?,
        val description: String? = null,
    ) : AttendanceStatus
}
