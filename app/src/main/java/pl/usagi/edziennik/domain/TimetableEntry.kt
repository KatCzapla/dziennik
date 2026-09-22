package pl.usagi.edziennik.domain

import java.time.LocalDate
import java.time.LocalTime

data class TimetableEntry(
    val date: LocalDate,
    val lessonNumber: Int?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val teacher: String?,
    val supportingTeacher1: String? = null,
    val supportingTeacher2: String? = null,
    val room: String?,
    val changes: List<TimetableChange>,
) {
    val isCancelled: Boolean
        get() = changes.any { it.kind == TimetableChangeKind.Cancelled }

    val cancellationReason: String?
        get() = changes.firstNotNullOfOrNull { change ->
            change.absenceInformation.takeIf {
                change.kind == TimetableChangeKind.Cancelled && !it.isNullOrBlank()
            }
        }
}

data class TimetableChange(
    val changeType: Int,
    val kind: TimetableChangeKind,
    val teacherType: Int?,
    val day: String?,
    val lessonNumber: Int?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val group: String?,
    val subject: String?,
    val teacher: String?,
    val room: String?,
    val absenceInformation: String?,
) {
    val isSubstitution: Boolean
        get() = kind == TimetableChangeKind.Substitution && teacher != null

    val isTeacherAbsence: Boolean
        get() = kind == TimetableChangeKind.FirstSupportingTeacherAbsence

    val absenceEffect: String?
        get() = absenceInformation.takeUnless { it.isNullOrBlank() }
}

sealed interface TimetableChangeKind {
    data object Cancelled : TimetableChangeKind
    data object Substitution : TimetableChangeKind
    data object FirstSupportingTeacherAbsence : TimetableChangeKind
    data class Unknown(val rawValue: Int) : TimetableChangeKind
}
