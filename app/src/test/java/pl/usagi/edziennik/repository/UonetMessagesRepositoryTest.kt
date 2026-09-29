package pl.usagi.edziennik.repository

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.dto.MailboxDto
import pl.usagi.edziennik.dto.MessageDetailsDto
import pl.usagi.edziennik.dto.ReceivedMessageDto
import pl.usagi.edziennik.dto.UnreadMessageCountDto
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.network.messages.MessagesRemoteDataSource

class UonetMessagesRepositoryTest {
    @Test
    fun sumsUnreadCountsWithoutLosingZero() = runTest {
        val repository = UonetMessagesRepository(
            FakeMessagesRemoteDataSource(
                unreadCounts = listOf(
                    UnreadMessageCountDto("first", 0),
                    UnreadMessageCountDto("second", 4),
                ),
            ),
        )

        assertEquals(4, repository.getUnreadCount())
    }

    @Test
    fun zeroUnreadCountRemainsZero() = runTest {
        val repository = UonetMessagesRepository(
            FakeMessagesRemoteDataSource(
                unreadCounts = listOf(UnreadMessageCountDto("mailbox", 0)),
            ),
        )

        assertEquals(0, repository.getUnreadCount())
    }

    @Test
    fun receivedPageKeepsApiOrderAndDetectsEndByShortPage() = runTest {
        val repository = UonetMessagesRepository(
            FakeMessagesRemoteDataSource(
                received = listOf(
                    messageDto(id = 3, subject = "Trzecia"),
                    messageDto(id = 2, subject = "Druga"),
                ),
            ),
        )

        val page = repository.getReceivedMessagesPage(lastMessageId = 10, pageSize = 50)

        assertEquals(listOf(3, 2), page.messages.map { it.id })
        assertEquals(true, page.endReached)
    }

    @Test
    fun fullReceivedPageDoesNotMarkEndReached() = runTest {
        val repository = UonetMessagesRepository(
            FakeMessagesRemoteDataSource(
                received = listOf(messageDto(id = 1), messageDto(id = 2)),
            ),
        )

        val page = repository.getReceivedMessagesPage(lastMessageId = 0, pageSize = 2)

        assertEquals(false, page.endReached)
    }

    @Test
    fun messagesPagePassesMailboxTypeToRemoteDataSource() = runTest {
        val dataSource = FakeMessagesRemoteDataSource(
            received = listOf(messageDto(id = 8)),
        )
        val repository = UonetMessagesRepository(dataSource)

        val page = repository.getMessagesPage(
            mailboxType = MessageMailboxType.Sent,
            lastMessageId = 42,
            pageSize = 50,
        )

        assertEquals(listOf(MessageMailboxType.Sent), dataSource.mailboxRequests.map { it.mailboxType })
        assertEquals(listOf(8), page.messages.map { it.id })
    }


    @Test
    fun messageDetailsAreMappedFromRemoteDataSource() = runTest {
        val repository = UonetMessagesRepository(
            FakeMessagesRemoteDataSource(details = messageDetailsDto()),
        )

        val details = repository.getMessageDetails("api-key-123")

        assertEquals(123, details.id)
        assertEquals("Treść wiadomości", details.content)
        assertEquals("Anna Kowalska", details.sender)
    }

    @Test
    fun successfulMarkReadInvalidatesUnreadCount() = runTest {
        val repository = UonetMessagesRepository(FakeMessagesRemoteDataSource())
        var invalidations = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.unreadCountInvalidations.first()
            invalidations += 1
        }

        repository.markMessageAsRead("api-key-123")
        job.join()

        assertEquals(1, invalidations)
    }

    private class FakeMessagesRemoteDataSource(
        private val unreadCounts: List<UnreadMessageCountDto> = emptyList(),
        private val received: List<ReceivedMessageDto> = emptyList(),
        private val details: MessageDetailsDto = messageDetailsDto(),
    ) : MessagesRemoteDataSource {
        val mailboxRequests = mutableListOf<MailboxRequest>()

        override suspend fun confirmSession() = Unit

        override suspend fun getUnreadCounts(): List<UnreadMessageCountDto> = unreadCounts

        override suspend fun getMailboxes(): List<MailboxDto> = emptyList()

        override suspend fun getReceivedMessages(
            lastMessageId: Int,
            pageSize: Int,
        ): List<ReceivedMessageDto> =
            getMessages(
                mailboxType = MessageMailboxType.Received,
                lastMessageId = lastMessageId,
                pageSize = pageSize,
            )

        override suspend fun getMessages(
            mailboxType: MessageMailboxType,
            lastMessageId: Int,
            pageSize: Int,
        ): List<ReceivedMessageDto> {
            mailboxRequests += MailboxRequest(mailboxType, lastMessageId, pageSize)
            return received
        }

        override suspend fun getMessageDetails(apiGlobalKey: String): MessageDetailsDto = details

        override suspend fun downloadAttachment(url: String, destination: java.io.File): String? = error("not used")

        override suspend fun markMessageAsRead(apiGlobalKey: String) = Unit
    }

    private data class MailboxRequest(
        val mailboxType: MessageMailboxType,
        val lastMessageId: Int,
        val pageSize: Int,
    )

    private companion object {
        fun messageDto(
            id: Int,
            subject: String = "Temat",
        ): ReceivedMessageDto =
            ReceivedMessageDto(
                apiGlobalKey = "api-key-$id",
                correspondents = "Anna Kowalska",
                correspondentsDetails = null,
                subject = subject,
                date = "2026-09-14T18:32:00+02:00",
                mailbox = "Rodzic",
                hasAttachments = false,
                read = false,
                readBy = null,
                important = false,
                userRole = 1,
                withdrawn = false,
                replied = false,
                forwarded = false,
                id = id,
            )

        fun messageDetailsDto(): MessageDetailsDto =
            MessageDetailsDto(
                date = "2026-09-14T18:32:00+02:00",
                apiGlobalKey = "api-key-123",
                sender = "Anna Kowalska",
                senderType = 1,
                recipients = listOf("Jan Nowak"),
                subject = "Zebranie",
                content = "Treść wiadomości",
                read = false,
                attachments = emptyList(),
                senderInfo = null,
                withdrawn = false,
                withdrawnAt = null,
                id = 123,
            )
    }
}
