package pl.usagi.edziennik.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

fun interface WidgetUpdater {
    suspend fun update()
}

fun interface TimetableWidgetUpdater : WidgetUpdater
fun interface TasksWidgetUpdater : WidgetUpdater

class LocalWidgetsUpdater @Inject constructor(
    private val timetable: TimetableWidgetUpdater,
    private val tasks: TasksWidgetUpdater,
) : WidgetUpdater {
    override suspend fun update() {
        timetable.updateSafely()
        tasks.updateSafely()
    }
}

@Singleton
class LocalWidgetInvalidations @Inject constructor() {
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    fun invalidate() {
        mutableRevision.update { it + 1 }
    }
}

class GlanceTimetableWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val contentReader: TodayTimetableContentReader,
    private val invalidations: LocalWidgetInvalidations,
) : TimetableWidgetUpdater {
    override suspend fun update() {
        // Running Glance sessions also need a signal to re-read their local data.
        invalidations.invalidate()
        TodayTimetableWidget(contentReader, invalidations).updateAll(context)
    }
}

internal suspend fun WidgetUpdater.updateSafely() {
    try {
        update()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Rendering failure must not undo a snapshot or interrupt notifications.
    }
}
