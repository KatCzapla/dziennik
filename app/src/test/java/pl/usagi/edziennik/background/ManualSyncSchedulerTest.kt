package pl.usagi.edziennik.background

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlinx.coroutines.test.runTest

class ManualSyncSchedulerTest {
    @Test fun manualRefreshRequestsUniqueOneShotWork() = runTest {
        val requests = mutableListOf<Pair<String, ExistingWorkPolicy>>()
        ManualSyncScheduler { name, policy -> requests += name to policy }.refresh()
        assertEquals(listOf(ManualSyncScheduler.WORK_NAME to ExistingWorkPolicy.KEEP), requests)
    }

    @Test fun repeatedClicksKeepOnlyOneUnfinishedRequest() = runTest {
        val queue = KeepingQueue()
        val scheduler = ManualSyncScheduler(queue)
        repeat(20) { scheduler.refresh() }
        assertEquals(setOf(ManualSyncScheduler.WORK_NAME), queue.active)
        assertEquals(1, queue.accepted)
        queue.active.clear()
        scheduler.refresh()
        assertEquals(2, queue.accepted)
    }

    @Test fun manualRefreshUsesSeparateNameAndLeavesPeriodicWorkUntouched() = runTest {
        val queue = KeepingQueue().apply { active += PeriodicSyncScheduler.WORK_NAME }
        ManualSyncScheduler(queue).refresh()
        assertNotEquals(PeriodicSyncScheduler.WORK_NAME, ManualSyncScheduler.WORK_NAME)
        assertEquals(setOf(PeriodicSyncScheduler.WORK_NAME, ManualSyncScheduler.WORK_NAME), queue.active)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, PeriodicSyncScheduler.POLICY)
    }

    private class KeepingQueue : ManualSyncWorkQueue {
        val active = mutableSetOf<String>()
        var accepted = 0
        override suspend fun enqueueUnique(name: String, policy: ExistingWorkPolicy) {
            assertEquals(ExistingWorkPolicy.KEEP, policy)
            if (active.add(name)) accepted++
        }
    }
}
