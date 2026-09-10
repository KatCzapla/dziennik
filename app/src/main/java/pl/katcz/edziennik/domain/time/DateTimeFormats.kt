package pl.katcz.edziennik.domain.time

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun parseApiLocalDate(value: String): LocalDate = parseApiDateTime(value).toLocalDate()

fun parseApiLocalTime(value: String): LocalTime = parseApiDateTime(value).toLocalTime()

fun toApiDateTime(value: ZonedDateTime): String =
    value.withZoneSameInstant(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

private fun parseApiDateTime(value: String): LocalDateTime =
    try {
        OffsetDateTime.parse(value).toLocalDateTime()
    } catch (exception: DateTimeParseException) {
        LocalDateTime.parse(value)
    }

fun LocalDate.atStartOfDayIn(zoneId: ZoneId): ZonedDateTime =
    atStartOfDay(zoneId)

fun LocalDate.atEndOfDayIn(zoneId: ZoneId): ZonedDateTime =
    atTime(23, 59, 59, 999_000_000).atZone(zoneId)
