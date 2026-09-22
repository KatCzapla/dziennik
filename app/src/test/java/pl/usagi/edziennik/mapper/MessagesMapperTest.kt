package pl.usagi.edziennik.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.dto.MailboxDto
import pl.usagi.edziennik.dto.MessageAttachmentDto
import pl.usagi.edziennik.dto.MessageDetailsDto
import pl.usagi.edziennik.dto.ReceivedMessageDto
import java.time.LocalDateTime

class MessagesMapperTest {
    @Test
    fun mapsStableMessageIdSenderSubjectDateAndUnreadState() {
        val message = messageDto(
            id = 123,
            correspondents = "Anna Kowalska",
            subject = "Zebranie",
            date = "2026-09-14T18:32:00+02:00",
            read = false,
        ).toDomain()

        assertEquals(123, message.id)
        assertEquals("api-key-123", message.apiGlobalKey)
        assertEquals("Anna Kowalska", message.sender)
        assertEquals("Zebranie", message.subject)
        assertEquals(LocalDateTime.of(2026, 9, 14, 18, 32), message.date)
        assertTrue(message.isUnread)
    }

    @Test
    fun mapsReadMessageAsNotUnread() {
        val message = messageDto(read = true).toDomain()

        assertFalse(message.isUnread)
    }

    @Test
    fun handlesConfirmedNullableFieldsWithoutAffectingListModel() {
        val message = messageDto(
            correspondentsDetails = null,
            readBy = null,
        ).toDomain()

        assertEquals("Anna Kowalska", message.sender)
        assertEquals("Zebranie", message.subject)
    }

    @Test
    fun mapsMailboxFields() {
        val mailbox = MailboxDto(
            globalKey = "mailbox-key",
            name = "Rodzic",
            userType = 1,
        ).toDomain()

        assertEquals("mailbox-key", mailbox.globalKey)
        assertEquals("Rodzic", mailbox.name)
        assertEquals(1, mailbox.userType)
    }

    @Test
    fun mapsMessageDetailsFromConfirmedApiContract() {
        val details = MessageDetailsDto(
            date = "2026-09-14T18:32:00+02:00",
            apiGlobalKey = "api-key-123",
            sender = "Anna Kowalska",
            senderType = 1,
            recipients = listOf("Jan Nowak", "Anna Nowak"),
            subject = "Zebranie",
            content = "<p>Treść wiadomości</p>",
            read = true,
            attachments = listOf(
                MessageAttachmentDto(
                    url = "https://files.test/a.pdf",
                    fileName = "a.pdf",
                ),
            ),
            senderInfo = "",
            withdrawn = false,
            withdrawnAt = null,
            id = 123,
        ).toDomain()

        assertEquals(123, details.id)
        assertEquals("api-key-123", details.apiGlobalKey)
        assertEquals("Anna Kowalska", details.sender)
        assertEquals(1, details.senderType)
        assertEquals(listOf("Jan Nowak", "Anna Nowak"), details.recipients)
        assertEquals("Zebranie", details.subject)
        assertEquals("<p>Treść wiadomości</p>", details.content)
        assertEquals(true, details.read)
        assertEquals("https://files.test/a.pdf", details.attachments.single().url)
        assertEquals("a.pdf", details.attachments.single().fileName)
        assertEquals(null, details.senderInfo)
        assertEquals(false, details.withdrawn)
        assertEquals(null, details.withdrawnAt)
        assertEquals(LocalDateTime.of(2026, 9, 14, 18, 32), details.date)
    }

    private fun messageDto(
        id: Int = 123,
        correspondents: String = "Anna Kowalska",
        correspondentsDetails: String? = null,
        subject: String = "Zebranie",
        date: String = "2026-09-14T18:32:00+02:00",
        read: Boolean = false,
        readBy: String? = null,
    ): ReceivedMessageDto =
        ReceivedMessageDto(
            apiGlobalKey = "api-key-$id",
            correspondents = correspondents,
            correspondentsDetails = correspondentsDetails,
            subject = subject,
            date = date,
            mailbox = "Rodzic",
            hasAttachments = false,
            read = read,
            readBy = readBy,
            important = false,
            userRole = 1,
            withdrawn = false,
            replied = false,
            forwarded = false,
            id = id,
        )
}
