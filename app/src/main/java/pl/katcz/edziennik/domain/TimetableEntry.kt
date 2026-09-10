package pl.katcz.edziennik.domain

import java.time.LocalDate
import java.time.LocalTime

data class TimetableEntry(
    val date: LocalDate,
    val lessonNumber: Int?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val teacher: String?,
    val room: String?,
    val changes: List<TimetableChange>,
)

data class TimetableChange(
    val changeType: Int,
    val lessonNumber: Int?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val subject: String?,
    val teacher: String?,
    val room: String?,
    val absenceInformation: String?,
) {
    val isSubstitution: Boolean
        get() = changeType == 7 && teacher != null
}
