@file:kotlinx.serialization.UseSerializers(
    LocalDateSerializer::class,
    LocalTimeSerializer::class,
    LocalDateTimeSerializer::class,
    BigDecimalSerializer::class,
)

package pl.usagi.edziennik.domain.changes

import kotlinx.serialization.Serializable
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.TimetableChangeKind
import pl.usagi.edziennik.domain.TimetableEntry
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
data class MessageSnapshot(val id: Int, val subject: String, val date: LocalDateTime)

fun List<ReceivedMessage>.messageSnapshots(): List<MessageSnapshot> =
    map { MessageSnapshot(it.id, it.subject, it.date) }

@Serializable
data class GradeFingerprint(
    val value: String,
    val date: LocalDate,
    val weight: BigDecimal?,
    val category: String,
    val teacher: String?,
    val color: Int?,
    val correctedGradeId: Int?,
)

@Serializable
data class GradeSnapshot(
    val periodId: Int,
    val subjectPosition: Int,
    val subjectName: String,
    val columnId: Int,
    val fingerprint: GradeFingerprint,
    val changedSinceLastLogin: Boolean,
)

fun Grades.gradeSnapshots(periodId: Int): List<GradeSnapshot> =
    subjects.flatMap { subject ->
        subject.columns.flatMap { column ->
            column.grades.map { grade ->
                GradeSnapshot(
                    periodId = periodId,
                    subjectPosition = subject.position,
                    subjectName = subject.subjectName,
                    columnId = column.id,
                    fingerprint = grade.fingerprint(),
                    changedSinceLastLogin = grade.changedSinceLastLogin,
                )
            }
        }
    }

private fun Grade.fingerprint() = GradeFingerprint(
    value = value,
    date = date,
    weight = weight,
    category = category,
    teacher = teacher,
    color = color,
    correctedGradeId = correctedGradeId,
)

@Serializable
data class TimetableSlot(val date: LocalDate, val start: LocalTime, val end: LocalTime)

@Serializable
data class TimetableSnapshot(
    val slot: TimetableSlot,
    val subject: String,
    val teacher: String?,
    val supportingTeacher1: String?,
    val supportingTeacher2: String?,
    val room: String?,
    val cancelled: Boolean,
    val substitutions: List<SubstitutionSnapshot>,
    val firstSupportingTeacherAbsent: Boolean,
)

@Serializable
data class SubstitutionSnapshot(
    val teacher: String?,
    val subject: String?,
    val room: String?,
)

fun List<TimetableEntry>.timetableSnapshots(): List<TimetableSnapshot> = map { entry ->
    TimetableSnapshot(
        slot = TimetableSlot(entry.date, entry.startTime, entry.endTime),
        subject = entry.subject,
        teacher = entry.teacher,
        supportingTeacher1 = entry.supportingTeacher1,
        supportingTeacher2 = entry.supportingTeacher2,
        room = entry.room,
        cancelled = entry.isCancelled,
        substitutions = entry.changes.filter { it.kind == TimetableChangeKind.Substitution }
            .map { SubstitutionSnapshot(it.teacher, it.subject, it.room) },
        firstSupportingTeacherAbsent = entry.changes.any {
            it.kind == TimetableChangeKind.FirstSupportingTeacherAbsence
        },
    )
}

@Serializable
data class AttendanceKey(val date: LocalDate, val lessonTimeId: Int?, val classLessonId: Int?)

@Serializable
data class AttendanceSnapshot(
    val key: AttendanceKey,
    val subject: String?,
    @Serializable(with = AttendanceStatusSerializer::class) val status: AttendanceStatus,
)

fun List<AttendanceEntry>.attendanceSnapshots(): List<AttendanceSnapshot> = map { entry ->
    AttendanceSnapshot(
        key = AttendanceKey(entry.date, entry.lessonTimeId, entry.classLessonId),
        subject = entry.subject,
        status = when (val status = entry.status) {
            is AttendanceStatus.Unknown -> AttendanceStatus.Unknown(status.rawValue)
            else -> status
        },
    )
}

@Serializable
data class TaskKey(val rawType: Int, val id: Int)

@Serializable
data class TaskDetailsSnapshot(val description: String?, val responseDeadline: LocalDate?)

@Serializable
data class TaskSnapshot(
    val key: TaskKey,
    val date: LocalDate,
    val subject: String?,
    val hasAttachment: Boolean,
    val details: TaskDetailsSnapshot?,
)

fun List<SchoolTask>.taskSnapshots(): List<TaskSnapshot> = map { task ->
    TaskSnapshot(
        key = TaskKey(task.rawType, task.id),
        date = task.date,
        subject = task.subject,
        hasAttachment = task.hasAttachment,
        details = task.details?.let { TaskDetailsSnapshot(it.description, it.responseDeadline) },
    )
}

@Serializable
data class InformationKey(val date: LocalDate, val subject: String)

@Serializable
data class InformationSnapshot(val key: InformationKey)

fun List<DashboardInformation>.informationSnapshots(): List<InformationSnapshot> =
    map { InformationSnapshot(InformationKey(it.date, it.subject)) }

@Serializable
data class PaymentAccountSnapshot(val operationName: String?, val amountDue: BigDecimal?)

@Serializable
data class PaymentsSnapshot(val accounts: List<PaymentAccountSnapshot>)

fun Payments.toSnapshot(): PaymentsSnapshot = PaymentsSnapshot(
    bankAccounts.map { PaymentAccountSnapshot(it.operationName, it.amountDue) },
)
