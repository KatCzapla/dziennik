package pl.usagi.edziennik.persistence.changes

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotPayload
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.TimetableSnapshot
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.clampedSchoolWeekEnd
import pl.usagi.edziennik.domain.time.clampedSchoolWeekStart
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.intersectsSchoolYear
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

sealed interface LocalTimetableResult {
    data object NoBaseline : LocalTimetableResult

    data class Available(
        val lessons: List<TimetableSnapshot>,
        val lastCompleteAt: Instant?,
    ) : LocalTimetableResult
}

class LocalTimetableReader @Inject constructor(
    private val store: SnapshotStore,
    private val identity: LocalSnapshotIdentity,
    private val json: Json,
    private val clock: Clock,
) {
    suspend fun read(date: LocalDate = LocalDate.now(clock)): LocalTimetableResult {
        val accountId = identity.currentAccountId() ?: return LocalTimetableResult.NoBaseline
        val studentId = identity.selectedStudentScopeId() ?: return LocalTimetableResult.NoBaseline
        val week = WeekRange.fromDate(date)
        val schoolYear = currentSchoolYearRange(date)
        if (date.isBefore(schoolYear.start) || date.isAfter(schoolYear.end) ||
            !week.intersectsSchoolYear(schoolYear)) return LocalTimetableResult.NoBaseline

        val range = "${week.clampedSchoolWeekStart(schoolYear)}/${week.clampedSchoolWeekEnd(schoolYear)}"
        val scope = SnapshotScope(accountId, studentId, SnapshotArea.TIMETABLE, range)
        val saved = store.read(scope) ?: return LocalTimetableResult.NoBaseline
        if (identity.currentAccountId() != accountId || identity.selectedStudentScopeId() != studentId) {
            return LocalTimetableResult.NoBaseline
        }
        val snapshot = json.decodeFromString<SnapshotPayload>(saved) as SnapshotPayload.Timetable
        return LocalTimetableResult.Available(
            lessons = snapshot.items.filter { it.slot.date == date }.sortedBy { it.slot.start },
            lastCompleteAt = snapshot.lastCompleteAtEpochMillis?.let(Instant::ofEpochMilli),
        )
    }
}
