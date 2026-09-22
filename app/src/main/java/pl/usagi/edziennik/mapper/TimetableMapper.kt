package pl.usagi.edziennik.mapper

import pl.usagi.edziennik.domain.TimetableChange
import pl.usagi.edziennik.domain.TimetableChangeKind
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.time.parseApiLocalDate
import pl.usagi.edziennik.domain.time.parseApiLocalTime
import pl.usagi.edziennik.dto.TimetableChangeDto
import pl.usagi.edziennik.dto.TimetableEntryDto

fun TimetableEntryDto.toDomain(): TimetableEntry =
    TimetableEntry(
        date = parseApiLocalDate(date),
        lessonNumber = null,
        startTime = parseApiLocalTime(timeFrom),
        endTime = parseApiLocalTime(timeTo),
        subject = subject,
        teacher = teacher.emptyToNull(),
        supportingTeacher1 = supportingTeacher1.emptyToNull(),
        supportingTeacher2 = supportingTeacher2.emptyToNull(),
        room = room.emptyToNull(),
        changes = changes.map { it.toDomain() },
    )

fun TimetableChangeDto.toDomain(): TimetableChange =
    TimetableChange(
        changeType = changeType,
        kind = toTimetableChangeKind(
            changeType = changeType,
            teacherType = teacherType,
        ),
        teacherType = teacherType,
        day = day.emptyToNull(),
        lessonNumber = lessonNumber,
        startTime = timeFrom?.let(::parseApiLocalTime),
        endTime = timeTo?.let(::parseApiLocalTime),
        group = group.emptyToNull(),
        subject = subject.emptyToNull(),
        teacher = teacher.emptyToNull(),
        room = room.emptyToNull(),
        absenceInformation = absenceInformation.emptyToNull(),
    )

fun List<TimetableEntryDto>.toDomainTimetable(): List<TimetableEntry> =
    map { it.toDomain() }
        .sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startTime })

private fun String?.emptyToNull(): String? =
    takeUnless { it.isNullOrBlank() }

private fun toTimetableChangeKind(
    changeType: Int,
    teacherType: Int?,
): TimetableChangeKind =
    when {
        changeType == TEACHER_ABSENCE_CHANGE_TYPE && teacherType == FIRST_SUPPORTING_TEACHER_TYPE ->
            TimetableChangeKind.FirstSupportingTeacherAbsence

        changeType == CANCELLED_CHANGE_TYPE -> TimetableChangeKind.Cancelled
        changeType == SUBSTITUTION_CHANGE_TYPE -> TimetableChangeKind.Substitution
        else -> TimetableChangeKind.Unknown(changeType)
    }

private const val TEACHER_ABSENCE_CHANGE_TYPE = 2
private const val FIRST_SUPPORTING_TEACHER_TYPE = 1
private const val CANCELLED_CHANGE_TYPE = 4
private const val SUBSTITUTION_CHANGE_TYPE = 7
