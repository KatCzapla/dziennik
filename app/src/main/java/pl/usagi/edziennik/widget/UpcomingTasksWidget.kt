package pl.usagi.edziennik.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import pl.usagi.edziennik.MainActivity
import pl.usagi.edziennik.R
import pl.usagi.edziennik.ui.timetable.dayMonthLabel
import pl.usagi.edziennik.ui.timetable.polishName
import javax.inject.Inject

@AndroidEntryPoint
class UpcomingTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    @Inject lateinit var contentReader: UpcomingTasksContentReader
    @Inject lateinit var invalidations: LocalWidgetInvalidations

    override val glanceAppWidget: GlanceAppWidget by lazy { UpcomingTasksWidget(contentReader, invalidations) }

    override fun onReceive(context: Context, intent: Intent) {
        if (!handleWidgetClockBroadcast(context, intent, UpcomingTasksWidgetReceiver::class.java)) {
            super.onReceive(context, intent)
        }
    }
}

internal class UpcomingTasksWidget(
    private val contentReader: UpcomingTasksContentReader,
    private val invalidations: LocalWidgetInvalidations,
) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val presentation = withContext(Dispatchers.IO) { contentReader.read() }
        val workState = WorkManager.getInstance(context).manualWidgetRefreshActive()
        val initiallyRefreshing = workState.first()
        val contentFlow = combine(invalidations.revision, workState) { _, refreshing ->
            contentReader.read() to refreshing
        }.flowOn(Dispatchers.IO)
        provideContent {
            val content by contentFlow.collectAsState(initial = presentation to initiallyRefreshing)
            UpcomingTasksContent(content.first, content.second)
        }
    }
}

class GlanceTasksWidgetUpdater @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val reader: UpcomingTasksContentReader,
    private val invalidations: LocalWidgetInvalidations,
) : TasksWidgetUpdater {
    override suspend fun update() {
        invalidations.invalidate()
        UpcomingTasksWidget(reader, invalidations).updateAll(context)
    }
}

private val taskInk = ColorProvider(R.color.timetable_widget_text)
private val taskMuted = ColorProvider(R.color.timetable_widget_secondary_text)
private val taskAccent = ColorProvider(R.color.timetable_widget_accent)

private fun tasksWidgetIntent(context: Context, route: String) = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    putExtra(MainActivity.EXTRA_NOTIFICATION_ROUTE, route)
}

@Composable
private fun UpcomingTasksContent(presentation: UpcomingTasksPresentation, refreshing: Boolean) {
    val context = LocalContext.current
    val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val count = visibleTaskCount(LocalSize.current.height.value, scale, presentation.message != null, refreshing)
    val shown = presentation.items.take(count)
    val remaining = presentation.items.size - shown.size
    val openModule = actionStartActivity(tasksWidgetIntent(context, "schoolTasks"))
    Column(modifier = GlanceModifier.fillMaxSize().background(ColorProvider(R.color.timetable_widget_background))
        .padding(16.dp).clickable(openModule)) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight()
                ) {
                    Text(
                        "Sprawdziany i zadania", modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            color = taskInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ), maxLines = 2
                    )
                    Text(
                        presentation.rangeLabel,
                        style = TextStyle(color = taskMuted, fontSize = 12.sp),
                        maxLines = 1
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.End
                ) {
                    if (refreshing) {
                        Text("Odświeżanie...", style = TextStyle(color = taskMuted, fontSize = 12.sp), maxLines = 1)

                        Spacer(modifier = GlanceModifier.width(8.dp))
                    }
                    Image(provider = ImageProvider(R.drawable.ic_widget_refresh), contentDescription = "Odśwież zadania",
                        colorFilter = ColorFilter.tint(taskMuted), modifier = GlanceModifier.size(32.dp).padding(6.dp)
                            .clickable(actionRunCallback<RefreshTimetableAction>()))
                }
            }
            Spacer(GlanceModifier.height(8.dp))
        }
        presentation.message?.let {
            Text(it, modifier = GlanceModifier.padding(bottom = 8.dp),
                style = TextStyle(color = taskMuted, fontSize = 12.sp), maxLines = 2)
        }
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            shown.forEach { task ->
                Column(modifier = GlanceModifier.fillMaxWidth().height((64f * scale).dp).padding(vertical = 4.dp)
                    .clickable(actionStartActivity(tasksWidgetIntent(context, task.target.route())))) {
                    Row() {
                        Text("${task.dateLabel} · ", modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = taskMuted, fontSize = 12.sp), maxLines = 1)
                        Text(
                            task.typeLabel,
                            style = TextStyle(color = taskAccent, fontSize = 12.sp),
                            maxLines = 1)
                    }
                    Text(task.subject, style = TextStyle(color = taskInk, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium), maxLines = 1)
                    task.description?.let { Text(it, style = TextStyle(color = taskMuted, fontSize = 12.sp), maxLines = 1) }
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            if (remaining > 0) Text("+$remaining więcej w aplikacji", style = TextStyle(color = taskAccent, fontSize = 12.sp), maxLines = 1)
            presentation.lastCompleteLabel?.let { Text(it, style = TextStyle(color = taskMuted, fontSize = 12.sp), maxLines = 2) }
        }
    }
}
