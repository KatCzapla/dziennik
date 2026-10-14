package pl.usagi.edziennik.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import pl.usagi.edziennik.background.AndroidChangeNotifier
import pl.usagi.edziennik.background.PeriodicSyncScheduler
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.SyncDiagnosticEntry
import pl.usagi.edziennik.persistence.changes.SyncDiagnostics
import javax.inject.Inject

enum class DiagnosticChannelState { NOT_CREATED, ENABLED, BLOCKED, NOT_REQUIRED }

data class DiagnosticNotifications(
    val permissionGranted: Boolean,
    val appEnabled: Boolean,
    val channel: DiagnosticChannelState,
)

fun notificationDiagnosticState(context: Context): DiagnosticNotifications {
    val channel = if (Build.VERSION.SDK_INT < 26) DiagnosticChannelState.NOT_REQUIRED else {
        val current = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(AndroidChangeNotifier.CHANNEL_ID)
        when {
            current == null -> DiagnosticChannelState.NOT_CREATED
            current.importance == NotificationManager.IMPORTANCE_NONE -> DiagnosticChannelState.BLOCKED
            else -> DiagnosticChannelState.ENABLED
        }
    }
    return DiagnosticNotifications(
        permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
        appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        channel = channel,
    )
}

data class SettingsUiState(
    val hasAccount: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val periodicStates: List<WorkInfo.State> = emptyList(),
    val history: List<SyncDiagnosticEntry> = emptyList(),
    val pendingCount: Int = 0,
    val notifications: DiagnosticNotifications? = null,
) {
    val periodicRegistered: Boolean get() = periodicStates.any { !it.isFinished }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val identity: LocalSnapshotIdentity,
    private val diagnostics: SyncDiagnostics,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState = state.asStateFlow()

    suspend fun refresh() {
        val account = identity.currentAccountId()
        if (account == null) {
            state.value = SettingsUiState(loading = false)
            return
        }
        try {
            val work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(PeriodicSyncScheduler.WORK_NAME).first()
            val history = diagnostics.history()
            val pending = diagnostics.pendingCount()
            state.value = if (identity.currentAccountId() == account) SettingsUiState(
                hasAccount = true, loading = false, periodicStates = work.map { it.state }.distinct(),
                history = history, pendingCount = pending, notifications = notificationDiagnosticState(context),
            ) else SettingsUiState(loading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.value = SettingsUiState(hasAccount = identity.currentAccountId() == account,
                loading = false, failed = true)
        }
    }
}
