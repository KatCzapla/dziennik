package pl.usagi.edziennik.ui.messages

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Mailbox
import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.ReceivedMessagesPage
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadFetchesFirstPage() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(1)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())

        advanceUntilIdle()

        assertEquals(listOf(0), repository.receivedRequests.map { it.lastMessageId })
        assertEquals(listOf(1), viewModel.uiState.value.messages.map { it.id })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun nextPageIsAppendedAndOrderIsPreserved() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(
                0 to ReceivedMessagesPage(listOf(message(3), message(2)), endReached = false),
                2 to ReceivedMessagesPage(listOf(message(1)), endReached = true),
            ),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(3, 2, 1), viewModel.uiState.value.messages.map { it.id })
    }

    @Test
    fun duplicateMessageIdDoesNotCreateSecondItem() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(
                0 to ReceivedMessagesPage(listOf(message(2), message(1)), endReached = false),
                1 to ReceivedMessagesPage(listOf(message(1)), endReached = false),
            ),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(2, 1), viewModel.uiState.value.messages.map { it.id })
        assertTrue(viewModel.uiState.value.endReached)
    }

    @Test
    fun nextPageDoesNotStartMoreThanOnceInParallel() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(
                0 to ReceivedMessagesPage(listOf(message(2)), endReached = false),
                2 to ReceivedMessagesPage(listOf(message(1)), endReached = true),
            ),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        async { viewModel.loadNextPage() }
        async { viewModel.loadNextPage() }
        advanceUntilIdle()

        assertEquals(listOf(0, 2), repository.receivedRequests.map { it.lastMessageId })
    }

    @Test
    fun endReachedStopsNextRequests() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(1)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(0), repository.receivedRequests.map { it.lastMessageId })
    }

    @Test
    fun nextPageErrorKeepsAlreadyLoadedMessagesAndRetryWorks() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(
                0 to ReceivedMessagesPage(listOf(message(2)), endReached = false),
                2 to ReceivedMessagesPage(listOf(message(1)), endReached = true),
            ),
        )
        repository.failNextPageForLastId = 2
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(2), viewModel.uiState.value.messages.map { it.id })
        assertEquals("Nie udało się pobrać wiadomości.", viewModel.uiState.value.nextPageErrorMessage)

        repository.failNextPageForLastId = null
        viewModel.retryNextPage()
        advanceUntilIdle()

        assertEquals(listOf(2, 1), viewModel.uiState.value.messages.map { it.id })
        assertEquals(null, viewModel.uiState.value.nextPageErrorMessage)
    }

    @Test
    fun initialErrorCanBeRetried() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(1)), endReached = true)),
            failInitial = true,
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        assertEquals("Nie udało się pobrać wiadomości.", viewModel.uiState.value.errorMessage)

        repository.failInitial = false
        viewModel.retryInitialLoad()
        advanceUntilIdle()

        assertEquals(listOf(1), viewModel.uiState.value.messages.map { it.id })
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun emptyFirstPageGivesEmptyStateData() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(emptyList(), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        assertEquals(emptyList<ReceivedMessage>(), viewModel.uiState.value.messages)
        assertTrue(viewModel.uiState.value.endReached)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun openMessageSelectsClickedMessageAndLoadsItsDetails() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(3), message(2), message(1)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(2)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.selectedMessageIndex)
        assertEquals(listOf("api-key-2"), repository.detailRequests)
        assertTrue(viewModel.uiState.value.messageDetails.getValue(2) is MessageDetailsPageState.Loaded)
    }

    @Test
    fun selectedIndexesFollowCurrentListOrderForPager() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(3), message(2), message(1)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(3)
        viewModel.selectMessageIndex(2)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.selectedMessageIndex)
        assertEquals(1, viewModel.uiState.value.selectedMessage?.id)
        assertEquals(listOf("api-key-3", "api-key-1"), repository.detailRequests)
    }

    @Test
    fun detailsAreLoadedLazilyAndCached() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(2), message(1)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(2)
        advanceUntilIdle()
        viewModel.selectMessageIndex(1)
        advanceUntilIdle()
        viewModel.selectMessageIndex(0)
        advanceUntilIdle()

        assertEquals(listOf("api-key-2", "api-key-1"), repository.detailRequests)
    }

    @Test
    fun singleDetailsPageErrorDoesNotClearOtherDetailsPages() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(2), message(1)), endReached = true)),
        )
        repository.failDetailsForApiKey = "api-key-1"
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(2)
        advanceUntilIdle()
        viewModel.selectMessageIndex(1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.messageDetails.getValue(2) is MessageDetailsPageState.Loaded)
        assertTrue(viewModel.uiState.value.messageDetails.getValue(1) is MessageDetailsPageState.Error)
    }

    @Test
    fun openingUnreadMessageMarksOnlyCurrentMessageAsRead() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(2), message(4)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(2)
        advanceUntilIdle()

        assertEquals(listOf("api-key-2"), repository.markReadRequests)
        assertEquals(false, viewModel.uiState.value.messages.first { it.id == 2 }.isUnread)
        assertEquals(true, viewModel.uiState.value.messages.first { it.id == 4 }.isUnread)
    }

    @Test
    fun readMessageDoesNotCallMarkRead() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(1, isUnread = false)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(1)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.markReadRequests)
    }

    @Test
    fun selectingSameUnreadMessageDoesNotMarkReadMoreThanOnce() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(2)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(2)
        advanceUntilIdle()
        viewModel.selectMessageIndex(0)
        advanceUntilIdle()

        assertEquals(listOf("api-key-2"), repository.markReadRequests)
    }

    @Test
    fun markReadFailureKeepsUnreadState() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(2)), endReached = true)),
        )
        repository.failMarkReadForApiKey = "api-key-2"
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(2)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.messages.single().isUnread)
    }

    @Test
    fun detailsStateIsClosedWithoutReloadingList() = runTest {
        val repository = FakeMessagesRepository(
            pages = mutableMapOf(0 to ReceivedMessagesPage(listOf(message(2), message(1)), endReached = true)),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.openMessage(2)
        advanceUntilIdle()
        viewModel.closeMessageDetails()

        assertEquals(null, viewModel.uiState.value.selectedMessageIndex)
        assertEquals(listOf(0), repository.receivedRequests.map { it.lastMessageId })
        assertEquals(listOf(2, 1), viewModel.uiState.value.messages.map { it.id })
    }

    @Test
    fun switchingMailboxLoadsAndShowsOnlySelectedMailboxMessages() = runTest {
        val repository = FakeMessagesRepository(
            pagesByMailbox = mutableMapOf(
                MessageMailboxType.Received to mutableMapOf(0 to ReceivedMessagesPage(listOf(message(3)), endReached = true)),
                MessageMailboxType.Sent to mutableMapOf(0 to ReceivedMessagesPage(listOf(message(8, isUnread = false)), endReached = true)),
            ),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.selectMailbox(MessageMailboxType.Sent)
        advanceUntilIdle()

        assertEquals(MessageMailboxType.Sent, viewModel.uiState.value.selectedMailbox)
        assertEquals(listOf(8), viewModel.uiState.value.messages.map { it.id })
        assertEquals(listOf(MessageMailboxType.Received, MessageMailboxType.Sent), repository.receivedRequests.map { it.mailboxType })
    }

    @Test
    fun mailboxPaginationStateIsIndependent() = runTest {
        val repository = FakeMessagesRepository(
            pagesByMailbox = mutableMapOf(
                MessageMailboxType.Received to mutableMapOf(
                    0 to ReceivedMessagesPage(listOf(message(3), message(2)), endReached = false),
                    2 to ReceivedMessagesPage(listOf(message(1)), endReached = true),
                ),
                MessageMailboxType.Drafts to mutableMapOf(
                    0 to ReceivedMessagesPage(listOf(message(12), message(11)), endReached = false),
                    11 to ReceivedMessagesPage(listOf(message(10)), endReached = true),
                ),
            ),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()
        viewModel.selectMailbox(MessageMailboxType.Drafts)
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(12, 11, 10), viewModel.uiState.value.messages.map { it.id })
        viewModel.selectMailbox(MessageMailboxType.Received)
        advanceUntilIdle()
        assertEquals(listOf(3, 2, 1), viewModel.uiState.value.messages.map { it.id })
        assertEquals(
            listOf(
                MessageMailboxType.Received to 0,
                MessageMailboxType.Received to 2,
                MessageMailboxType.Drafts to 0,
                MessageMailboxType.Drafts to 11,
            ),
            repository.receivedRequests.map { it.mailboxType to it.lastMessageId },
        )
    }

    @Test
    fun detailsPagerUsesCurrentMailboxOrderOnly() = runTest {
        val repository = FakeMessagesRepository(
            pagesByMailbox = mutableMapOf(
                MessageMailboxType.Received to mutableMapOf(0 to ReceivedMessagesPage(listOf(message(3), message(2)), endReached = true)),
                MessageMailboxType.Deleted to mutableMapOf(0 to ReceivedMessagesPage(listOf(message(9), message(8)), endReached = true)),
            ),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.selectMailbox(MessageMailboxType.Deleted)
        advanceUntilIdle()
        viewModel.openMessage(8)
        advanceUntilIdle()

        assertEquals(MessageMailboxType.Deleted, viewModel.uiState.value.selectedMailbox)
        assertEquals(1, viewModel.uiState.value.selectedMessageIndex)
        assertEquals(8, viewModel.uiState.value.selectedMessage?.id)
        assertEquals(listOf("api-key-8"), repository.detailRequests)
    }

    @Test
    fun markReadIsNotAppliedOutsideReceivedMailbox() = runTest {
        val repository = FakeMessagesRepository(
            pagesByMailbox = mutableMapOf(
                MessageMailboxType.Received to mutableMapOf(0 to ReceivedMessagesPage(listOf(message(2)), endReached = true)),
                MessageMailboxType.Sent to mutableMapOf(0 to ReceivedMessagesPage(listOf(message(4)), endReached = true)),
            ),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.selectMailbox(MessageMailboxType.Sent)
        advanceUntilIdle()
        viewModel.openMessage(4)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.markReadRequests)
        assertEquals(true, viewModel.uiState.value.messages.single().isUnread)
    }

    @Test
    fun returningFromDetailsKeepsCurrentMailboxAndListState() = runTest {
        val repository = FakeMessagesRepository(
            pagesByMailbox = mutableMapOf(
                MessageMailboxType.Received to mutableMapOf(0 to ReceivedMessagesPage(listOf(message(1)), endReached = true)),
                MessageMailboxType.Sent to mutableMapOf(0 to ReceivedMessagesPage(listOf(message(7), message(6)), endReached = true)),
            ),
        )
        val viewModel = MessagesViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()

        viewModel.selectMailbox(MessageMailboxType.Sent)
        advanceUntilIdle()
        viewModel.openMessage(7)
        advanceUntilIdle()
        viewModel.closeMessageDetails()

        assertEquals(MessageMailboxType.Sent, viewModel.uiState.value.selectedMailbox)
        assertEquals(null, viewModel.uiState.value.selectedMessageIndex)
        assertEquals(listOf(7, 6), viewModel.uiState.value.messages.map { it.id })
    }

    private class FakeMessagesRepository(
        private val pages: MutableMap<Int, ReceivedMessagesPage> = mutableMapOf(),
        private val pagesByMailbox: MutableMap<MessageMailboxType, MutableMap<Int, ReceivedMessagesPage>> =
            mutableMapOf(MessageMailboxType.Received to pages),
        var failInitial: Boolean = false,
    ) : MessagesRepository {
        override val unreadCountInvalidations: Flow<Unit> = MutableSharedFlow()
        val receivedRequests = mutableListOf<ReceivedRequest>()
        val detailRequests = mutableListOf<String>()
        val markReadRequests = mutableListOf<String>()
        var failNextPageForLastId: Int? = null
        var failDetailsForApiKey: String? = null
        var failMarkReadForApiKey: String? = null

        override suspend fun getMailboxes(): List<Mailbox> = emptyList()

        override suspend fun getUnreadCount(): Int = 0

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
        ): ReceivedMessagesPage {
            receivedRequests += ReceivedRequest(mailboxType, lastMessageId, pageSize)
            if (lastMessageId == 0 && failInitial) error("initial failed")
            if (lastMessageId == failNextPageForLastId) error("next failed")
            return pagesByMailbox.getValue(mailboxType).getValue(lastMessageId)
        }

        override suspend fun getMessageDetails(apiGlobalKey: String): MessageDetails {
            detailRequests += apiGlobalKey
            if (apiGlobalKey == failDetailsForApiKey) error("details failed")
            return messageDetails(apiGlobalKey)
        }

        override suspend fun markMessageAsRead(apiGlobalKey: String) {
            markReadRequests += apiGlobalKey
            if (apiGlobalKey == failMarkReadForApiKey) error("mark read failed")
        }
    }

    private data class ReceivedRequest(
        val mailboxType: MessageMailboxType,
        val lastMessageId: Int,
        val pageSize: Int,
    )

    private companion object {
        fun message(
            id: Int,
            isUnread: Boolean = id % 2 == 0,
        ): ReceivedMessage =
            ReceivedMessage(
                id = id,
                apiGlobalKey = "api-key-$id",
                sender = "Anna Kowalska",
                subject = "Temat $id",
                date = LocalDateTime.of(2026, 9, 14, 18, 32),
                isUnread = isUnread,
                hasAttachments = false,
            )

        fun messageDetails(apiGlobalKey: String): MessageDetails =
            MessageDetails(
                id = apiGlobalKey.substringAfterLast("-").toInt(),
                apiGlobalKey = apiGlobalKey,
                sender = "Anna Kowalska",
                senderType = 1,
                recipients = listOf("Jan Nowak"),
                subject = "Temat",
                content = "Treść",
                date = LocalDateTime.of(2026, 9, 14, 18, 32),
                read = true,
                attachments = emptyList(),
                senderInfo = null,
                withdrawn = false,
                withdrawnAt = null,
            )
    }
}
