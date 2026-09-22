package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.Student

interface StudentRepository {
    suspend fun getStudents(): List<Student>
}
