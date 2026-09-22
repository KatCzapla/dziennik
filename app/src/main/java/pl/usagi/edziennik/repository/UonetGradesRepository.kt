package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.network.grades.UonetGradesDataSource
import javax.inject.Inject

class UonetGradesRepository @Inject constructor(
    private val dataSource: UonetGradesDataSource,
) : GradesRepository {
    override suspend fun getClassificationPeriods(student: Student): List<ClassificationPeriod> =
        dataSource.getClassificationPeriods(
            studentKey = student.key,
            diaryId = student.diaryId,
        ).map { it.toDomain() }

    override suspend fun getGrades(
        student: Student,
        period: ClassificationPeriod,
    ): Grades =
        dataSource.getGrades(
            studentKey = student.key,
            periodId = period.id,
        ).toDomain()
}
