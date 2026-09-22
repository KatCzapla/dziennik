package pl.usagi.edziennik.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchoolYearRangeTest {
    private val range = SchoolYearRange(
        start = LocalDate.of(2026, 9, 1),
        end = LocalDate.of(2027, 6, 30),
    )

    @Test
    fun selectableDatesStayInsideSchoolYearAndWeekdays() {
        assertTrue(LocalDate.of(2026, 9, 1).isSelectableSchoolDate(range))
        assertFalse(LocalDate.of(2026, 8, 31).isSelectableSchoolDate(range))
        assertTrue(LocalDate.of(2027, 6, 30).isSelectableSchoolDate(range))
        assertFalse(LocalDate.of(2027, 7, 1).isSelectableSchoolDate(range))
        assertFalse(LocalDate.of(2026, 9, 5).isSelectableSchoolDate(range))
    }

    @Test
    fun boundaryStartWeekShowsOnlySeptemberSchoolDaysAsSelectable() {
        val week = WeekRange.fromDate(LocalDate.of(2026, 9, 1))
        val days = (0L..4L).map { week.start.plusDays(it) }

        assertEquals(LocalDate.of(2026, 8, 31), days.first())
        assertFalse(days[0].isSelectableSchoolDate(range))
        assertTrue(days[1].isSelectableSchoolDate(range))
        assertTrue(days[4].isSelectableSchoolDate(range))
        assertFalse(week.previous().intersectsSchoolYear(range))
    }

    @Test
    fun boundaryEndWeekShowsOnlyJuneSchoolDaysAsSelectable() {
        val week = WeekRange.fromDate(LocalDate.of(2027, 6, 30))
        val days = (0L..4L).map { week.start.plusDays(it) }

        assertTrue(LocalDate.of(2027, 6, 30).isSelectableSchoolDate(range))
        assertFalse(LocalDate.of(2027, 7, 1).isSelectableSchoolDate(range))
        assertEquals(LocalDate.of(2027, 6, 30), lastSelectableSchoolDate(range))
        assertFalse(days[3].isSelectableSchoolDate(range))
        assertFalse(week.next().intersectsSchoolYear(range))
    }

    @Test
    fun julyAndAugustUseJustEndedSchoolYear() {
        assertEquals(
            SchoolYearRange(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 6, 30)),
            currentSchoolYearRange(LocalDate.of(2027, 8, 15)),
        )
    }
}
