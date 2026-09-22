package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnreadMessageCountDto(
    @SerialName("globalKey")
    val globalKey: String,
    @SerialName("liczbaWiadomosci")
    val count: Int,
)

@Serializable
data class MailboxDto(
    @SerialName("globalKey")
    val globalKey: String,
    @SerialName("nazwa")
    val name: String,
    @SerialName("typUzytkownika")
    val userType: Int,
)

@Serializable
data class ReceivedMessageDto(
    @SerialName("apiGlobalKey")
    val apiGlobalKey: String,
    @SerialName("korespondenci")
    val correspondents: String,
    @SerialName("korespondenciSzczegoly")
    val correspondentsDetails: String? = null,
    @SerialName("temat")
    val subject: String,
    @SerialName("data")
    val date: String,
    @SerialName("skrzynka")
    val mailbox: String,
    @SerialName("hasZalaczniki")
    val hasAttachments: Boolean,
    @SerialName("przeczytana")
    val read: Boolean,
    @SerialName("nieprzeczytanePrzeczytanePrzez")
    val readBy: String? = null,
    @SerialName("wazna")
    val important: Boolean,
    @SerialName("uzytkownikRola")
    val userRole: Int,
    @SerialName("wycofana")
    val withdrawn: Boolean,
    @SerialName("odpowiedziana")
    val replied: Boolean,
    @SerialName("przekazana")
    val forwarded: Boolean,
    @SerialName("id")
    val id: Int,
)

@Serializable
data class MessageDetailsDto(
    @SerialName("data")
    val date: String,
    @SerialName("apiGlobalKey")
    val apiGlobalKey: String,
    @SerialName("nadawca")
    val sender: String,
    @SerialName("nadawcaTyp")
    val senderType: Int,
    @SerialName("odbiorcy")
    val recipients: List<String> = emptyList(),
    @SerialName("temat")
    val subject: String,
    @SerialName("tresc")
    val content: String,
    @SerialName("odczytana")
    val read: Boolean,
    @SerialName("zalaczniki")
    val attachments: List<MessageAttachmentDto> = emptyList(),
    @SerialName("nadawcaInfo")
    val senderInfo: String? = null,
    @SerialName("wycofana")
    val withdrawn: Boolean,
    @SerialName("dataWycofania")
    val withdrawnAt: String? = null,
    @SerialName("id")
    val id: Int,
)

@Serializable
data class MessageAttachmentDto(
    @SerialName("url")
    val url: String,
    @SerialName("nazwaPliku")
    val fileName: String,
)

@Serializable
data class MessageKeyRequestDto(
    @SerialName("apiGlobalKey")
    val apiGlobalKey: String,
)
