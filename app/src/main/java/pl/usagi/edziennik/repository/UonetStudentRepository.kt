package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.mapper.toDomainStudents
import pl.usagi.edziennik.network.context.UonetContextDataSource
import javax.inject.Inject

class UonetStudentRepository @Inject constructor(
    private val contextDataSource: UonetContextDataSource,
) : StudentRepository {
    override suspend fun getStudents(): List<Student> =
        contextDataSource.getContext().students.toDomainStudents()
}
