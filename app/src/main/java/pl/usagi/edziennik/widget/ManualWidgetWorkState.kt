package pl.usagi.edziennik.widget

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.usagi.edziennik.background.ManualSyncScheduler

fun isManualWidgetRefreshActive(states: List<WorkInfo.State>): Boolean = states.any { !it.isFinished }

internal fun WorkManager.manualWidgetRefreshActive(): Flow<Boolean> =
    getWorkInfosForUniqueWorkFlow(ManualSyncScheduler.WORK_NAME)
        .map { infos -> isManualWidgetRefreshActive(infos.map { it.state }) }
        .distinctUntilChanged()
