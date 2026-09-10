package pl.katcz.edziennik.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.katcz.edziennik.dto.StudentDto

class StudentMapperTest {
    @Test
    fun mapsContextStudentDtoToDomainStudent() {
        val dto = StudentDto(
            key = "student-key",
            diaryId = 42,
            name = "Jan Kowalski",
            className = "4A",
            schoolName = "Szkola Podstawowa",
        )

        val student = dto.toDomain()

        assertEquals("student-key", student.key)
        assertEquals(42, student.diaryId)
        assertEquals("Jan Kowalski", student.name)
        assertEquals("4A", student.className)
        assertEquals("Szkola Podstawowa", student.schoolName)
    }
}
