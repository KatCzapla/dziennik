package pl.usagi.edziennik.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.work.WorkManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
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
import androidx.glance.layout.width
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import pl.usagi.edziennik.MainActivity
import pl.usagi.edziennik.R
import javax.inject.Inject

@AndroidEntryPoint
class TodayTimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    @Inject
    lateinit var contentReader: TodayTimetableContentReader
    @Inject
    lateinit var invalidations: TimetableWidgetInvalidations

    override val glanceAppWidget: GlanceAppWidget by lazy {
        TodayTimetableWidget(
            contentReader,
            invalidations
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (isWidgetClockBroadcast(intent.action)) {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TodayTimetableWidgetReceiver::class.java))
            if (ids.isNotEmpty()) enqueueLocalTimetableWidgetUpdate(context)
        } else {
            super.onReceive(context, intent)
        }
    }
}

internal fun isWidgetClockBroadcast(action: String?): Boolean = action in setOf(
    Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED,
)

internal class TodayTimetableWidget(
    private val contentReader: TodayTimetableContentReader,
    private val invalidations: TimetableWidgetInvalidations,
) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val presentation = withContext(Dispatchers.IO) { contentReader.read() }
        val workState = WorkManager.getInstance(context).manualWidgetRefreshActive()
        val initiallyRefreshing = workState.first()
        val contentFlow = combine(invalidations.revision, workState) { _, refreshing ->
            contentReader.read() to refreshing
        }.flowOn(Dispatchers.IO)
        val openTimetable = Intent(context, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_ROUTE, "timetable")
        }
        provideContent {
            val content by contentFlow.collectAsState(initial = presentation to initiallyRefreshing)
            TimetableContent(content.first, openTimetable, content.second)
        }
    }

}

private val ink = ColorProvider(R.color.timetable_widget_text)
private val muted = ColorProvider(R.color.timetable_widget_secondary_text)
private val accent = ColorProvider(R.color.timetable_widget_accent)
private val error = ColorProvider(R.color.timetable_widget_error)

@Composable
private fun TimetableContent(
    presentation: TodayTimetablePresentation,
    openTimetable: Intent,
    refreshing: Boolean
) {
    val action = actionStartActivity(openTimetable)
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(R.color.timetable_widget_background)).clickable(action),
    ) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
            item {
                Column(modifier = GlanceModifier.fillMaxWidth().clickable(action)) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Plan na dziś", modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.End
                        ) {
                            if (refreshing) {
                                Text(
                                    "Odświeżanie...",
                                    style = TextStyle(color = muted, fontSize = 12.sp)
                                )

                                Spacer(modifier = GlanceModifier.width(8.dp))
                            }
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_refresh),
                                contentDescription = "Odśwież plan",
                                colorFilter = ColorFilter.tint(muted),
                                modifier = GlanceModifier.size(32.dp).padding(6.dp)
                                    .clickable(actionRunCallback<RefreshTimetableAction>()),
                            )
                        }
                    }
                    Text(presentation.dateLabel, style = TextStyle(color = muted, fontSize = 12.sp))
                    Spacer(GlanceModifier.height(12.dp))
                }
            }
            presentation.message?.let { message ->
                item {
                    Text(
                        message, style = TextStyle(color = muted, fontSize = 13.sp),
                        modifier = GlanceModifier.clickable(action)
                    )
                }
            }
            items(presentation.lessons) { lesson ->
                Column(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clickable(action)
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            lesson.startTime, modifier = GlanceModifier.width(42.dp),
                            style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            lesson.subject, modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                        lesson.room?.let { room ->
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                room, modifier = GlanceModifier.width(36.dp),
                                style = TextStyle(
                                    color = muted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.End
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    if (lesson.cancelled || lesson.substituted) {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            Spacer(GlanceModifier.width(50.dp))
                            if (lesson.cancelled) {
                                Text("Odwołana", style = TextStyle(color = error, fontSize = 12.sp))
                            }
                            if (lesson.cancelled && lesson.substituted) Spacer(
                                GlanceModifier.width(
                                    8.dp
                                )
                            )
                            if (lesson.substituted) {
                                Text(
                                    "Zastępstwo",
                                    style = TextStyle(color = accent, fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }
            }
            presentation.lastCompleteLabel?.let { label ->
                item {
                    Column(modifier = GlanceModifier.clickable(action)) {
                        Spacer(GlanceModifier.height(10.dp))
                        Text(
                            "Zsynchronizowano $label",
                            style = TextStyle(color = muted, fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }
}
