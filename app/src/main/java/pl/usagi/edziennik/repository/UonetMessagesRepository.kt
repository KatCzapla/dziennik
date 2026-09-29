package pl.usagi.edziennik.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pl.usagi.edziennik.domain.Mailbox
import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.network.messages.MessagesRemoteDataSource
import pl.usagi.edziennik.network.messages.MessagesSessionException
import java.time.format.DateTimeParseException
import java.io.File
import javax.inject.Inject

class UonetMessagesRepository @Inject constructor(
    private val dataSource: MessagesRemoteDataSource,
) : MessagesRepository {
    private val _unreadCountInvalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val unreadCountInvalidations: Flow<Unit> = _unreadCountInvalidations.asSharedFlow()

    override suspend fun getMailboxes(): List<Mailbox> =
        runCatchingMessages {
            dataSource.getMailboxes().map { it.toDomain() }
        }

    override suspend fun getUnreadCount(): Int =
        runCatchingMessages {
            dataSource.getUnreadCounts().sumOf { it.count }
        }

    override suspend fun getReceivedMessagesPage(
        lastMessageId: Int,
        pageSize: Int,
    ): ReceivedMessagesPage =
        getMessagesPage(
            mailboxType = MessageMailboxType.Received,
            lastMessageId = lastMessageId,
            pageSize = pageSize,
        )

    override suspend fun getMessagesPage(
        mailboxType: MessageMailboxType,
        lastMessageId: Int,
        pageSize: Int,
    ): ReceivedMessagesPage =
        runCatchingMessages {
            val messages = dataSource.getMessages(
                mailboxType = mailboxType,
                lastMessageId = lastMessageId,
                pageSize = pageSize,
            ).map { it.toDomain() }

            ReceivedMessagesPage(
                messages = messages,
                endReached = messages.size < pageSize,
            )
        }

    override suspend fun getMessageDetails(apiGlobalKey: String): MessageDetails =
        runCatchingMessages {
            dataSource.getMessageDetails(apiGlobalKey).toDomain()
        }

    override suspend fun downloadAttachment(url: String, destination: File): String? =
        runCatchingMessages {
            dataSource.downloadAttachment(url, destination)
        }

    override suspend fun markMessageAsRead(apiGlobalKey: String) {
        runCatchingMessages {
            dataSource.markMessageAsRead(apiGlobalKey)
        }
        _unreadCountInvalidations.tryEmit(Unit)
    }

    private inline fun <T> runCatchingMessages(block: () -> T): T =
        try {
            block()
        } catch (exception: MessagesSessionException) {
            throw MessagesRepositoryException.MessagesUnavailable(exception)
        } catch (exception: DateTimeParseException) {
            throw MessagesRepositoryException.UnexpectedResponse(exception)
        } catch (exception: IllegalArgumentException) {
            throw MessagesRepositoryException.UnexpectedResponse(exception)
        }
}

sealed class MessagesRepositoryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class MessagesUnavailable(cause: Throwable) : MessagesRepositoryException("Messages service is unavailable.", cause)

    class UnexpectedResponse(cause: Throwable? = null) :
        MessagesRepositoryException("Messages response did not match the expected contract.", cause)
}
