@file:kotlinx.serialization.UseSerializers(
    LocalDateSerializer::class,
    LocalTimeSerializer::class,
    LocalDateTimeSerializer::class,
    BigDecimalSerializer::class,
)

package pl.usagi.edziennik.domain.changes

import kotlinx.serialization.Serializable

import pl.usagi.edziennik.domain.AttendanceStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
sealed interface ChangeEvent {
    @Serializable
    data class NewMessage(val id: Int, val subject: String, val date: LocalDateTime, val sender: String? = null) : ChangeEvent
    @Serializable
    data class NewGrade(
        val periodId: Int,
        val subjectPosition: Int,
        val subjectName: String,
        val columnId: Int,
        val value: String,
        val date: LocalDate,
    ) : ChangeEvent

    @Serializable
    data class EditedGrade(
        val periodId: Int,
        val subjectPosition: Int,
        val subjectName: String,
        val columnId: Int,
        val oldValue: String,
        val newValue: String,
        val date: LocalDate,
    ) : ChangeEvent

    @Serializable
    data class LessonCancelled(val slot: TimetableSlot, val subject: String) : ChangeEvent
    @Serializable
    data class LessonSubstituted(val slot: TimetableSlot, val subject: String, val teacher: String? = null) : ChangeEvent
    @Serializable
    data class LessonUpdated(val slot: TimetableSlot, val subject: String, val details: String? = null) : ChangeEvent
    @Serializable
    data class AttendanceChanged(
        val key: AttendanceKey,
        val subject: String?,
        @Serializable(with = AttendanceStatusSerializer::class) val status: AttendanceStatus,
        val isNew: Boolean,
        val startTime: LocalTime? = null,
    ) : ChangeEvent

    @Serializable
    data class TaskChanged(
        val key: TaskKey,
        val date: LocalDate,
        val subject: String?,
        val isNew: Boolean,
        val description: String? = null,
    ) : ChangeEvent
    @Serializable
    data class NewInformation(val date: LocalDate, val subject: String) : ChangeEvent
    @Serializable
    data class PaymentChanged(val operationName: String?, val amountDue: BigDecimal?) : ChangeEvent
}
