package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.atEndOfDayIn
import pl.usagi.edziennik.domain.time.atStartOfDayIn
import pl.usagi.edziennik.domain.time.toApiDateTime
import java.time.LocalDate
import java.time.ZoneId

internal fun ActiveStudentManager.requireActiveStudent(missingStudent: Exception): Student =
    activeStudent.value ?: throw missingStudent

internal fun LocalDate.toApiStartOfDay(zoneId: ZoneId): String =
    toApiDateTime(atStartOfDayIn(zoneId))

internal fun LocalDate.toApiEndOfDay(zoneId: ZoneId): String =
    toApiDateTime(atEndOfDayIn(zoneId))
