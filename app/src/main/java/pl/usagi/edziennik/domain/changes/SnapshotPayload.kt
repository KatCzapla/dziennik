package pl.usagi.edziennik.domain.changes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SnapshotPayload {
    @Serializable @SerialName("messages")
    data class Messages(val items: List<MessageSnapshot>) : SnapshotPayload

    @Serializable @SerialName("grades")
    data class Grades(val items: List<GradeSnapshot>) : SnapshotPayload

    @Serializable @SerialName("timetable")
    data class Timetable(val items: List<TimetableSnapshot>) : SnapshotPayload

    @Serializable @SerialName("attendance")
    data class Attendance(val items: List<AttendanceSnapshot>) : SnapshotPayload

    @Serializable @SerialName("tasks")
    data class Tasks(val items: List<TaskSnapshot>) : SnapshotPayload

    @Serializable @SerialName("information")
    data class Information(val items: List<InformationSnapshot>) : SnapshotPayload

    @Serializable @SerialName("payments")
    data class Payments(val value: PaymentsSnapshot) : SnapshotPayload
}
