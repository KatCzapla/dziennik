package pl.usagi.edziennik.ui.settings

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.WorkInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.usagi.edziennik.background.AndroidChangeNotifier

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class SettingsDiagnosticsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Test fun missingPermissionAndUncreatedChannelAreDistinct() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val state = notificationDiagnosticState(context)
        assertFalse(state.permissionGranted)
        assertEquals(DiagnosticChannelState.NOT_CREATED, state.channel)
        assertNull(manager.getNotificationChannel(AndroidChangeNotifier.CHANNEL_ID))
    }

    @Test fun permissionGrantedAndChannelEnabled() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.createNotificationChannel(NotificationChannel(AndroidChangeNotifier.CHANNEL_ID, "Test", NotificationManager.IMPORTANCE_DEFAULT))
        val state = notificationDiagnosticState(context)
        assertTrue(state.permissionGranted)
        assertTrue(state.appEnabled)
        assertEquals(DiagnosticChannelState.ENABLED, state.channel)
    }

    @Test fun blockedChannelIsReported() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.createNotificationChannel(NotificationChannel(AndroidChangeNotifier.CHANNEL_ID, "Test", NotificationManager.IMPORTANCE_NONE))
        assertEquals(DiagnosticChannelState.BLOCKED, notificationDiagnosticState(context).channel)
    }

    @Test fun applicationNotificationSettingIsSeparateFromRuntimePermission() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(false)
        val state = notificationDiagnosticState(context)
        assertTrue(state.permissionGranted)
        assertFalse(state.appEnabled)
    }

    @Test @Config(sdk = [32]) fun olderAndroidDoesNotRequireRuntimeNotificationPermission() {
        assertTrue(notificationDiagnosticState(context).permissionGranted)
    }

    @Test fun activeWorkIsRegisteredButCompletedAndCancelledWorkAreNot() {
        listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED).forEach {
            assertTrue(SettingsUiState(periodicStates = listOf(it)).periodicRegistered)
        }
        listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED).forEach {
            assertFalse(SettingsUiState(periodicStates = listOf(it)).periodicRegistered)
        }
        assertFalse(SettingsUiState().periodicRegistered)
    }
}
