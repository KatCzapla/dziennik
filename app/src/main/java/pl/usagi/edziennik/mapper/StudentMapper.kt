package pl.usagi.edziennik.mapper

import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.dto.StudentDto

fun StudentDto.toDomain(): Student =
    Student(
        key = key,
        diaryId = diaryId,
        name = name,
        className = className,
        schoolName = schoolName,
    )

fun List<StudentDto>.toDomainStudents(): List<Student> = map { it.toDomain() }
