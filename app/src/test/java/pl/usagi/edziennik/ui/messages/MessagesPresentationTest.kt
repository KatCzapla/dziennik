package pl.usagi.edziennik.ui.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.ReceivedMessage
import java.time.LocalDate
import java.time.LocalDateTime

class MessagesPresentationTest {
    @Test
    fun formatsTodayMessageAsTime() {
        val item = message(
            date = LocalDateTime.of(2026, 9, 14, 18, 32),
        ).toMessageListItem(today = LocalDate.of(2026, 9, 14))

        assertEquals("18:32", item.dateLabel)
    }

    @Test
    fun formatsOlderMessageAsDayAndMonth() {
        val item = message(
            date = LocalDateTime.of(2026, 9, 12, 8, 15),
        ).toMessageListItem(today = LocalDate.of(2026, 9, 14))

        assertEquals("12 wrz", item.dateLabel)
    }

    @Test
    fun formatsPreviousYearMessageAsShortNumericDate() {
        val item = message(
            date = LocalDateTime.of(2025, 9, 12, 8, 15),
        ).toMessageListItem(today = LocalDate.of(2026, 9, 14))

        assertEquals("12.09.25", item.dateLabel)
    }

    @Test
    fun formatsMessageDetailsDateWithDateAndTime() {
        val label = LocalDateTime.of(2026, 9, 14, 18, 32).messageDetailsDateLabel()

        assertEquals("14 wrz 2026, 18:32", label)
    }

    @Test
    fun keepsUnreadFlagForListItem() {
        assertTrue(message(isUnread = true).toMessageListItem(LocalDate.of(2026, 9, 14)).isUnread)
        assertFalse(message(isUnread = false).toMessageListItem(LocalDate.of(2026, 9, 14)).isUnread)
    }

    @Test
    fun keepsAttachmentFlagForListItem() {
        assertTrue(message(hasAttachments = true).toMessageListItem(LocalDate.of(2026, 9, 14)).hasAttachments)
        assertFalse(message(hasAttachments = false).toMessageListItem(LocalDate.of(2026, 9, 14)).hasAttachments)
    }

    private fun message(
        date: LocalDateTime = LocalDateTime.of(2026, 9, 14, 18, 32),
        isUnread: Boolean = true,
        hasAttachments: Boolean = false,
    ): ReceivedMessage =
        ReceivedMessage(
            id = 1,
            apiGlobalKey = "api-key",
            sender = "Anna Kowalska",
            subject = "Zebranie",
            date = date,
            isUnread = isUnread,
            hasAttachments = hasAttachments,
        )
}
