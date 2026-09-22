package pl.usagi.edziennik.ui.messages

import pl.usagi.edziennik.domain.ReceivedMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MessageListItem(
    val id: Int,
    val sender: String,
    val subject: String,
    val dateLabel: String,
    val isUnread: Boolean,
    val hasAttachments: Boolean,
)

fun ReceivedMessage.toMessageListItem(today: LocalDate): MessageListItem =
    MessageListItem(
        id = id,
        sender = sender,
        subject = subject,
        dateLabel = date.dashboardMessageDateLabel(today),
        isUnread = isUnread,
        hasAttachments = hasAttachments,
    )

fun Int?.dashboardUnreadBadgeLabel(): String? =
    when {
        this == null || this <= 0 -> null
        this > 99 -> "99+"
        else -> toString()
    }

fun LocalDateTime.messageDetailsDateLabel(): String =
    format(detailsDateFormatter)

private fun java.time.LocalDateTime.dashboardMessageDateLabel(today: LocalDate): String =
    when (val date = toLocalDate()) {
        today -> format(timeFormatter)
        today.minusDays(1) -> "Wczoraj"
        else -> if (date.year == today.year) {
            format(dayMonthFormatter)
        } else {
            format(shortDateFormatter)
        }
    }

private val polishLocale = Locale.forLanguageTag("pl-PL")
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", polishLocale)
private val shortDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")
private val detailsDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", polishLocale)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
