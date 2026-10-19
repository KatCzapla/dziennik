package pl.usagi.edziennik.ui.timetable

import org.junit.Assert.*
import org.junit.Test
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.dto.TimetableChangeDto
import pl.usagi.edziennik.dto.TimetableEntryDto
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.ui.dashboard.toDashboardLessons
import java.time.LocalDateTime

class TimetableBlockTest {
    private fun lesson(additional: Boolean? = false): TimetableEntry = TimetableEntryDto(
        date = "2026-10-16T00:00:00+02:00",
        timeFrom = "2026-10-16T13:05:00+02:00",
        timeTo = "2026-10-16T13:50:00+02:00",
        subject = if (additional == true) "Matematyka" else "Kreatywność",
        teacher = "Nauczyciel", room = "106", additional = additional,
    ).toDomain()

    @Test fun sameSlotMergesOnePrimaryAndOneAdditional() {
        val primary = lesson()
        val additional = lesson(true)
        val block = listOf(primary, additional).toTimetableBlocks().single()
        assertSame(primary, block.primary)
        assertSame(additional, block.additional)
    }

    @Test fun additionalFirstStillDisplaysPrimaryFirst() {
        val primary = lesson()
        val additional = lesson(true)
        assertEquals(TimetableBlock(primary, additional), listOf(additional, primary).toTimetableBlocks().single())
    }

    @Test fun differentStartTimesRemainSeparate() {
        val primary = lesson()
        assertSeparate(listOf(primary, lesson(true).copy(startTime = primary.startTime.plusMinutes(1))))
    }

    @Test fun differentEndTimesRemainSeparate() {
        val primary = lesson()
        assertSeparate(listOf(primary, lesson(true).copy(endTime = primary.endTime.plusMinutes(1))))
    }

    @Test fun differentDaysRemainSeparate() {
        val primary = lesson()
        assertSeparate(listOf(primary, lesson(true).copy(date = primary.date.plusDays(1))))
    }

    @Test fun noPrimaryRemainsSeparate() = assertSeparate(listOf(lesson(true), lesson(true)))

    @Test fun unknownAdditionalFlagIsNotAssumedPrimary() = assertSeparate(listOf(lesson(null), lesson(true)))

    @Test fun multipleCandidatesAreNeverMergedOrDropped() {
        assertSeparate(listOf(lesson(), lesson(), lesson(true)))
        assertSeparate(listOf(lesson(), lesson(true), lesson(true)))
        assertSeparate(listOf(lesson(), lesson(true), lesson(null)))
    }

    @Test fun separateRoomsTeachersAndAnnotationsAreRetained() {
        val primary = lesson().copy(annotation = 3)
        val additional = lesson(true).copy(teacher = "Inny nauczyciel", room = "215", annotation = 999)
        val block = listOf(primary, additional).toTimetableBlocks().single()
        assertEquals("106", block.primary.room)
        assertEquals("Nauczyciel", block.primary.teacher)
        assertEquals(3, block.primary.annotation)
        assertEquals("215", block.additional!!.room)
        assertEquals("Inny nauczyciel", block.additional.teacher)
        assertEquals(999, block.additional.annotation)
    }

    @Test fun cancellationAndSubstitutionStayWithTheirOwnLesson() {
        val primary = lesson().copy(changes = listOf(TimetableChangeDto(4, absenceInformation = "Powód").toDomain()))
        val additional = lesson(true).copy(changes = listOf(TimetableChangeDto(7, teacher = "Zastępca").toDomain()))
        val block = listOf(primary, additional).toTimetableBlocks().single()
        assertTrue(block.primary.isCancelled)
        assertEquals("Powód", block.primary.cancellationReason)
        assertFalse(block.additional!!.isCancelled)
        assertTrue(block.additional.changes.single().isSubstitution)
        assertEquals("Zastępca", block.additional.changes.single().teacher)
    }

    @Test fun dashboardUsesSameBlockWithoutLosingAdditionalChanges() {
        val primary = lesson()
        val additional = lesson(true).copy(changes = listOf(TimetableChangeDto(4).toDomain()))
        val result = listOf(additional, primary).toDashboardLessons(primary.date, LocalDateTime.of(primary.date, primary.startTime))
        assertEquals(TimetableBlock(primary, additional), result.single().block)
        assertTrue(result.single().block!!.additional!!.isCancelled)
    }

    @Test fun mapperPreservesAdditionalAndAnnotationWithoutMutatingDto() {
        val dto = TimetableEntryDto("2026-10-16T00:00:00+02:00", "2026-10-16T13:05:00+02:00", "2026-10-16T13:50:00+02:00", subject = "Matematyka", additional = true, annotation = 3)
        val entry = dto.toDomain()
        assertEquals(dto.additional, entry.additional)
        assertEquals(dto.annotation, entry.annotation)
    }

    @Test fun dashboardPreservesAmbiguousSlotsAsSeparateRows() {
        val primary = lesson()
        val entries = listOf(primary, lesson(true), lesson(true))
        val result = entries.toDashboardLessons(primary.date, LocalDateTime.of(primary.date, primary.startTime))
        assertEquals(3, result.size)
        assertTrue(result.all { it.block?.additional == null })
    }

    @Test fun groupingDoesNotModifyOrDeduplicateInput() {
        val entries = mutableListOf(lesson(), lesson(true), lesson(true))
        val original = entries.toList()
        assertEquals(3, entries.toTimetableBlocks().size)
        assertEquals(original, entries)
    }

    private fun assertSeparate(entries: List<TimetableEntry>) {
        val blocks = entries.toTimetableBlocks()
        assertEquals(entries, blocks.map { it.primary })
        assertTrue(blocks.all { it.additional == null })
    }
}
