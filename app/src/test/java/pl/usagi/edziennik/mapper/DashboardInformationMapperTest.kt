package pl.usagi.edziennik.mapper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.dto.DashboardInformationDto
import java.time.LocalDate

class DashboardInformationMapperTest {
    @Test
    fun mapsConfirmedInformationPayloadWithoutChangingContent() {
        val dto = Json.decodeFromString<DashboardInformationDto>("""
            {"temat":"Zebrania z rodzicami","tresc":"Zapraszamy na ...","data":"2026-09-07T00:00:00+02:00"}
        """.trimIndent())

        val information = dto.toDomain()

        assertEquals("Zebrania z rodzicami", information.subject)
        assertEquals("Zapraszamy na ...", information.content)
        assertEquals(LocalDate.of(2026, 9, 7), information.date)
    }
}
