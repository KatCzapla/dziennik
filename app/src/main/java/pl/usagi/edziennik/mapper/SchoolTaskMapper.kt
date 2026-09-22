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
        type = type.toSchoolTaskType(),
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

private fun Int.toSchoolTaskType(): SchoolTaskType =
    when (this) {
        HOMEWORK_TYPE -> SchoolTaskType.Homework
        QUIZ_TYPE -> SchoolTaskType.Quiz
        else -> SchoolTaskType.Unknown(this)
    }

private fun String?.emptyToNull(): String? =
    takeUnless { it.isNullOrBlank() }

private const val QUIZ_TYPE = 2
private const val HOMEWORK_TYPE = 4
