package pl.usagi.edziennik.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.usagi.edziennik.dto.ContextDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.usagi.edziennik.dto.StudentDto

class StudentMapperTest {
    @Test fun currentContextSchemaPreservesLegacyFieldsForSameFixtures() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        for (field in listOf("", ",\"idJednostkaSkladowa\":null", ",\"idJednostkaSkladowa\":1220")) {
            val body = """{"uczniowie":[{"key":"runtime","idDziennik":1,"uczen":"Uczeń","oddzial":"1A","jednostka":"Szkoła"$field}]}"""
            val legacy = json.decodeFromString<LegacyContext>(body).students.single()
            val current = json.decodeFromString<ContextDto>(body).students.single().toDomain()
            assertEquals(listOf(legacy.key, legacy.diaryId, legacy.name, legacy.className, legacy.schoolName),
                listOf(current.key, current.diaryId, current.name, current.className, current.schoolName))
        }
    }

    // The Context schema at HEAD before the optional organizational unit field.
    @Serializable
    private data class LegacyContext(@SerialName("uczniowie") val students: List<LegacyStudent>)

    @Serializable
    private data class LegacyStudent(
        @SerialName("key") val key: String,
        @SerialName("idDziennik") val diaryId: Int,
        @SerialName("uczen") val name: String,
        @SerialName("oddzial") val className: String,
        @SerialName("jednostka") val schoolName: String,
    )
    @Test fun contextAllowsUnitIdMissingOrNull() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        for (field in listOf("", ",\"idJednostkaSkladowa\":null")) {
            val body = """{"uczniowie":[{"key":"runtime","idDziennik":1,"uczen":"Uczeń","oddzial":"1A","jednostka":"Szkoła"$field}]}"""
            assertEquals(null, json.decodeFromString<ContextDto>(body).students.single().toDomain().organizationalUnitId)
        }
    }

    @Test fun contextMapsNumericUnitIdWithProductionJsonSettings() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val body = """{"uczniowie":[{"key":"runtime","idDziennik":1,"uczen":"Uczeń","oddzial":"1A","jednostka":"Szkoła","idJednostkaSkladowa":1220}]}"""
        assertEquals(1220, json.decodeFromString<ContextDto>(body).students.single().toDomain().organizationalUnitId)
    }

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
