package pl.usagi.edziennik.persistence.changes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskSnapshotWeeksTest {
    @Test fun mondayHorizonUsesTwoCalendarWeeksAndKeepsExistingKeys() {
        val weeks = taskSnapshotWeeks(LocalDate.of(2026, 10, 12))
        assertEquals(2, weeks.size)
        assertEquals(TaskSnapshotWeek(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18),
            "2026-10-12/2026-10-16"), weeks.first())
        assertEquals(LocalDate.of(2026, 10, 25), weeks.last().to)
    }

    @Test fun wednesdayHorizonUsesThreeWeeks() {
        val weeks = taskSnapshotWeeks(LocalDate.of(2026, 9, 30))
        assertEquals(listOf("2026-09-28/2026-10-02", "2026-10-05/2026-10-09",
            "2026-10-12/2026-10-16"), weeks.map { it.scopeRange })
        assertEquals(LocalDate.of(2026, 10, 18), weeks.last().to)
    }

    @Test fun saturdayDoesNotSkipTheCurrentWeek() {
        val weeks = taskSnapshotWeeks(LocalDate.of(2026, 10, 17))
        assertEquals(3, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 12), weeks.first().from)
        assertEquals(LocalDate.of(2026, 10, 18), weeks.first().to)
    }

    @Test fun yearEndClipsRequestsAndKeepsFinalWeekend() {
        val weeks = taskSnapshotWeeks(LocalDate.of(2024, 6, 29))
        assertEquals(listOf(TaskSnapshotWeek(LocalDate.of(2024, 6, 24), LocalDate.of(2024, 6, 30),
            "2024-06-24/2024-06-28")), weeks)
    }

    @Test fun yearStartClipsFirstCalendarWeek() {
        val weeks = taskSnapshotWeeks(LocalDate.of(2026, 9, 1))
        assertEquals(LocalDate.of(2026, 9, 1), weeks.first().from)
        assertEquals("2026-09-01/2026-09-04", weeks.first().scopeRange)
        assertEquals(LocalDate.of(2026, 9, 6), weeks.first().to)
    }

    @Test fun horizonStartingInAugustIncludesUpcomingSchoolYear() {
        val weeks = taskSnapshotWeeks(LocalDate.of(2026, 8, 28))
        assertEquals(2, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 1), weeks.first().from)
        assertEquals(LocalDate.of(2026, 9, 13), weeks.last().to)
    }

    @Test fun schoolYearStartingOnSundayHasAValidWeekendOnlyScope() {
        val weeks = taskSnapshotWeeks(LocalDate.of(2024, 9, 1))
        assertEquals(TaskSnapshotWeek(LocalDate.of(2024, 9, 1), LocalDate.of(2024, 9, 1),
            "2024-09-01/2024-09-01"), weeks.first())
    }

    @Test fun summerRangeHasNoSchoolYearRequests() {
        assertTrue(taskSnapshotWeeks(LocalDate.of(2026, 7, 15)).isEmpty())
    }
}
