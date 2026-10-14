package pl.usagi.edziennik.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.usagi.edziennik.MainActivity
import pl.usagi.edziennik.R
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.changes.ChangeEvent
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class NotificationSpec(val title: String, val text: String, val route: String)

fun ChangeEvent.notificationSpec(): NotificationSpec = when (this) {
    is ChangeEvent.NewMessage -> NotificationSpec(
        "Nowa wiadomość" + sender?.takeIf { it.isNotBlank() }?.let { " od ${it.trim()}" }.orEmpty(),
        subject.trim(), "messages",
    )
    is ChangeEvent.NewGrade -> NotificationSpec("Nowa ocena", parts(value, subjectName), "grades")
    is ChangeEvent.EditedGrade -> NotificationSpec("Edycja oceny", parts(newValue, subjectName), "grades")
    is ChangeEvent.LessonCancelled -> NotificationSpec("Odwołana lekcja ${slot.date.shortLabel()}", subject.trim(), "timetable")
    is ChangeEvent.LessonSubstituted -> NotificationSpec(
        "Zastępstwo ${slot.date.shortLabel()}", parts(subject, teacher), "timetable",
    )
    is ChangeEvent.LessonUpdated -> NotificationSpec(
        "Zmiana planu ${slot.date.shortLabel()}", parts(subject, details), "timetable",
    )
    is ChangeEvent.AttendanceChanged -> NotificationSpec(
        "Frekwencja",
        parts(status.notificationLabel(), listOfNotNull(startTime?.shortLabel(), subject?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(" ")),
        "attendance",
    )
    is ChangeEvent.TaskChanged -> NotificationSpec(
        taskTitle(key.rawType, isNew, date),
        parts(subject, description.shortened()),
        "schoolTasks?taskId=${key.id}&taskType=${key.rawType}&taskDate=$date",
    )
    is ChangeEvent.NewInformation -> NotificationSpec("Nowa informacja", subject.trim(), "home")
    is ChangeEvent.PaymentChanged -> NotificationSpec(
        "Zmiana w opłatach",
        partsWithColon(operationName, amountDue?.let {
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pl-PL")).format(it)
        }).ifBlank { "Sprawdź opłaty" },
        "payments",
    )
}

private val notificationDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("pl-PL"))
private val notificationTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val notificationWhitespace = Regex("\\s+")
private const val TASK_DESCRIPTION_LIMIT = 100

private fun LocalDate.shortLabel(): String = format(notificationDateFormat)
private fun LocalTime.shortLabel(): String = format(notificationTimeFormat)

private fun parts(vararg values: String?): String = values.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .joinToString(", ")

private fun partsWithColon(first: String?, second: String?): String =
    listOfNotNull(first?.trim()?.takeIf(String::isNotEmpty), second?.trim()?.takeIf(String::isNotEmpty))
        .joinToString(": ")

private fun String?.shortened(): String? {
    val normalized = this?.replace(notificationWhitespace, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (normalized.length <= TASK_DESCRIPTION_LIMIT) normalized
    else normalized.take(TASK_DESCRIPTION_LIMIT).trimEnd() + "..."
}

private fun taskTitle(rawType: Int, isNew: Boolean, date: LocalDate): String {
    val prefix = when (SchoolTaskType.fromRawValue(rawType)) {
        SchoolTaskType.Test -> if (isNew) "Sprawdzian" else "Zmiana sprawdzianu"
        SchoolTaskType.Quiz -> if (isNew) "Kartkówka" else "Zmiana kartkówki"
        SchoolTaskType.Homework -> if (isNew) "Zadanie domowe" else "Zmiana zadania domowego"
        is SchoolTaskType.Unknown -> "Sprawdziany i zadania"
    }
    return "$prefix ${date.shortLabel()}"
}

private fun AttendanceStatus.notificationLabel(): String = when (this) {
    AttendanceStatus.Present -> "Obecność"
    AttendanceStatus.Absent -> "Nieobecność"
    AttendanceStatus.ExcusedAbsence -> "Nieobecność usprawiedliwiona"
    AttendanceStatus.Late -> "Spóźnienie"
    AttendanceStatus.ExcusedLate -> "Spóźnienie usprawiedliwione"
    AttendanceStatus.SchoolRelatedAbsence -> "Nieobecność z przyczyn szkolnych"
    AttendanceStatus.Released -> "Zwolnienie"
    is AttendanceStatus.Unknown -> "Nowy status frekwencji"
}

interface ChangeNotifier {
    fun publish(id: Int, event: ChangeEvent): Boolean
}

class AndroidChangeNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ChangeNotifier {

    override fun publish(id: Int, event: ChangeEvent): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Zmiany w dzienniku", NotificationManager.IMPORTANCE_DEFAULT),
            )
            if (context.getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        if (event is ChangeEvent.AttendanceChanged && event.status == AttendanceStatus.Present) return false
        val spec = event.notificationSpec()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_ROUTE, spec.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            return false
        }
        return true
    }

    companion object {
        const val CHANNEL_ID = "changes"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object BackgroundModule {
    @Provides fun outboxStore(impl: RoomNotificationOutboxStore): NotificationOutboxStore = impl
    @Provides fun pendingPublisher(impl: NotificationOutboxPublisher): PendingNotificationPublisher = impl
    @Provides fun manualSyncQueue(impl: WorkManagerManualSyncQueue): ManualSyncWorkQueue = impl
    @Provides fun scheduler(impl: PeriodicSyncScheduler): SyncScheduler = impl
    @Provides fun session(impl: BackgroundSessionPreparer): SyncSession = impl
    @Provides fun notifier(impl: AndroidChangeNotifier): ChangeNotifier = impl
}
