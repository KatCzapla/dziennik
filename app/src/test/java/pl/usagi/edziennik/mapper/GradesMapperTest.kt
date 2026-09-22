package pl.usagi.edziennik.mapper

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.usagi.edziennik.dto.ClassificationPeriodDto
import pl.usagi.edziennik.dto.GradeColumnDto
import pl.usagi.edziennik.dto.GradeDto
import pl.usagi.edziennik.dto.GradesResponseDto
import pl.usagi.edziennik.dto.GradesSettingsDto
import pl.usagi.edziennik.dto.SubjectGradesDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

class GradesMapperTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun mapsGradeValueAsStringAndPolishDate() {
        val grade = gradeDto(value = "5", date = "10.09.2026").toDomain()

        assertEquals("5", grade.value)
        assertEquals(LocalDate.of(2026, 9, 10), grade.date)
    }

    @Test
    fun mapsPlusGradeValueAsString() {
        val grade = gradeDto(value = "+").toDomain()

        assertEquals("+", grade.value)
    }

    @Test
    fun mapsWeightTeacherAndCorrectedGradeNullability() {
        val grade = gradeDto(
            weight = JsonPrimitive(1),
            teacher = null,
            correctedGradeId = null,
        ).toDomain()

        assertEquals(BigDecimal("1"), grade.weight)
        assertNull(grade.teacher)
        assertNull(grade.correctedGradeId)
    }

    @Test
    fun mapsEmptyColumnsAndSubjectWithoutGrades() {
        val subject = subjectDto(columns = emptyList()).toDomain()

        assertEquals(emptyList<Any>(), subject.columns)
        assertEquals(0, subject.columns.sumOf { it.grades.size })
    }

    @Test
    fun mapsSettings() {
        val settings = GradesSettingsDto(
            showAverageAndPoints = true,
            adultStudent = true,
            descriptiveGrade = false,
            lastClassificationPeriod = true,
        ).toDomain()

        assertEquals(true, settings.showAverageAndPoints)
        assertEquals(true, settings.adultStudent)
        assertEquals(false, settings.descriptiveGrade)
        assertEquals(true, settings.lastClassificationPeriod)
    }

    @Test
    fun preservesAverageZeroWithoutInterpretingMeaning() {
        val grades = GradesResponseDto(
            subjects = listOf(subjectDto(average = JsonPrimitive(0.0))),
            settings = GradesSettingsDto(showAverageAndPoints = false),
        ).toDomain()

        assertEquals(BigDecimal("0.0"), grades.subjects.first().average)
        assertEquals(false, grades.settings.showAverageAndPoints)
    }

    @Test
    fun mapsClassificationPeriodWithOffsets() {
        val period = ClassificationPeriodDto(
            periodNumber = 1,
            dateFrom = "2026-09-01T00:00:00+02:00",
            dateTo = "2027-01-31T00:00:00+01:00",
            id = 123,
        ).toDomain()

        assertEquals(123, period.id)
        assertEquals(1, period.number)
        assertEquals(OffsetDateTime.parse("2026-09-01T00:00:00+02:00"), period.startDate)
        assertEquals(OffsetDateTime.parse("2027-01-31T00:00:00+01:00"), period.endDate)
    }

    @Test
    fun decodesConfirmedGradesPayloadWithNullsDecimalsAndEmptyColumns() {
        val payload = """
            {
              "ocenyPrzedmioty": [
                {
                  "przedmiotNazwa": "Wychowanie fizyczne",
                  "pozycja": 11,
                  "uwzglednijWageOcen": true,
                  "nauczyciele": ["Jan Kowalski"],
                  "kolumnyOcenyCzastkowe": [
                    {
                      "idKolumny": 123,
                      "kategoriaKolumny": "Aktywność",
                      "nazwaKolumny": "aktywność",
                      "oceny": [
                        {
                          "wpis": "5",
                          "dataOceny": "10.09.2026",
                          "idKolumny": 123,
                          "kategoriaKolumny": "Aktywność",
                          "nazwaKolumny": "aktywność",
                          "waga": 1.00,
                          "kolorOceny": 0,
                          "nauczyciel": "Jan Kowalski",
                          "zmienionaOdOstatniegoLogowania": true,
                          "idOcenaPoprawiona": null
                        }
                      ]
                    }
                  ],
                  "egzaminFormaPraktyczna": null,
                  "egzaminFormaUstna": null,
                  "egzaminOcenaProponowana": null,
                  "egzaminOcenaLaczna": null,
                  "sumaPunktow": "",
                  "sumaPunktowWszystkieSemestry": "",
                  "srednia": 0.0,
                  "sredniaWszystkieSemestry": 0.0,
                  "proponowanaOcenaOkresowa": null,
                  "proponowanaOcenaOkresowaPunkty": "",
                  "ocenaOkresowa": null,
                  "ocenaOkresowaPunkty": "",
                  "podsumowanieOcen": null
                },
                {
                  "przedmiotNazwa": "Informatyka",
                  "pozycja": 12,
                  "uwzglednijWageOcen": true,
                  "nauczyciele": [],
                  "kolumnyOcenyCzastkowe": [],
                  "srednia": 0.0,
                  "sredniaWszystkieSemestry": 0.0,
                  "podsumowanieOcen": null
                }
              ],
              "ustawienia": {
                "isSredniaAndPunkty": false,
                "isDorosli": false,
                "isOcenaOpisowa": false,
                "isOstatniOkresKlasyfikacyjny": false
              }
            }
        """.trimIndent()

        val grades = json.decodeFromString<GradesResponseDto>(payload).toDomain()

        assertEquals(2, grades.subjects.size)
        assertEquals("5", grades.subjects.first().columns.first().grades.first().value)
        assertEquals(BigDecimal("1.00"), grades.subjects.first().columns.first().grades.first().weight)
        assertEquals(BigDecimal("0.0"), grades.subjects.first().average)
        assertEquals(0, grades.subjects[1].columns.sumOf { it.grades.size })
    }

    @Test
    fun decodesFuturePeriodWithSubjectsAndZeroGradesAsValidResponse() {
        val payload = """
            {
              "ocenyPrzedmioty": [
                {
                  "przedmiotNazwa": "Matematyka",
                  "pozycja": 1,
                  "uwzglednijWageOcen": true,
                  "nauczyciele": [],
                  "kolumnyOcenyCzastkowe": [],
                  "srednia": 0.0,
                  "sredniaWszystkieSemestry": 0.0,
                  "podsumowanieOcen": null
                },
                {
                  "przedmiotNazwa": "Informatyka",
                  "pozycja": 2,
                  "uwzglednijWageOcen": true,
                  "nauczyciele": [],
                  "kolumnyOcenyCzastkowe": [],
                  "srednia": 0.0,
                  "sredniaWszystkieSemestry": 0.0,
                  "podsumowanieOcen": null
                }
              ],
              "ustawienia": {
                "isSredniaAndPunkty": false,
                "isDorosli": false,
                "isOcenaOpisowa": false,
                "isOstatniOkresKlasyfikacyjny": true
              }
            }
        """.trimIndent()

        val grades = json.decodeFromString<GradesResponseDto>(payload).toDomain()

        assertEquals(2, grades.subjects.size)
        assertEquals(0, grades.subjects.sumOf { subject -> subject.columns.sumOf { it.grades.size } })
        assertEquals(true, grades.settings.lastClassificationPeriod)
    }


    private fun subjectDto(
        columns: List<GradeColumnDto> = listOf(columnDto()),
        average: kotlinx.serialization.json.JsonElement? = JsonNull,
    ) = SubjectGradesDto(
        subjectName = "Matematyka",
        position = 2,
        includeGradeWeight = true,
        teachers = listOf("Jan Kowalski"),
        columns = columns,
        average = average,
    )

    private fun columnDto() = GradeColumnDto(
        id = 10,
        category = "Aktywność",
        name = "Aktywność",
        grades = listOf(gradeDto()),
    )

    private fun gradeDto(
        value: String = "5",
        date: String = "10.09.2026",
        weight: kotlinx.serialization.json.JsonElement? = JsonPrimitive(1),
        teacher: String? = "Jan Kowalski",
        correctedGradeId: Int? = 42,
    ) = GradeDto(
        value = value,
        date = date,
        columnId = 10,
        category = "Aktywność",
        columnName = "Aktywność",
        weight = weight,
        teacher = teacher,
        correctedGradeId = correctedGradeId,
    )
}
