package pl.usagi.edziennik.widget

import pl.usagi.edziennik.persistence.changes.LocalTimetableResult
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TodayTimetablePresentation(
    val dateLabel: String,
    val lessons: List<TodayLesson>,
    val message: String?,
    val lastCompleteLabel: String?,
    val isLoggedOut: Boolean = false,
)

data class TodayLesson(
    val startTime: String,
    val subject: String,
    val room: String?,
    val cancelled: Boolean,
    val substituted: Boolean,
)

private val polish = Locale.forLanguageTag("pl-PL")
private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", polish)
private val syncFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", polish)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun presentTodayTimetable(
    result: LocalTimetableResult,
    date: LocalDate,
    zoneId: ZoneId,
): TodayTimetablePresentation {
    val dateLabel = date.format(dateFormat)
    return when (result) {
        LocalTimetableResult.LoggedOut -> TodayTimetablePresentation(
            dateLabel, emptyList(), "Zaloguj się, aby zobaczyć plan lekcji", null, isLoggedOut = true,
        )
        LocalTimetableResult.NoBaseline -> TodayTimetablePresentation(
            dateLabel, emptyList(), "Plan nie został jeszcze zsynchronizowany.", null,
        )
        is LocalTimetableResult.Available -> TodayTimetablePresentation(
            dateLabel = dateLabel,
            lessons = result.lessons.filter { it.slot.date == date }
                .sortedBy { it.slot.start }
                .map { lesson ->
                    TodayLesson(
                        startTime = lesson.slot.start.format(timeFormat),
                        subject = lesson.subject,
                        room = lesson.room?.takeIf { it.isNotBlank() },
                        cancelled = lesson.cancelled,
                        substituted = lesson.substitutions.isNotEmpty(),
                    )
                },
            message = if (result.lessons.none { it.slot.date == date }) "Dzisiaj nie ma lekcji." else null,
            lastCompleteLabel = result.lastCompleteAt?.atZone(zoneId)?.format(syncFormat),
        )
    }
}
