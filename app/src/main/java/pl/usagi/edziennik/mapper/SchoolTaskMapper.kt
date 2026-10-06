package pl.usagi.edziennik.mapper

import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.time.parseApiLocalDate
import pl.usagi.edziennik.dto.HomeworkDetailsDto
import pl.usagi.edziennik.dto.SchoolTaskDto
import pl.usagi.edziennik.dto.TestDetailsDto

fun SchoolTaskDto.toDomain(): SchoolTask =
    SchoolTask(
        type = SchoolTaskType.fromRawValue(type),
        rawType = type,
        date = parseApiLocalDate(date),
        subject = subject.emptyToNull(),
        hasAttachment = hasAttachment,
        id = id,
    )

fun List<SchoolTaskDto>.toDomainSchoolTasks(): List<SchoolTask> =
    map { it.toDomain() }
        .sortedWith(compareBy<SchoolTask> { it.date }.thenBy { it.id })

fun HomeworkDetailsDto.toDomainDetails(): SchoolTaskDetails =
    SchoolTaskDetails(
        description = description.emptyToNull(),
        teacher = teacher.emptyToNull(),
        responseDeadline = responseDeadline?.let(::parseApiLocalDate),
    )

fun TestDetailsDto.toDomainDetails(): SchoolTaskDetails =
    SchoolTaskDetails(
        description = description.emptyToNull(),
        teacher = teacher.emptyToNull(),
        responseDeadline = null,
    )

private fun String?.emptyToNull(): String? =
    takeUnless { it.isNullOrBlank() }
