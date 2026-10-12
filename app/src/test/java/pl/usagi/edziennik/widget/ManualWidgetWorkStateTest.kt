package pl.usagi.edziennik.widget

import androidx.work.WorkInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualWidgetWorkStateTest {
    @Test fun enqueuedShowsRefreshingEvenBeforeWorkerStarts() {
        assertTrue(isManualWidgetRefreshActive(listOf(WorkInfo.State.ENQUEUED)))
    }

    @Test fun runningShowsRefreshing() {
        assertTrue(isManualWidgetRefreshActive(listOf(WorkInfo.State.RUNNING)))
    }

    @Test fun succeededClearsRefreshing() {
        assertFalse(isManualWidgetRefreshActive(listOf(WorkInfo.State.SUCCEEDED)))
    }

    @Test fun failedClearsRefreshing() {
        assertFalse(isManualWidgetRefreshActive(listOf(WorkInfo.State.FAILED)))
    }

    @Test fun cancelledClearsRefreshing() {
        assertFalse(isManualWidgetRefreshActive(listOf(WorkInfo.State.CANCELLED)))
    }

    @Test fun noWorkDoesNotShowRefreshing() {
        assertFalse(isManualWidgetRefreshActive(emptyList()))
    }

    @Test fun historicalFinishedWorkDoesNotHideNewQueuedWork() {
        assertTrue(isManualWidgetRefreshActive(listOf(WorkInfo.State.FAILED,
            WorkInfo.State.CANCELLED, WorkInfo.State.SUCCEEDED, WorkInfo.State.ENQUEUED)))
    }

    @Test fun processRecreationReadsFinishedWorkWithoutInMemoryFlag() {
        assertTrue(isManualWidgetRefreshActive(listOf(WorkInfo.State.RUNNING)))
        assertFalse(isManualWidgetRefreshActive(listOf(WorkInfo.State.FAILED)))
    }

    @Test fun blockedWorkIsStillPending() {
        assertTrue(isManualWidgetRefreshActive(listOf(WorkInfo.State.BLOCKED)))
    }
}
