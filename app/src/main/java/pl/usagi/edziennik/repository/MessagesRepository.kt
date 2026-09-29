package pl.usagi.edziennik.repository

import kotlinx.coroutines.flow.Flow
import pl.usagi.edziennik.domain.Mailbox
import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.domain.ReceivedMessage
import java.io.File

interface MessagesRepository {
    val unreadCountInvalidations: Flow<Unit>

    suspend fun getMailboxes(): List<Mailbox>

    suspend fun getUnreadCount(): Int

    suspend fun getReceivedMessagesPage(
        lastMessageId: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): ReceivedMessagesPage =
        getMessagesPage(
            mailboxType = MessageMailboxType.Received,
            lastMessageId = lastMessageId,
            pageSize = pageSize,
        )

    suspend fun getMessagesPage(
        mailboxType: MessageMailboxType,
        lastMessageId: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): ReceivedMessagesPage

    suspend fun getMessageDetails(apiGlobalKey: String): MessageDetails

    suspend fun downloadAttachment(url: String, destination: File): String?

    suspend fun markMessageAsRead(apiGlobalKey: String)

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

data class ReceivedMessagesPage(
    val messages: List<ReceivedMessage>,
    val endReached: Boolean,
)
