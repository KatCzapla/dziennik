package pl.usagi.edziennik.domain

import java.time.LocalDate

data class SchoolDayOff(
    val name: String?,
    val from: LocalDate,
    val to: LocalDate,
    val allUnits: Boolean,
    val unitIds: Set<Int>,
) {
    fun appliesTo(date: LocalDate, unitId: Int?): Boolean =
        date in from..to && (allUnits || (unitId != null && unitId in unitIds))
}

fun List<SchoolDayOff>.forDay(date: LocalDate, unitId: Int?): List<SchoolDayOff> =
    filter { it.appliesTo(date, unitId) }.distinctBy { it.name }
