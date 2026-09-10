package pl.katcz.edziennik.repository

import pl.katcz.edziennik.domain.Student

interface StudentRepository {
    suspend fun getStudents(): List<Student>
}
