package pl.usagi.edziennik.domain

import java.time.LocalDateTime

enum class MessageMailboxType {
    Received,
    Sent,
    Deleted,
    Drafts,
}

data class ReceivedMessage(
    val id: Int,
    val apiGlobalKey: String,
    val sender: String,
    val subject: String,
    val date: LocalDateTime,
    val isUnread: Boolean,
    val hasAttachments: Boolean,
)

data class MessageDetails(
    val id: Int,
    val apiGlobalKey: String,
    val sender: String,
    val senderType: Int,
    val recipients: List<String>,
    val subject: String,
    val content: String,
    val date: LocalDateTime,
    val read: Boolean,
    val attachments: List<MessageAttachment>,
    val senderInfo: String?,
    val withdrawn: Boolean,
    val withdrawnAt: LocalDateTime?,
)

data class MessageAttachment(
    val url: String,
    val fileName: String,
)

data class Mailbox(
    val globalKey: String,
    val name: String,
    val userType: Int,
)
