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
    val excuseStatus: AttendanceExcuseStatus? = null,
)

sealed interface AttendanceStatus {
    data object Present : AttendanceStatus
    data object Absent : AttendanceStatus
    data object ExcusedAbsence : AttendanceStatus
    data object Late : AttendanceStatus
    data object ExcusedLate : AttendanceStatus
    data object SchoolRelatedAbsence : AttendanceStatus
    data object Released : AttendanceStatus

    data class Unknown(
        val rawValue: Int?,
        val description: String? = null,
    ) : AttendanceStatus
}

sealed interface AttendanceExcuseStatus {
    data object Pending : AttendanceExcuseStatus
    data object Approved : AttendanceExcuseStatus
    data class Unknown(val rawValue: Int) : AttendanceExcuseStatus
}

data class AttendanceExcuse(
    val day: LocalDate,
    val lessonTimeId: Int,
    val status: AttendanceExcuseStatus,
)

data class AttendanceSubject(
    val id: Int,
    val name: String,
)

data class AttendanceStatistics(
    val summary: Double?,
    val categories: List<AttendanceCategoryStatistics>,
)

data class AttendanceCategoryStatistics(
    val status: AttendanceStatus,
    val total: Int?,
    val months: List<AttendanceMonthStatistics>,
    val periods: List<Int>,
)

data class AttendanceMonthStatistics(
    val month: Int,
    val value: Int,
)
