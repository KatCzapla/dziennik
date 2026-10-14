package pl.usagi.edziennik.mapper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import pl.usagi.edziennik.domain.forDay
import pl.usagi.edziennik.dto.DayOffUnitDto
import pl.usagi.edziennik.dto.SchoolDayOffDto
import pl.usagi.edziennik.dto.ContextDto
import java.time.LocalDate

class SchoolDayOffMapperTest {
    private val day = LocalDate.of(2026, 10, 14)
    private fun dto(all: Boolean = false) = SchoolDayOffDto("dZień Edukacji narodowej",
        "2026-10-14T00:00:00+02:00", "2026-10-14T00:00:00+02:00", all, listOf(DayOffUnitDto(1220)))

    @Test fun singleDayAppliesOnlyToItsDate() {
        val mapped = dto().toDomain()
        assertTrue(mapped.appliesTo(day, 1220))
        assertFalse(mapped.appliesTo(day.minusDays(1), 1220))
        assertFalse(mapped.appliesTo(day.plusDays(1), 1220))
    }

    @Test fun multiDayRangeIncludesBothEndpoints() {
        val mapped = dto().copy(to = "2026-10-16T00:00:00+02:00").toDomain()
        (0L..2L).forEach { assertTrue(mapped.appliesTo(day.plusDays(it), 1220)) }
        assertFalse(mapped.appliesTo(day.plusDays(3), 1220))
    }

    @Test fun positiveOffsetDoesNotMoveMidnightToPreviousUtcDay() {
        assertEquals(day, dto().toDomain().from)
        assertEquals(day, dto().toDomain().to)
    }

    @Test fun allUnitsAppliesEvenWithoutStudentUnitId() {
        assertTrue(dto(all = true).toDomain().appliesTo(day, null))
        assertTrue(dto(all = true).toDomain().appliesTo(day, 999))
    }

    @Test fun restrictedDayNeedsMatchingContextUnitId() {
        assertTrue(dto().toDomain().appliesTo(day, 1220))
        assertFalse(dto().toDomain().appliesTo(day, 999))
        assertFalse(dto().toDomain().appliesTo(day, null))
    }

    @Test fun emptyUnitListIsNotInterpretedAsAllUnits() {
        assertFalse(dto().copy(units = emptyList()).toDomain().appliesTo(day, 1220))
    }

    @Test fun mixedCaseNameIsNormalizedWithoutMutatingDto() {
        val raw = dto()
        assertEquals("Dzień Edukacji Narodowej", raw.toDomain().name)
        assertEquals("dZień Edukacji narodowej", raw.name)
    }

    @Test fun whitespaceIsNormalizedAndBlankNameUsesUiFallback() {
        assertEquals("Dzień Wolny", dto().copy(name = " \n dZIEŃ\t WOLNY ").toDomain().name)
        assertNull(dto().copy(name = " \t ").toDomain().name)
        assertNull(dto().copy(name = null).toDomain().name)
    }

    @Test fun multipleEntriesForSameDayAreRetainedWithoutDuplicateLabels() {
        val first = dto().toDomain()
        val second = first.copy(name = "Inny Dzień Wolny")
        assertEquals(listOf(first, second), listOf(first, second, first).forDay(day, 1220))
    }

    @Test fun reversedRangeIsRejectedInsteadOfShowingWrongDay() {
        assertTrue(runCatching { dto().copy(to = "2026-10-13T00:00:00+02:00").toDomain() }.isFailure)
    }

    @Test fun contextUnitIdReachesStudentDomainAndMissingIdStaysNull() {
        val json = Json { ignoreUnknownKeys = true }
        val context = json.decodeFromString<ContextDto>("""{"uczniowie":[{"key":"runtime","idDziennik":1,"uczen":"Uczeń","oddzial":"1A","jednostka":"Szkoła","idJednostkaSkladowa":1220}]}""")
        assertEquals(1220, context.students.single().toDomain().organizationalUnitId)
        assertNull(context.students.single().copy(organizationalUnitId = null).toDomain().organizationalUnitId)
    }
}
