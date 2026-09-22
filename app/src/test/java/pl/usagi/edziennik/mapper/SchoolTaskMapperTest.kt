package pl.usagi.edziennik.mapper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.dto.HomeworkDetailsDto
import pl.usagi.edziennik.dto.SchoolTaskDto
import pl.usagi.edziennik.dto.TestDetailsDto
import java.time.LocalDate

class SchoolTaskMapperTest {
    @Test
    fun homeworkTypeMapsToHomework() {
        val task = schoolTaskDto(type = 4).toDomain()

        assertEquals(SchoolTaskType.Homework, task.type)
        assertEquals(4, task.rawType)
    }

    @Test
    fun quizTypeMapsToQuiz() {
        val task = schoolTaskDto(type = 2).toDomain()

        assertEquals(SchoolTaskType.Quiz, task.type)
        assertEquals(2, task.rawType)
    }

    @Test
    fun unknownTypeKeepsRawValueWithoutThrowing() {
        val task = schoolTaskDto(type = 999).toDomain()

        assertEquals(SchoolTaskType.Unknown(999), task.type)
        assertEquals(999, task.rawType)
    }

    @Test
    fun nullablePayloadFieldsDeserializeAndMap() {
        val payload = """
            [
              {
                "typ": 999,
                "przedmiotNazwa": null,
                "data": "2026-09-15T00:00:00+02:00",
                "id": 42
              }
            ]
        """.trimIndent()
        val dto = Json.decodeFromString<List<SchoolTaskDto>>(payload).single()
        val task = dto.toDomain()

        assertEquals(SchoolTaskType.Unknown(999), task.type)
        assertEquals(null, task.subject)
        assertEquals(false, task.hasAttachment)
        assertEquals(LocalDate.of(2026, 9, 15), task.date)
        assertEquals(42, task.id)
    }

    @Test
    fun mappingPreservesPresentationDataAndSortsByDate() {
        val tasks = listOf(
            schoolTaskDto(type = 4, id = 2, subject = "Język polski", date = "2026-09-16T00:00:00+02:00"),
            schoolTaskDto(type = 2, id = 1, subject = "Matematyka", date = "2026-09-15T00:00:00+02:00", hasAttachment = true),
        ).toDomainSchoolTasks()

        assertEquals(LocalDate.of(2026, 9, 15), tasks.first().date)
        assertEquals("Matematyka", tasks.first().subject)
        assertTrue(tasks.first().hasAttachment)
        assertEquals(1, tasks.first().id)
    }

    @Test
    fun homeworkDetailsPayloadMapsPresentationFields() {
        val details = HomeworkDetailsDto(
            type = 4,
            date = "2026-09-15T00:00:00+02:00",
            responseDeadline = "2026-09-16T00:00:00+02:00",
            subject = "Język polski",
            teacher = "Anna Nowak",
            description = "Przeczytać rozdział 4",
            id = 10,
        ).toDomainDetails()

        assertEquals("Przeczytać rozdział 4", details.description)
        assertEquals("Anna Nowak", details.teacher)
        assertEquals(LocalDate.of(2026, 9, 16), details.responseDeadline)
    }

    @Test
    fun testDetailsPayloadMapsPresentationFields() {
        val details = TestDetailsDto(
            type = 2,
            date = "2026-09-15T00:00:00+02:00",
            subject = "Matematyka",
            teacher = "Jan Kowalski",
            description = "Równania liniowe",
            id = 11,
        ).toDomainDetails()

        assertEquals("Równania liniowe", details.description)
        assertEquals("Jan Kowalski", details.teacher)
        assertEquals(null, details.responseDeadline)
    }

    @Test
    fun nullAndBlankDetailFieldsDoNotThrowAndMapToNull() {
        val homeworkDetails = HomeworkDetailsDto(
            type = 4,
            date = "2026-09-15T00:00:00+02:00",
            responseDeadline = null,
            subject = null,
            teacher = "",
            description = " ",
            id = 12,
        ).toDomainDetails()

        assertEquals(null, homeworkDetails.description)
        assertEquals(null, homeworkDetails.teacher)
        assertEquals(null, homeworkDetails.responseDeadline)
    }

    private fun schoolTaskDto(
        type: Int,
        id: Int = 1,
        subject: String? = "Matematyka",
        date: String = "2026-09-15T00:00:00+02:00",
        hasAttachment: Boolean = false,
    ): SchoolTaskDto =
        SchoolTaskDto(
            type = type,
            subject = subject,
            date = date,
            hasAttachment = hasAttachment,
            id = id,
        )
}
