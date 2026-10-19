package pl.usagi.edziennik.ui.timetable

import pl.usagi.edziennik.domain.TimetableEntry
import java.time.LocalDate
import java.time.LocalTime

data class TimetableBlock(
    val primary: TimetableEntry,
    val additional: TimetableEntry? = null,
)

fun List<TimetableEntry>.toTimetableBlocks(): List<TimetableBlock> {
    val slots = groupBy { Triple(it.date, it.startTime, it.endTime) }
    val emitted = mutableSetOf<Triple<LocalDate, LocalTime, LocalTime>>()
    return buildList {
        for (entry in this@toTimetableBlocks) {
            val key = Triple(entry.date, entry.startTime, entry.endTime)
            val entries = slots.getValue(key)
            val primary = entries.singleOrNull { it.additional == false }
            val additional = entries.singleOrNull { it.additional == true }
            if (entries.size == 2 && primary != null && additional != null) {
                if (emitted.add(key)) add(TimetableBlock(primary, additional))
            } else {
                add(TimetableBlock(entry))
            }
        }
    }
}
