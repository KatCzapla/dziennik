package pl.usagi.edziennik.network.messages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pl.usagi.edziennik.dto.MailboxDto
import pl.usagi.edziennik.dto.MessageDetailsDto
import pl.usagi.edziennik.dto.MessageKeyRequestDto
import pl.usagi.edziennik.dto.ReceivedMessageDto
import pl.usagi.edziennik.dto.UnreadMessageCountDto
import pl.usagi.edziennik.domain.MessageMailboxType
import java.io.IOException
import javax.inject.Inject

interface MessagesApiClient {
    suspend fun getUnreadCounts(context: MessagesSessionContext): List<UnreadMessageCountDto>

    suspend fun getMailboxes(context: MessagesSessionContext): List<MailboxDto>

    suspend fun getReceivedMessages(
        context: MessagesSessionContext,
        lastMessageId: Int,
        pageSize: Int,
    ): List<ReceivedMessageDto> =
        getMessages(
            context = context,
            mailboxType = MessageMailboxType.Received,
            lastMessageId = lastMessageId,
            pageSize = pageSize,
        )

    suspend fun getMessages(
        context: MessagesSessionContext,
        mailboxType: MessageMailboxType,
        lastMessageId: Int,
        pageSize: Int,
    ): List<ReceivedMessageDto>

    suspend fun getMessageDetails(
        context: MessagesSessionContext,
        apiGlobalKey: String,
    ): MessageDetailsDto

    suspend fun markMessageAsRead(
        context: MessagesSessionContext,
        apiGlobalKey: String,
    )
}

class OkHttpMessagesApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : MessagesApiClient {
    override suspend fun getUnreadCounts(
        context: MessagesSessionContext,
    ): List<UnreadMessageCountDto> = withContext(Dispatchers.IO) {
        MessagesDiagnostics.debug("Messages API loading unread count")
        executeJsonListRequest(context, "LiczbyNieodczytanych", ListSerializer(UnreadMessageCountDto.serializer()))
    }

    override suspend fun getMailboxes(
        context: MessagesSessionContext,
    ): List<MailboxDto> = withContext(Dispatchers.IO) {
        MessagesDiagnostics.debug("Messages API loading mailboxes")
        executeJsonListRequest(context, "Skrzynki", ListSerializer(MailboxDto.serializer()))
    }

    override suspend fun getReceivedMessages(
        context: MessagesSessionContext,
        lastMessageId: Int,
        pageSize: Int,
    ): List<ReceivedMessageDto> =
        getMessages(
            context = context,
            mailboxType = MessageMailboxType.Received,
            lastMessageId = lastMessageId,
            pageSize = pageSize,
        )

    override suspend fun getMessages(
        context: MessagesSessionContext,
        mailboxType: MessageMailboxType,
        lastMessageId: Int,
        pageSize: Int,
    ): List<ReceivedMessageDto> = withContext(Dispatchers.IO) {
        MessagesDiagnostics.debug("Messages API loading mailbox page: mailbox=${mailboxType.safeLogName()}")
        val url = context.apiBaseUrl.newBuilder()
            .addPathSegment(mailboxType.apiEndpoint())
            .addQueryParameter("idLastWiadomosc", lastMessageId.toString())
            .addQueryParameter("pageSize", pageSize.toString())
            .build()

        val request = baseRequest(context)
            .url(url)
            .get()
            .build()

        executeJsonRequest(request, ListSerializer(ReceivedMessageDto.serializer()))
    }

    override suspend fun getMessageDetails(
        context: MessagesSessionContext,
        apiGlobalKey: String,
    ): MessageDetailsDto = withContext(Dispatchers.IO) {
        MessagesDiagnostics.debug("Messages API loading message details")
        val url = context.apiBaseUrl.newBuilder()
            .addPathSegment("WiadomoscSzczegoly")
            .addQueryParameter("apiGlobalKey", apiGlobalKey)
            .build()

        val request = baseRequest(context)
            .url(url)
            .get()
            .build()

        executeJsonRequest(request, MessageDetailsDto.serializer())
    }

