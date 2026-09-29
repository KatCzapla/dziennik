package pl.usagi.edziennik.mapper

import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceExcuse
import pl.usagi.edziennik.domain.AttendanceExcuseStatus
import pl.usagi.edziennik.domain.AttendanceCategoryStatistics
import pl.usagi.edziennik.domain.AttendanceMonthStatistics
import pl.usagi.edziennik.domain.AttendanceStatistics
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.AttendanceSubject
import pl.usagi.edziennik.domain.time.parseApiLocalDate
import pl.usagi.edziennik.domain.time.parseApiLocalTime
import pl.usagi.edziennik.dto.AttendanceEntryDto
import pl.usagi.edziennik.dto.AttendanceExcusesResponseDto
import pl.usagi.edziennik.dto.AttendanceStatisticsDto
import pl.usagi.edziennik.dto.AttendanceSubjectDto

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

fun AttendanceExcusesResponseDto.toDomainExcuses(): List<AttendanceExcuse> =
    excuses.mapNotNull { excuse ->
        val day = excuse.day?.let(::runCatchingParseDate)?.getOrNull() ?: return@mapNotNull null
        val lessonTimeId = excuse.lessonTimeId ?: return@mapNotNull null
        AttendanceExcuse(
            day = day,
            lessonTimeId = lessonTimeId,
            status = when (excuse.status) {
                0 -> AttendanceExcuseStatus.Pending
                1 -> AttendanceExcuseStatus.Approved
                else -> AttendanceExcuseStatus.Unknown(excuse.status)
            },
        )
    }

fun List<AttendanceEntry>.withExcuses(excuses: List<AttendanceExcuse>): List<AttendanceEntry> {
    val byLesson = excuses.associateBy { it.day to it.lessonTimeId }
    return map { entry ->
        val excuse = entry.lessonTimeId?.let { byLesson[entry.date to it] }
        if (excuse == null) entry else entry.copy(excuseStatus = excuse.status)
    }
}

fun List<AttendanceSubjectDto>.toDomainAttendanceSubjects(): List<AttendanceSubject> =
    map { AttendanceSubject(id = it.id, name = it.name) }

fun AttendanceStatisticsDto.toDomain(): AttendanceStatistics =
    AttendanceStatistics(
        summary = summary,
        categories = statistics.map { category ->
            AttendanceCategoryStatistics(
                status = category.attendanceCategory.toAttendanceStatus(),
                total = category.total,
                months = category.months.map { month ->
                    AttendanceMonthStatistics(month = month.month, value = month.value)
                },
                periods = category.periods,
            )
        },
    )

private fun Int?.toAttendanceStatus(): AttendanceStatus =
    when (this) {
        1 -> AttendanceStatus.Present
        2 -> AttendanceStatus.Absent
        3 -> AttendanceStatus.ExcusedAbsence
        4 -> AttendanceStatus.Late
        5 -> AttendanceStatus.ExcusedLate
        6 -> AttendanceStatus.SchoolRelatedAbsence
        7 -> AttendanceStatus.Released
        else -> AttendanceStatus.Unknown(rawValue = this)
    }

private fun runCatchingParseDate(value: String) =
    runCatching { parseApiLocalDate(value) }

private fun runCatchingParseTime(value: String) =
    runCatching { parseApiLocalTime(value) }

private fun String?.emptyToNull(): String? =
    takeUnless { it.isNullOrEmpty() }
