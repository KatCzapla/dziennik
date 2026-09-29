package pl.usagi.edziennik.domain.changes

import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.TimetableEntry

fun detectMessages(old: List<MessageSnapshot>, current: List<ReceivedMessage>): List<ChangeEvent> {
    val oldIds = old.mapTo(mutableSetOf()) { it.id }
    return current.messageSnapshots().distinctBy { it.id }
        .filter { it.id !in oldIds }
        .map { ChangeEvent.NewMessage(it.id, it.subject, it.date) }
}

fun detectGrades(old: List<GradeSnapshot>, current: Grades, periodId: Int): List<ChangeEvent> {
    val currentSnapshots = current.gradeSnapshots(periodId)
    val oldGroups = old.filter { it.periodId == periodId }.groupBy { Triple(it.periodId, it.subjectPosition, it.columnId) }
    val currentGroups = currentSnapshots.groupBy { Triple(it.periodId, it.subjectPosition, it.columnId) }
    return currentGroups.flatMap { (key, now) ->
        val before = oldGroups[key].orEmpty()
        val oldCounts = before.groupingBy { it.fingerprint }.eachCount()
        val newCounts = now.groupingBy { it.fingerprint }.eachCount()
        val added = now.distinctBy { it.fingerprint }.flatMap { grade ->
            List((newCounts.getValue(grade.fingerprint) - oldCounts.getOrDefault(grade.fingerprint, 0)).coerceAtLeast(0)) {
                grade
            }
        }
        val removed = before.distinctBy { it.fingerprint }.flatMap { grade ->
            List((oldCounts.getValue(grade.fingerprint) - newCounts.getOrDefault(grade.fingerprint, 0)).coerceAtLeast(0)) {
                grade
            }
        }
        when {
            removed.isEmpty() -> added.map { it.newGradeEvent() }
            added.size == 1 && removed.size == 1 &&
                oldCounts[removed.single().fingerprint] == 1 &&
                newCounts[added.single().fingerprint] == 1 &&
                added.single().changedSinceLastLogin &&
                removed.single().fingerprint.sameGradeExceptValue(added.single().fingerprint) &&
                removed.single().fingerprint.value != added.single().fingerprint.value -> {
                val previous = removed.single()
                val updated = added.single()
                listOf(
                    ChangeEvent.EditedGrade(
                        periodId = key.first,
                        subjectPosition = key.second,
                        subjectName = updated.subjectName,
                        columnId = key.third,
                        oldValue = previous.fingerprint.value,
                        newValue = updated.fingerprint.value,
                        date = updated.fingerprint.date,
                    ),
                )
            }
            else -> emptyList()
        }
    }
}

private fun GradeFingerprint.sameGradeExceptValue(other: GradeFingerprint): Boolean =
    copy(value = other.value) == other

private fun GradeSnapshot.newGradeEvent() = ChangeEvent.NewGrade(
    periodId = periodId,
    subjectPosition = subjectPosition,
    subjectName = subjectName,
    columnId = columnId,
    value = fingerprint.value,
    date = fingerprint.date,
)

fun detectTimetable(old: List<TimetableSnapshot>, current: List<TimetableEntry>): List<ChangeEvent> {
    val before = old.groupBy { it.slot }
    return current.timetableSnapshots().groupBy { it.slot }.flatMap { (slot, matches) ->
        val previous = before[slot]?.singleOrNull() ?: return@flatMap emptyList()
        val updated = matches.singleOrNull() ?: return@flatMap emptyList()
        when {
            !previous.cancelled && updated.cancelled ->
                listOf(ChangeEvent.LessonCancelled(slot, updated.subject))
            previous.substitutions.multiset() != updated.substitutions.multiset() && updated.substitutions.isNotEmpty() ->
                listOf(ChangeEvent.LessonSubstituted(slot, updated.subject))
            previous.copy(substitutions = emptyList()) != updated.copy(substitutions = emptyList()) ||
                previous.substitutions.multiset() != updated.substitutions.multiset() ->
                listOf(ChangeEvent.LessonUpdated(slot, updated.subject))
            else -> emptyList()
        }
    }
}

fun detectAttendance(old: List<AttendanceSnapshot>, current: List<AttendanceEntry>): List<ChangeEvent> {
    val before = old.groupBy { it.key }
    return current.attendanceSnapshots().groupBy { it.key }.flatMap { (key, matches) ->
        if (key.lessonTimeId == null && key.classLessonId == null) return@flatMap emptyList()
        val updated = matches.singleOrNull() ?: return@flatMap emptyList()
        if (before[key].orEmpty().size > 1 || updated.status == AttendanceStatus.Present) return@flatMap emptyList()
        val previous = before[key]?.singleOrNull()
        if (previous?.status == updated.status) emptyList()
        else listOf(ChangeEvent.AttendanceChanged(key, updated.subject, updated.status, previous == null))
    }
}

fun detectTasks(old: List<TaskSnapshot>, current: List<SchoolTask>): List<ChangeEvent> {
    val before = old.groupBy { it.key }
    return current.taskSnapshots().groupBy { it.key }.flatMap { (key, matches) ->
        val updated = matches.singleOrNull() ?: return@flatMap emptyList()
        if (before[key].orEmpty().size > 1) return@flatMap emptyList()
        val previous = before[key]?.singleOrNull()
        val changed = previous == null || previous.date != updated.date || previous.subject != updated.subject ||
            previous.hasAttachment != updated.hasAttachment ||
            (previous.details != null && updated.details != null && previous.details != updated.details)
        if (changed) listOf(ChangeEvent.TaskChanged(key, updated.date, updated.subject, previous == null))
        else emptyList()
    }
}

fun detectInformation(old: List<InformationSnapshot>, current: List<DashboardInformation>): List<ChangeEvent> {
    val before = old.groupBy { it.key }
    return current.informationSnapshots().groupBy { it.key }.flatMap { (key, matches) ->
        if (matches.size == 1 && key !in before) listOf(ChangeEvent.NewInformation(key.date, key.subject))
        else emptyList()
    }
}

fun detectPayments(old: PaymentsSnapshot, current: Payments): List<ChangeEvent> {
    val previous = old.accounts.singleOrNull() ?: return emptyList()
    val updated = current.toSnapshot().accounts.singleOrNull() ?: return emptyList()
    val amountChanged = when {
        previous.amountDue == null || updated.amountDue == null -> previous.amountDue != updated.amountDue
        else -> previous.amountDue.compareTo(updated.amountDue) != 0
    }
    return if (previous.operationName != updated.operationName || amountChanged) {
        listOf(ChangeEvent.PaymentChanged(updated.operationName, updated.amountDue))
    }
    else emptyList()
}

private fun <T> List<T>.multiset(): Map<T, Int> = groupingBy { it }.eachCount()
