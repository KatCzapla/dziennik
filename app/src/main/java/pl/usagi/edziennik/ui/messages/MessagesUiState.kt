package pl.usagi.edziennik.ui.messages

import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.Student

data class MessagesUiState(
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val selectedMailbox: MessageMailboxType = MessageMailboxType.Received,
    val mailboxes: Map<MessageMailboxType, MessagesMailboxUiState> = MessageMailboxType.entries.associateWith {
        MessagesMailboxUiState()
    },
) {
    val currentMailbox: MessagesMailboxUiState
        get() = mailboxes.getValue(selectedMailbox)

    val messages: List<ReceivedMessage>
        get() = currentMailbox.messages

    val isLoading: Boolean
        get() = currentMailbox.isLoading

    val isLoadingMore: Boolean
        get() = currentMailbox.isLoadingMore

    val endReached: Boolean
        get() = currentMailbox.endReached

    val errorMessage: String?
        get() = currentMailbox.errorMessage

    val nextPageErrorMessage: String?
        get() = currentMailbox.nextPageErrorMessage

    val selectedMessageIndex: Int?
        get() = currentMailbox.selectedMessageIndex

    val messageDetails: Map<Int, MessageDetailsPageState>
        get() = currentMailbox.messageDetails

    val canLoadMore: Boolean
        get() = currentMailbox.canLoadMore

    val selectedMessage: ReceivedMessage?
        get() = selectedMessageIndex?.let(messages::getOrNull)

    val isShowingDetails: Boolean
        get() = selectedMessageIndex != null
}

data class MessagesMailboxUiState(
    val messages: List<ReceivedMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val nextPageErrorMessage: String? = null,
    val selectedMessageIndex: Int? = null,
    val messageDetails: Map<Int, MessageDetailsPageState> = emptyMap(),
) {
    val canLoadMore: Boolean
        get() = !isLoading && !isLoadingMore && !endReached && errorMessage == null
}

sealed interface MessageDetailsPageState {
    data object Loading : MessageDetailsPageState

    data class Loaded(
        val details: MessageDetails,
    ) : MessageDetailsPageState

    data class Error(
        val message: String,
    ) : MessageDetailsPageState
}
