package pl.usagi.edziennik.mapper

import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.time.parseApiLocalDate
import pl.usagi.edziennik.domain.time.parseApiLocalTime
import pl.usagi.edziennik.dto.AttendanceEntryDto

fun AttendanceEntryDto.toDomainOrNull(): AttendanceEntry? {
    val date = date?.let(::runCatchingParseDate)?.getOrNull() ?: return null
    val startTime = timeFrom?.let(::runCatchingParseTime)?.getOrNull() ?: return null
    val endTime = timeTo?.let(::runCatchingParseTime)?.getOrNull() ?: return null

    return AttendanceEntry(
        lessonTimeId = lessonTimeId,
        classLessonId = classLessonId,
        lessonNumber = lessonNumber,
        status = attendanceCategory.toAttendanceStatus(),
        date = date,
        startTime = startTime,
        endTime = endTime,
        subject = subject.emptyToNull(),
        teacher = teacher.emptyToNull(),
    )
}

fun List<AttendanceEntryDto>.toDomainAttendance(): List<AttendanceEntry> =
    mapNotNull { it.toDomainOrNull() }
        .sortedWith(compareBy<AttendanceEntry> { it.date }.thenBy { it.startTime })

private fun Int?.toAttendanceStatus(): AttendanceStatus =
    when (this) {
        PRESENT_CATEGORY -> AttendanceStatus.Present
        else -> AttendanceStatus.Unknown(rawValue = this)
    }

private fun runCatchingParseDate(value: String) =
    runCatching { parseApiLocalDate(value) }

private fun runCatchingParseTime(value: String) =
    runCatching { parseApiLocalTime(value) }

private fun String?.emptyToNull(): String? =
    takeUnless { it.isNullOrEmpty() }

private const val PRESENT_CATEGORY = 1
