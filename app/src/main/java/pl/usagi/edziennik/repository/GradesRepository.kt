package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.Student

interface GradesRepository {
    suspend fun getClassificationPeriods(student: Student): List<ClassificationPeriod>

    suspend fun getGrades(
        student: Student,
        period: ClassificationPeriod,
    ): Grades
}
