package pl.usagi.edziennik.mapper

import pl.usagi.edziennik.domain.Mailbox
import pl.usagi.edziennik.domain.MessageAttachment
import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.time.parseApiLocalDateTime
import pl.usagi.edziennik.dto.MailboxDto
import pl.usagi.edziennik.dto.MessageAttachmentDto
import pl.usagi.edziennik.dto.MessageDetailsDto
import pl.usagi.edziennik.dto.ReceivedMessageDto
import pl.usagi.edziennik.util.blankToNull

fun ReceivedMessageDto.toDomain(): ReceivedMessage =
    ReceivedMessage(
        id = id,
        apiGlobalKey = apiGlobalKey,
        sender = correspondents,
        subject = subject,
        date = parseApiLocalDateTime(date),
        isUnread = !read,
        hasAttachments = hasAttachments,
    )

fun MessageDetailsDto.toDomain(): MessageDetails =
    MessageDetails(
        id = id,
        apiGlobalKey = apiGlobalKey,
        sender = sender,
        senderType = senderType,
        recipients = recipients,
        subject = subject,
        content = content,
        date = parseApiLocalDateTime(date),
        read = read,
        attachments = attachments.map { it.toDomain() },
        senderInfo = senderInfo.blankToNull(),
        withdrawn = withdrawn,
        withdrawnAt = withdrawnAt?.takeUnless { it.isBlank() }?.let(::parseApiLocalDateTime),
    )

fun MessageAttachmentDto.toDomain(): MessageAttachment =
    MessageAttachment(
        url = url,
        fileName = fileName,
    )

fun MailboxDto.toDomain(): Mailbox =
    Mailbox(
        globalKey = globalKey,
        name = name,
        userType = userType,
    )
