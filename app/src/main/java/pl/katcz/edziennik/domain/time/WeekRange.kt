package pl.katcz.edziennik.domain.time

import java.time.LocalDate

data class WeekRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    fun previous(): WeekRange = fromDate(start.minusWeeks(1))

    fun next(): WeekRange = fromDate(start.plusWeeks(1))

    companion object {
        fun current(today: LocalDate = LocalDate.now()): WeekRange = fromDate(today)

        fun fromDate(date: LocalDate): WeekRange {
            val start = date.minusDays(date.dayOfWeek.value.toLong() - 1)
            return WeekRange(
                start = start,
                end = start.plusDays(6),
            )
        }
    }
}