    override suspend fun markMessageAsRead(
        context: MessagesSessionContext,
        apiGlobalKey: String,
    ): Unit = withContext(Dispatchers.IO) {
        MessagesDiagnostics.debug("Messages API marking message as read")
        val url = context.apiBaseUrl.newBuilder()
            .addPathSegment("WiadomoscSzczegoly")
            .build()
        val requestBody = json.encodeToString(
            MessageKeyRequestDto.serializer(),
            MessageKeyRequestDto(apiGlobalKey = apiGlobalKey),
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = baseRequest(context)
            .url(url)
            .put(requestBody)
            .build()

        executeEmptyRequest(request)
    }

    private fun <T> executeJsonListRequest(
        context: MessagesSessionContext,
        endpoint: String,
        serializer: kotlinx.serialization.DeserializationStrategy<List<T>>,
    ): List<T> {
        val url = context.apiBaseUrl.newBuilder()
            .addPathSegment(endpoint)
            .build()

        val request = baseRequest(context)
            .url(url)
            .get()
            .build()

        return executeJsonRequest(request, serializer)
    }

    private fun <T> executeJsonRequest(
        request: Request,
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T =
        try {
            okHttpClient.newCall(request).execute().use { response ->
                MessagesDiagnostics.debug("Messages API response: ${response.safeSummary()}")
                if (!response.isSuccessful) {
                    MessagesDiagnostics.warn("Messages API request failed: status=${response.code}, host=${response.request.url.host}, path=${response.request.url.encodedPath}")
                    throw MessagesSessionException.Http(response.code)
                }
                if (!response.header("Content-Type").orEmpty().contains("application/json", ignoreCase = true)) {
                    MessagesDiagnostics.warn("Messages API unexpected content type: host=${response.request.url.host}, path=${response.request.url.encodedPath}, status=${response.code}")
                    throw MessagesSessionException.UnexpectedResponse()
                }

                json.decodeFromString(serializer, response.body.string())
            }
        } catch (exception: IOException) {
            MessagesDiagnostics.warn("Messages API network failed: ${exception::class.simpleName}")
            throw MessagesSessionException.Network(exception)
        } catch (exception: SerializationException) {
            MessagesDiagnostics.warn("Messages API serialization failed: ${exception::class.simpleName}")
            throw MessagesSessionException.UnexpectedResponse(exception)
        } catch (exception: IllegalArgumentException) {
            MessagesDiagnostics.warn("Messages API contract failed: ${exception::class.simpleName}")
            throw MessagesSessionException.UnexpectedResponse(exception)
        }

    private fun executeEmptyRequest(request: Request) {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                MessagesDiagnostics.debug("Messages API response: ${response.safeSummary()}")
                if (!response.isSuccessful) {
                    MessagesDiagnostics.warn("Messages API request failed: status=${response.code}, host=${response.request.url.host}, path=${response.request.url.encodedPath}")
                    throw MessagesSessionException.Http(response.code)
                }
            }
        } catch (exception: IOException) {
            MessagesDiagnostics.warn("Messages API network failed: ${exception::class.simpleName}")
            throw MessagesSessionException.Network(exception)
        }
    }

    private fun baseRequest(context: MessagesSessionContext): Request.Builder =
        Request.Builder()
            .header("Accept", ACCEPT_JSON)
            .header("Referer", context.appUrl.toString())
            .header("X-V-AppGuid", context.appGuid)
            .header("X-V-RequestVerificationToken", context.requestVerificationToken)

    private companion object {
        const val ACCEPT_JSON = "application/json, text/plain, */*"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun MessageMailboxType.apiEndpoint(): String =
    when (this) {
        MessageMailboxType.Received -> "Odebrane"
        MessageMailboxType.Sent -> "Wyslane"
        MessageMailboxType.Deleted -> "Usuniete"
        MessageMailboxType.Drafts -> "Kopie"
    }

private fun MessageMailboxType.safeLogName(): String =
    when (this) {
        MessageMailboxType.Received -> "RECEIVED"
        MessageMailboxType.Sent -> "SENT"
        MessageMailboxType.Deleted -> "DELETED"
        MessageMailboxType.Drafts -> "DRAFTS"
    }
