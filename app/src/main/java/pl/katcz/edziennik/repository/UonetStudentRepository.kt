package pl.katcz.edziennik.repository

import pl.katcz.edziennik.domain.Student
import pl.katcz.edziennik.mapper.toDomainStudents
import pl.katcz.edziennik.network.context.UonetContextDataSource
import javax.inject.Inject

class UonetStudentRepository @Inject constructor(
    private val contextDataSource: UonetContextDataSource,
) : StudentRepository {
    override suspend fun getStudents(): List<Student> =
        contextDataSource.getContext().students.toDomainStudents()
}
