package pl.usagi.edziennik.mapper

import pl.usagi.edziennik.domain.SchoolDayOff
import pl.usagi.edziennik.domain.time.parseApiLocalDate
import pl.usagi.edziennik.dto.SchoolDayOffDto
import java.util.Locale

fun SchoolDayOffDto.toDomain(): SchoolDayOff {
    val fromDate = parseApiLocalDate(from)
    val toDate = parseApiLocalDate(to)
    require(!toDate.isBefore(fromDate)) { "Invalid day off date range" }
    val locale = Locale.forLanguageTag("pl-PL")
    val label = name?.trim()?.takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))
        ?.joinToString(" ") { word -> word.lowercase(locale).replaceFirstChar { it.titlecase(locale) } }
    return SchoolDayOff(label, fromDate, toDate, allUnits, units.mapTo(mutableSetOf()) { it.id })
}
