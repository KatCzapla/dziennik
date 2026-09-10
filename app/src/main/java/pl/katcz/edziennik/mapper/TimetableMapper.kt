package pl.katcz.edziennik.mapper

import pl.katcz.edziennik.domain.TimetableChange
import pl.katcz.edziennik.domain.TimetableEntry
import pl.katcz.edziennik.domain.time.parseApiLocalDate
import pl.katcz.edziennik.domain.time.parseApiLocalTime
import pl.katcz.edziennik.dto.TimetableChangeDto
import pl.katcz.edziennik.dto.TimetableEntryDto

fun TimetableEntryDto.toDomain(): TimetableEntry =
    TimetableEntry(
        date = parseApiLocalDate(date),
        lessonNumber = null,
        startTime = parseApiLocalTime(timeFrom),
        endTime = parseApiLocalTime(timeTo),
        subject = subject,
        teacher = teacher.emptyToNull(),
        room = room.emptyToNull(),
        changes = changes.map { it.toDomain() },
    )

fun TimetableChangeDto.toDomain(): TimetableChange =
    TimetableChange(
        changeType = changeType,
        lessonNumber = lessonNumber,
        startTime = timeFrom?.let(::parseApiLocalTime),
        endTime = timeTo?.let(::parseApiLocalTime),
        subject = subject.emptyToNull(),
        teacher = teacher.emptyToNull(),
        room = room.emptyToNull(),
        absenceInformation = absenceInformation.emptyToNull(),
    )

fun List<TimetableEntryDto>.toDomainTimetable(): List<TimetableEntry> =
    map { it.toDomain() }
        .sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startTime })

private fun String?.emptyToNull(): String? =
    takeUnless { it.isNullOrEmpty() }
