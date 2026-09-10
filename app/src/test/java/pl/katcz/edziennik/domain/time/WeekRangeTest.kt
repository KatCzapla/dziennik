package pl.katcz.edziennik.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekRangeTest {
    @Test
    fun weekRangeStartsOnMondayAndEndsOnSunday() {
        val week = WeekRange.fromDate(LocalDate.of(2026, 9, 10))

        assertEquals(LocalDate.of(2026, 9, 7), week.start)
        assertEquals(LocalDate.of(2026, 9, 13), week.end)
    }

    @Test
    fun previousWeekMovesSevenDaysBack() {
        val week = WeekRange.fromDate(LocalDate.of(2026, 9, 10)).previous()

        assertEquals(LocalDate.of(2026, 8, 31), week.start)
        assertEquals(LocalDate.of(2026, 9, 6), week.end)
    }

    @Test
    fun nextWeekMovesSevenDaysForward() {
        val week = WeekRange.fromDate(LocalDate.of(2026, 9, 10)).next()

        assertEquals(LocalDate.of(2026, 9, 14), week.start)
        assertEquals(LocalDate.of(2026, 9, 20), week.end)
    }
}
