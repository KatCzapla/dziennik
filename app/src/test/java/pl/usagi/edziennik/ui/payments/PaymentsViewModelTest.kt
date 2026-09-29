package pl.usagi.edziennik.ui.payments

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.repository.PaymentsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test
    fun loadsPaymentsForSelectedStudentAndSupportsEmptyResponse() = runTest {
        val manager = ActiveStudentManager()
        val repository = FakeRepository()
        val viewModel = PaymentsViewModel(repository, manager)
        manager.initialize(listOf(first))
        advanceUntilIdle()

        assertEquals(listOf(first), repository.requests)
        assertEquals(Payments(), viewModel.uiState.value.payments)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun noStudentDoesNotSendRequest() = runTest {
        val repository = FakeRepository()
        val viewModel = PaymentsViewModel(repository, ActiveStudentManager())
        advanceUntilIdle()
        assertEquals(emptyList<Student>(), repository.requests)
        assertNull(viewModel.uiState.value.payments)
    }

    @Test
    fun failureShowsErrorAndRetryCanLoadPayments() = runTest {
        val manager = ActiveStudentManager()
        val repository = FakeRepository().apply { fail = true }
        val viewModel = PaymentsViewModel(repository, manager)
        manager.initialize(listOf(first))
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        repository.fail = false
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(Payments(), viewModel.uiState.value.payments)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun changingStudentReloadsAndClearingStudentRemovesFinancialData() = runTest {
        val manager = ActiveStudentManager()
        val repository = FakeRepository()
        val viewModel = PaymentsViewModel(repository, manager)
        manager.initialize(listOf(first))
        advanceUntilIdle()
        val second = first.copy(key = "second", name = "Drugi uczeń")
        viewModel.selectStudent(second)
        advanceUntilIdle()

        assertEquals(listOf(first, second), repository.requests)
        assertEquals(second, viewModel.uiState.value.activeStudent)
        manager.clear()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.payments)
        assertNull(viewModel.uiState.value.activeStudent)
    }

    private class FakeRepository : PaymentsRepository {
        val requests = mutableListOf<Student>()
        var fail = false
        override suspend fun getPayments(student: Student): Payments {
            requests += student
            if (fail) error("Download failed")
            return Payments()
        }
    }

    private val first = Student("first", 1, "Uczeń", "4A", "Szkoła")
}
