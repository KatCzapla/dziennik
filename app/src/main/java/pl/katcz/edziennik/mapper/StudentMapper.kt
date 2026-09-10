package pl.katcz.edziennik.mapper

import pl.katcz.edziennik.domain.Student
import pl.katcz.edziennik.dto.StudentDto

fun StudentDto.toDomain(): Student =
    Student(
        key = key,
        diaryId = diaryId,
        name = name,
        className = className,
        schoolName = schoolName,
    )

fun List<StudentDto>.toDomainStudents(): List<Student> = map { it.toDomain() }
