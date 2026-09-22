package pl.usagi.edziennik.network.messages

import pl.usagi.edziennik.domain.MessageMailboxType
import javax.inject.Inject

interface MessagesRemoteDataSource {
    suspend fun confirmSession()

    suspend fun getUnreadCounts(): List<pl.usagi.edziennik.dto.UnreadMessageCountDto>

    suspend fun getMailboxes(): List<pl.usagi.edziennik.dto.MailboxDto>

    suspend fun getReceivedMessages(
        lastMessageId: Int,
        pageSize: Int,
    ): List<pl.usagi.edziennik.dto.ReceivedMessageDto> =
        getMessages(
            mailboxType = MessageMailboxType.Received,
            lastMessageId = lastMessageId,
            pageSize = pageSize,
        )

    suspend fun getMessages(
        mailboxType: MessageMailboxType,
        lastMessageId: Int,
        pageSize: Int,
    ): List<pl.usagi.edziennik.dto.ReceivedMessageDto>

    suspend fun getMessageDetails(apiGlobalKey: String): pl.usagi.edziennik.dto.MessageDetailsDto

    suspend fun markMessageAsRead(apiGlobalKey: String)
}

class MessagesDataSource @Inject constructor(
    private val messagesSessionManager: MessagesSessionManager,
    private val messagesApiClient: MessagesApiClient,
) : MessagesRemoteDataSource {
    override suspend fun confirmSession() {
        messagesSessionManager.ensureMessagesSession()
    }

    override suspend fun getUnreadCounts(): List<pl.usagi.edziennik.dto.UnreadMessageCountDto> {
        val context = messagesSessionManager.ensureMessagesSession()
        return messagesApiClient.getUnreadCounts(context)
    }

    override suspend fun getMailboxes(): List<pl.usagi.edziennik.dto.MailboxDto> {
        val context = messagesSessionManager.ensureMessagesSession()
        return messagesApiClient.getMailboxes(context)
    }

    override suspend fun getReceivedMessages(
        lastMessageId: Int,
        pageSize: Int,
    ): List<pl.usagi.edziennik.dto.ReceivedMessageDto> =
        getMessages(
            mailboxType = MessageMailboxType.Received,
            lastMessageId = lastMessageId,
            pageSize = pageSize,
        )

    override suspend fun getMessages(
        mailboxType: MessageMailboxType,
        lastMessageId: Int,
        pageSize: Int,
    ): List<pl.usagi.edziennik.dto.ReceivedMessageDto> {
        val context = messagesSessionManager.ensureMessagesSession()
        return messagesApiClient.getMessages(
            context = context,
            mailboxType = mailboxType,
            lastMessageId = lastMessageId,
            pageSize = pageSize,
        )
    }

    override suspend fun getMessageDetails(apiGlobalKey: String): pl.usagi.edziennik.dto.MessageDetailsDto {
        val context = messagesSessionManager.ensureMessagesSession()
        return messagesApiClient.getMessageDetails(
            context = context,
            apiGlobalKey = apiGlobalKey,
        )
    }

    override suspend fun markMessageAsRead(apiGlobalKey: String) {
        val context = messagesSessionManager.ensureMessagesSession()
        messagesApiClient.markMessageAsRead(
            context = context,
            apiGlobalKey = apiGlobalKey,
        )
    }
}
