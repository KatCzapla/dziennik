package pl.usagi.edziennik.background

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlinx.coroutines.test.runTest
import pl.usagi.edziennik.persistence.changes.LocalIdentityStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity

class ManualSyncSchedulerTest {
    @Test fun manualRefreshRequestsUniqueOneShotWork() = runTest {
        val requests = mutableListOf<Pair<String, ExistingWorkPolicy>>()
        ManualSyncScheduler(ManualSyncWorkQueue { name, policy -> requests += name to policy }, signedInIdentity()).refresh()
        assertEquals(listOf(ManualSyncScheduler.WORK_NAME to ExistingWorkPolicy.KEEP), requests)
    }

    @Test fun repeatedClicksKeepOnlyOneUnfinishedRequest() = runTest {
        val queue = KeepingQueue()
        val scheduler = ManualSyncScheduler(queue, signedInIdentity())
        repeat(20) { scheduler.refresh() }
        assertEquals(setOf(ManualSyncScheduler.WORK_NAME), queue.active)
        assertEquals(1, queue.accepted)
        queue.active.clear()
        scheduler.refresh()
        assertEquals(2, queue.accepted)
    }

    @Test fun manualRefreshUsesSeparateNameAndLeavesPeriodicWorkUntouched() = runTest {
        val queue = KeepingQueue().apply { active += PeriodicSyncScheduler.WORK_NAME }
        ManualSyncScheduler(queue, signedInIdentity()).refresh()
        assertNotEquals(PeriodicSyncScheduler.WORK_NAME, ManualSyncScheduler.WORK_NAME)
        assertEquals(setOf(PeriodicSyncScheduler.WORK_NAME, ManualSyncScheduler.WORK_NAME), queue.active)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, PeriodicSyncScheduler.POLICY)
    }

    @Test fun signedOutUserCannotEnqueueRefreshEvenThroughOldWidgetAction() = runTest {
        val identity = signedInIdentity()
        val queue = KeepingQueue()
        val scheduler = ManualSyncScheduler(queue, identity)
        identity.clearAccount()
        scheduler.refresh()
        assertEquals(0, queue.accepted)
        assertEquals(emptySet<String>(), queue.active)
    }

    @Test fun refreshBecomesAvailableForNewAccountAfterLogin() = runTest {
        val identity = signedInIdentity()
        val queue = KeepingQueue()
        val scheduler = ManualSyncScheduler(queue, identity)
        identity.clearAccount()
        scheduler.refresh()
        identity.ensureAccountId()
        scheduler.refresh()
        assertEquals(1, queue.accepted)
    }

    private fun signedInIdentity() = LocalSnapshotIdentity(object : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }).apply { ensureAccountId() }

    private class KeepingQueue : ManualSyncWorkQueue {
        val active = mutableSetOf<String>()
        var accepted = 0
        override suspend fun enqueueUnique(name: String, policy: ExistingWorkPolicy) {
            assertEquals(ExistingWorkPolicy.KEEP, policy)
            if (active.add(name)) accepted++
        }
    }
}
