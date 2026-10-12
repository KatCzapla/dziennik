package pl.usagi.edziennik.persistence.changes

import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.clampedSchoolWeekEnd
import pl.usagi.edziennik.domain.time.clampedSchoolWeekStart
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import java.time.LocalDate

internal data class TaskSnapshotWeek(val from: LocalDate, val to: LocalDate, val scopeRange: String)

internal fun taskSnapshotWeeks(start: LocalDate): List<TaskSnapshotWeek> = (0L..13L).mapNotNull { offset ->
    val date = start.plusDays(offset)
    val year = currentSchoolYearRange(date)
    if (date < year.start || date > year.end) return@mapNotNull null
    val week = WeekRange.fromDate(date)
    val from = maxOf(week.start, year.start)
    val to = minOf(week.end, year.end)
    val oldFrom = week.clampedSchoolWeekStart(year)
    val oldTo = week.clampedSchoolWeekEnd(year)
    // Keep existing weekday keys; a school year can also start on a weekend.
    val scopeRange = if (oldFrom <= oldTo) "$oldFrom/$oldTo" else "$from/$to"
    TaskSnapshotWeek(from, to, scopeRange)
}.distinct()
