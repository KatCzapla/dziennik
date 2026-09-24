package pl.usagi.edziennik.mapper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.TimetableChangeKind
import pl.usagi.edziennik.dto.TimetableChangeDto
import pl.usagi.edziennik.dto.TimetableEntryDto
import java.time.LocalDate
import java.time.LocalTime

class TimetableMapperTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun mapsTimetableDtoToDomainEntry() {
        val dto = TimetableEntryDto(
            date = "2026-09-10T00:00:00+02:00",
            timeFrom = "2026-09-10T08:00:00+02:00",
            timeTo = "2026-09-10T08:45:00+02:00",
            teacher = "Jan Kowalski",
            subject = "Matematyka",
            room = "12",
            changes = listOf(
                TimetableChangeDto(
                    changeType = 7,
                    lessonNumber = 1,
                    timeFrom = "2026-09-10T08:00:00+02:00",
                    timeTo = "2026-09-10T08:45:00+02:00",
                    teacher = "Anna Nowak",
                ),
            ),
        )

        val entry = dto.toDomain()

        assertEquals(LocalDate.of(2026, 9, 10), entry.date)
        assertEquals(LocalTime.of(8, 0), entry.startTime)
        assertEquals(LocalTime.of(8, 45), entry.endTime)
        assertEquals("Matematyka", entry.subject)
        assertEquals("Jan Kowalski", entry.teacher)
        assertEquals("12", entry.room)
        assertNull(entry.supportingTeacher1)
        assertNull(entry.supportingTeacher2)
        assertTrue(entry.changes.single().isSubstitution)
        assertEquals(TimetableChangeKind.Substitution, entry.changes.single().kind)
    }

    @Test
    fun emptyStringsBecomeNullsLikePythonPoc() {
        val dto = TimetableEntryDto(
            date = "2026-09-10T00:00:00",
            timeFrom = "2026-09-10T08:00:00",
            timeTo = "2026-09-10T08:45:00",
            teacher = "",
            subject = "Matematyka",
            room = "",
        )

        val entry = dto.toDomain()

        assertNull(entry.teacher)
        assertNull(entry.room)
    }

    @Test
    fun emptyTimetableResponseMapsToEmptyList() {
        assertTrue(emptyList<TimetableEntryDto>().toDomainTimetable().isEmpty())
    }

    @Test
    fun changeTypeFourMapsToCancelledLessonWithReason() {
        val dto = timetableEntryDto(
            completed = false,
            changes = listOf(
                TimetableChangeDto(
                    changeType = 4,
                    teacherType = 0,
                    absenceInformation = "wyjazd integracyjny",
                ),
            ),
        )

        val entry = dto.toDomain()

        assertTrue(entry.isCancelled)
        assertEquals(TimetableChangeKind.Cancelled, entry.changes.single().kind)
        assertEquals("wyjazd integracyjny", entry.cancellationReason)
    }

    @Test
    fun changeTypeOneMapsToCancelledLessonAndKeepsDetailInformation() {
        val entry = timetableEntryDto(
            changes = listOf(
                TimetableChangeDto(
                    changeType = 1,
                    absenceInformation = "uczniowie zwolnieni do domu",
                ),
            ),
        ).toDomain()

        assertTrue(entry.isCancelled)
        assertEquals(TimetableChangeKind.Cancelled, entry.changes.single().kind)
        assertEquals("uczniowie zwolnieni do domu", entry.changes.single().absenceInformation)
        assertEquals("uczniowie zwolnieni do domu", entry.cancellationReason)
    }

    @Test
    fun changeTypeFourWithoutReasonStillMapsToCancelledLesson() {
        val entry = timetableEntryDto(
            changes = listOf(TimetableChangeDto(changeType = 4, absenceInformation = null)),
        ).toDomain()

        assertTrue(entry.isCancelled)
        assertNull(entry.cancellationReason)
    }

    @Test
    fun unknownChangeTypeKeepsRawValueWithoutCrashing() {
        val entry = timetableEntryDto(
            changes = listOf(TimetableChangeDto(changeType = 999)),
        ).toDomain()

        assertEquals(TimetableChangeKind.Unknown(999), entry.changes.single().kind)
        assertFalse(entry.isCancelled)
        assertFalse(entry.changes.single().isSubstitution)
    }

    @Test
    fun completedFalseWithoutCancelledChangeDoesNotMeanCancelled() {
        val entry = timetableEntryDto(
            completed = false,
            changes = emptyList(),
        ).toDomain()

        assertFalse(entry.isCancelled)
    }

    @Test
    fun cancelledHasDomainPriorityOverSubstitution() {
        val entry = timetableEntryDto(
            changes = listOf(
                TimetableChangeDto(
                    changeType = 7,
                    teacher = "Anna Nowak",
                ),
                TimetableChangeDto(
                    changeType = 4,
                    absenceInformation = "wyjazd integracyjny",
                ),
            ),
        ).toDomain()

        assertTrue(entry.isCancelled)
        assertEquals("wyjazd integracyjny", entry.cancellationReason)
        assertTrue(entry.changes.first().isSubstitution)
    }

    @Test
    fun changeTypeTwoWithFirstSupportingTeacherMapsToTeacherAbsence() {
        val entry = timetableEntryDto(
            supportingTeacher1 = "nazwiskoNauczyciela2",
            changes = listOf(
                TimetableChangeDto(
                    changeType = 2,
                    teacherType = 1,
                    absenceInformation = "brak",
                ),
            ),
        ).toDomain()

        val change = entry.changes.single()
        assertEquals("nazwiskoNauczyciela2", entry.supportingTeacher1)
        assertEquals(TimetableChangeKind.FirstSupportingTeacherAbsence, change.kind)
        assertTrue(change.isTeacherAbsence)
        assertEquals("brak", change.absenceEffect)
    }

    @Test
    fun teacherAbsenceWithoutEffectStillKeepsTeacherAbsenceStatus() {
        val entry = timetableEntryDto(
            changes = listOf(
                TimetableChangeDto(
                    changeType = 2,
                    teacherType = 1,
                    absenceInformation = null,
                ),
            ),
        ).toDomain()

        val change = entry.changes.single()
        assertEquals(TimetableChangeKind.FirstSupportingTeacherAbsence, change.kind)
        assertTrue(change.isTeacherAbsence)
        assertNull(change.absenceEffect)
    }

    @Test
    fun blankTeacherAbsenceEffectDoesNotProducePresentationText() {
        val entry = timetableEntryDto(
            changes = listOf(
                TimetableChangeDto(
                    changeType = 2,
                    teacherType = 1,
                    absenceInformation = "",
                ),
            ),
        ).toDomain()

        assertEquals(TimetableChangeKind.FirstSupportingTeacherAbsence, entry.changes.single().kind)
        assertNull(entry.changes.single().absenceEffect)
    }

    @Test
    fun changeTypeTwoWithUnknownTeacherTypeStaysUnknown() {
        val entry = timetableEntryDto(
            changes = listOf(
                TimetableChangeDto(
                    changeType = 2,
                    teacherType = 999,
                    absenceInformation = "brak",
                ),
            ),
        ).toDomain()

        val change = entry.changes.single()
        assertEquals(TimetableChangeKind.Unknown(2), change.kind)
        assertEquals(999, change.teacherType)
        assertFalse(change.isTeacherAbsence)
    }

    @Test
    fun decodesConfirmedCancelledLessonPayloadWithNullableChangeFields() {
        val payload = """
            {
              "data": "2026-09-14T00:00:00+02:00",
              "godzinaOd": "2026-09-14T07:30:00+02:00",
              "godzinaDo": "2026-09-14T08:15:00+02:00",
              "prowadzacy": "Kiełbowicz Diana",
              "prowadzacyWspomagajacy1": "nazwiskoNauczyciela2",
              "prowadzacyWspomagajacy2": "",
              "przedmiot": "Informatyka",
              "podzial": "|inf 1",
              "sala": "103",
              "pseudonim": null,
              "zmiany": [
                {
                  "zmiana": 4,
                  "typProwadzacego": 0,
                  "dzien": null,
                  "nrLekcji": null,
                  "godzinaOd": null,
                  "godzinaDo": null,
                  "grupa": null,
                  "zajecia": null,
                  "sala": null,
                  "prowadzacy": null,
                  "informacjeNieobecnosc": "wyjazd integracyjny"
                }
              ],
              "zmianyUwagi": [],
              "adnotacja": 3,
              "dodatkowe": false,
              "zrealizowane": false,
              "idJednostkaSkladowa": 1220
            }
        """.trimIndent()

        val entry = json.decodeFromString<TimetableEntryDto>(payload).toDomain()

        assertTrue(entry.isCancelled)
        assertEquals("nazwiskoNauczyciela2", entry.supportingTeacher1)
        assertEquals("wyjazd integracyjny", entry.cancellationReason)
        assertEquals(TimetableChangeKind.Cancelled, entry.changes.single().kind)
    }

    @Test
    fun decodesConfirmedSupportingTeacherAbsencePayloadWithNullableChangeFields() {
        val payload = """
            {
              "data": "2026-09-15T00:00:00+02:00",
              "godzinaOd": "2026-09-15T09:15:00+02:00",
              "godzinaDo": "2026-09-15T10:00:00+02:00",
              "prowadzacy": "nazwiskoNauczyciela",
              "prowadzacyWspomagajacy1": "nazwiskoNauczyciela2",
              "prowadzacyWspomagajacy2": "",
              "przedmiot": "Język polski",
              "podzial": "",
              "sala": "XXX",
              "pseudonim": null,
              "zmiany": [
                {
                  "zmiana": 2,
                  "typProwadzacego": 1,
                  "dzien": null,
                  "nrLekcji": null,
                  "godzinaOd": null,
                  "godzinaDo": null,
                  "grupa": null,
                  "zajecia": null,
                  "sala": null,
                  "prowadzacy": null,
                  "informacjeNieobecnosc": "brak"
                }
              ],
              "zmianyUwagi": [],
              "adnotacja": 0,
              "dodatkowe": false,
              "zrealizowane": false,
              "idJednostkaSkladowa": 1220
            }
        """.trimIndent()

        val entry = json.decodeFromString<TimetableEntryDto>(payload).toDomain()

        assertEquals("nazwiskoNauczyciela", entry.teacher)
        assertEquals("nazwiskoNauczyciela2", entry.supportingTeacher1)
        assertEquals(TimetableChangeKind.FirstSupportingTeacherAbsence, entry.changes.single().kind)
        assertEquals("brak", entry.changes.single().absenceEffect)
    }

    private fun timetableEntryDto(
        completed: Boolean = true,
        supportingTeacher1: String? = null,
        changes: List<TimetableChangeDto> = emptyList(),
    ) = TimetableEntryDto(
        date = "2026-09-14T00:00:00+02:00",
        timeFrom = "2026-09-14T07:30:00+02:00",
        timeTo = "2026-09-14T08:15:00+02:00",
        teacher = "Kiełbowicz Diana",
        supportingTeacher1 = supportingTeacher1,
        subject = "Informatyka",
        room = "103",
        completed = completed,
        changes = changes,
    )
}
