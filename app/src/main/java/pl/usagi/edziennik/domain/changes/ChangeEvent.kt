package pl.usagi.edziennik.domain.changes

import pl.usagi.edziennik.domain.AttendanceStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface ChangeEvent {
    data class NewMessage(val id: Int, val subject: String, val date: LocalDateTime) : ChangeEvent
    data class NewGrade(
        val periodId: Int,
        val subjectPosition: Int,
        val subjectName: String,
        val columnId: Int,
        val value: String,
        val date: LocalDate,
    ) : ChangeEvent

    data class EditedGrade(
        val periodId: Int,
        val subjectPosition: Int,
        val subjectName: String,
        val columnId: Int,
        val oldValue: String,
        val newValue: String,
        val date: LocalDate,
    ) : ChangeEvent

    data class LessonCancelled(val slot: TimetableSlot, val subject: String) : ChangeEvent
    data class LessonSubstituted(val slot: TimetableSlot, val subject: String) : ChangeEvent
    data class LessonUpdated(val slot: TimetableSlot, val subject: String) : ChangeEvent
    data class AttendanceChanged(
        val key: AttendanceKey,
        val subject: String?,
        val status: AttendanceStatus,
        val isNew: Boolean,
    ) : ChangeEvent

    data class TaskChanged(val key: TaskKey, val date: LocalDate, val subject: String?, val isNew: Boolean) : ChangeEvent
    data class NewInformation(val date: LocalDate, val subject: String) : ChangeEvent
    data class PaymentChanged(val operationName: String?, val amountDue: BigDecimal?) : ChangeEvent
}
