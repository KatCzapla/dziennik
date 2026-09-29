package pl.usagi.edziennik.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.domain.MessageAttachment
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.repository.MessagesRepository
import javax.inject.Inject
import java.io.File

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagesRepository: MessagesRepository,
    private val activeStudentManager: ActiveStudentManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()
    private val loadingDetailsIds = mutableSetOf<MailboxMessageKey>()
    private val markReadAttemptedIds = mutableSetOf<Int>()
    private val markReadInFlightIds = mutableSetOf<Int>()

    init {
        observeStudents()
        observeActiveStudent()
        loadInitial(MessageMailboxType.Received)
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
    }

    fun selectMailbox(mailboxType: MessageMailboxType) {
        _uiState.update { state ->
            state.copy(selectedMailbox = mailboxType)
        }
        val mailboxState = _uiState.value.mailboxes.getValue(mailboxType)
        if (mailboxState.messages.isEmpty() && mailboxState.errorMessage == null && !mailboxState.isLoading) {
            loadInitial(mailboxType)
        }
    }

    fun retryInitialLoad() {
        loadInitial(_uiState.value.selectedMailbox, force = true)
    }

    fun loadNextPage() {
        loadNextPage(_uiState.value.selectedMailbox)
    }

    fun retryNextPage() {
        loadNextPage()
    }

    fun openMessage(messageId: Int) {
        val state = _uiState.value
        val index = state.messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            selectMessageIndex(index)
        }
    }

    fun closeMessageDetails() {
        val mailboxType = _uiState.value.selectedMailbox
        updateMailbox(mailboxType) { mailboxState ->
            mailboxState.copy(selectedMessageIndex = null)
        }
    }

    fun selectMessageIndex(index: Int) {
        val state = _uiState.value
        val mailboxType = state.selectedMailbox
        val mailboxState = state.currentMailbox
        if (index !in mailboxState.messages.indices) return

        val message = mailboxState.messages[index]
        updateMailbox(mailboxType) { it.copy(selectedMessageIndex = index) }
        loadDetailsIfNeeded(mailboxType, message)
        markAsReadIfNeeded(mailboxType, message)

        if (index >= mailboxState.messages.lastIndex - LOAD_MORE_THRESHOLD && mailboxState.canLoadMore) {
            loadNextPage(mailboxType)
        }
    }

    fun retryMessageDetails(messageId: Int) {
        val mailboxType = _uiState.value.selectedMailbox
        val message = _uiState.value.currentMailbox.messages.firstOrNull { it.id == messageId } ?: return
        updateMailbox(mailboxType) { mailboxState ->
            mailboxState.copy(messageDetails = mailboxState.messageDetails - messageId)
        }
        loadDetailsIfNeeded(mailboxType, message)
    }

    fun downloadAttachment(
        attachment: MessageAttachment,
        destination: File,
        onResult: (Result<String?>) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                onResult(Result.success(messagesRepository.downloadAttachment(attachment.url, destination)))
            } catch (exception: CancellationException) {
                destination.delete()
                throw exception
            } catch (exception: Exception) {
                destination.delete()
                onResult(Result.failure(exception))
            }
        }
    }

    private fun loadInitial(
        mailboxType: MessageMailboxType,
        force: Boolean = false,
    ) {
        val mailboxState = _uiState.value.mailboxes.getValue(mailboxType)
        if (mailboxState.isLoading || (!force && mailboxState.messages.isNotEmpty())) return

        viewModelScope.launch {
            updateMailbox(mailboxType) {
                it.copy(
                    messages = if (force) emptyList() else it.messages,
                    selectedMessageIndex = if (force) null else it.selectedMessageIndex,
                    messageDetails = if (force) emptyMap() else it.messageDetails,
                    isLoading = true,
                    isLoadingMore = false,
                    endReached = false,
                    errorMessage = null,
                    nextPageErrorMessage = null,
                )
            }
            runCatching {
                messagesRepository.getMessagesPage(mailboxType = mailboxType)
            }.onSuccess { page ->
                updateMailbox(mailboxType) {
                    it.copy(
                        messages = page.messages.distinctBy { message -> message.id },
                        isLoading = false,
                        endReached = page.endReached,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                updateMailbox(mailboxType) {
                    it.copy(
                        messages = emptyList(),
                        selectedMessageIndex = null,
                        messageDetails = emptyMap(),
                        isLoading = false,
                        endReached = false,
                        errorMessage = error.toMessagesErrorMessage(),
                    )
                }
            }
        }
    }

    private fun loadNextPage(mailboxType: MessageMailboxType) {
        val mailboxState = _uiState.value.mailboxes.getValue(mailboxType)
        if (!mailboxState.canLoadMore || mailboxState.messages.isEmpty()) return
        val lastMessageId = mailboxState.messages.last().id
        updateMailbox(mailboxType) {
            it.copy(isLoadingMore = true, nextPageErrorMessage = null)
        }

        viewModelScope.launch {
            runCatching {
                messagesRepository.getMessagesPage(
                    mailboxType = mailboxType,
                    lastMessageId = lastMessageId,
                )
            }.onSuccess { page ->
                updateMailbox(mailboxType) { state ->
                    val merged = state.messages.appendWithoutDuplicateIds(page.messages)
                    state.copy(
                        messages = merged,
                        selectedMessageIndex = state.selectedMessageIndex?.coerceAtMost(merged.lastIndex),
                        isLoadingMore = false,
                        endReached = page.endReached || merged.size == state.messages.size,
                        nextPageErrorMessage = null,
                    )
                }
            }.onFailure { error ->
                updateMailbox(mailboxType) {
                    it.copy(
                        isLoadingMore = false,
                        nextPageErrorMessage = error.toMessagesErrorMessage(),
                    )
                }
            }
        }
    }

    private fun observeStudents() {
        viewModelScope.launch {
            activeStudentManager.students.collect { students ->
                _uiState.update { state -> state.copy(students = students) }
            }
        }
    }

    private fun observeActiveStudent() {
        viewModelScope.launch {
            activeStudentManager.activeStudent.collect { student ->
                _uiState.update { state -> state.copy(activeStudent = student) }
            }
        }
    }

    private fun loadDetailsIfNeeded(
        mailboxType: MessageMailboxType,
        message: ReceivedMessage,
    ) {
        val messageId = message.id
        val key = MailboxMessageKey(mailboxType, messageId)
        val pageState = _uiState.value.mailboxes.getValue(mailboxType).messageDetails[messageId]
        if (pageState is MessageDetailsPageState.Loaded || pageState is MessageDetailsPageState.Loading) return
        if (!loadingDetailsIds.add(key)) return

        updateMailbox(mailboxType) {
            it.copy(messageDetails = it.messageDetails + (messageId to MessageDetailsPageState.Loading))
        }

        viewModelScope.launch {
            runCatching {
                messagesRepository.getMessageDetails(message.apiGlobalKey)
            }.onSuccess { details ->
                updateMailbox(mailboxType) {
                    it.copy(messageDetails = it.messageDetails + (messageId to MessageDetailsPageState.Loaded(details)))
                }
            }.onFailure { error ->
                updateMailbox(mailboxType) {
                    it.copy(
                        messageDetails = it.messageDetails + (
                            messageId to MessageDetailsPageState.Error(error.toMessagesErrorMessage())
                            ),
                    )
                }
            }
            loadingDetailsIds.remove(key)
        }
    }

    private fun markAsReadIfNeeded(
        mailboxType: MessageMailboxType,
        message: ReceivedMessage,
    ) {
        if (mailboxType != MessageMailboxType.Received) return
        if (!message.isUnread) return
        if (!markReadAttemptedIds.add(message.id)) return
        if (!markReadInFlightIds.add(message.id)) return

        viewModelScope.launch {
            runCatching {
                messagesRepository.markMessageAsRead(message.apiGlobalKey)
            }.onSuccess {
                updateMailbox(MessageMailboxType.Received) {
                    it.copy(
                        messages = it.messages.map { current ->
                            if (current.id == message.id) current.copy(isUnread = false) else current
                        },
                    )
                }
            }.onFailure {
                markReadAttemptedIds.remove(message.id)
            }
            markReadInFlightIds.remove(message.id)
        }
    }

    private fun updateMailbox(
        mailboxType: MessageMailboxType,
        transform: (MessagesMailboxUiState) -> MessagesMailboxUiState,
    ) {
        _uiState.update { state ->
            state.copy(
                mailboxes = state.mailboxes + (mailboxType to transform(state.mailboxes.getValue(mailboxType))),
            )
        }
    }

    private fun List<ReceivedMessage>.appendWithoutDuplicateIds(
        nextPage: List<ReceivedMessage>,
    ): List<ReceivedMessage> {
        val knownIds = mapTo(mutableSetOf()) { it.id }
        return this + nextPage.filter { knownIds.add(it.id) }
    }

    private data class MailboxMessageKey(
        val mailboxType: MessageMailboxType,
        val messageId: Int,
    )

    private companion object {
        const val LOAD_MORE_THRESHOLD = 5
    }
}
