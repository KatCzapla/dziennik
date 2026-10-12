package pl.usagi.edziennik.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import pl.usagi.edziennik.background.ManualSyncScheduler

class RefreshTimetableAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoint = EntryPointAccessors.fromApplication(context, RefreshTimetableEntryPoint::class.java)
        entryPoint.scheduler().refresh()
        entryPoint.updater().updateSafely()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RefreshTimetableEntryPoint {
    fun scheduler(): ManualSyncScheduler
    fun updater(): WidgetUpdater
}
