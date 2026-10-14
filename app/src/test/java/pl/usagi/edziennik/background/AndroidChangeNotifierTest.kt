package pl.usagi.edziennik.background

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.OutboxEntity
import pl.usagi.edziennik.persistence.changes.TestIdentityStore
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AndroidChangeNotifierTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val event = ChangeEvent.NewInformation(LocalDate.of(2026, 10, 12), "Informacja")

    @Test fun missingPermissionRetainsPendingEvent() = runBlocking {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertPending()
    }

    @Test fun disabledApplicationNotificationsRetainPendingEvent() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(false)
        assertPending()
    }

    @Test fun blockedChannelRetainsPendingEvent() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.createNotificationChannel(NotificationChannel(AndroidChangeNotifier.CHANNEL_ID, "Zmiany w dzienniku", NotificationManager.IMPORTANCE_NONE))
        assertPending()
    }

    @Test fun successfulNotifyUsesDurableIdAndRetryReplacesNotification() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = AndroidChangeNotifier(context)
        assertTrue(notifier.publish(41, event))
        assertTrue(notifier.publish(41, event))
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(41, manager.activeNotifications.single().id)
    }

    private suspend fun assertPending() {
        val identity = LocalSnapshotIdentity(TestIdentityStore())
        val account = identity.ensureAccountId()
        var acknowledged = false
        val store = object : NotificationOutboxStore {
            override suspend fun pending(accountId: String) = listOf(
                OutboxEntity(41, account.value, Json.encodeToString<ChangeEvent>(event), 0))
            override suspend fun markHandedOff(id: Int, accountId: String) { acknowledged = true }
        }
        var handedOff = 0
        NotificationOutboxPublisher(store, identity, AndroidChangeNotifier(context), Json)
            .publishPending { handedOff++ }
        assertEquals(0, handedOff)
        assertFalse(acknowledged)
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
