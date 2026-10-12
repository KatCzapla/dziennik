package pl.usagi.edziennik.widget

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.clampedSchoolWeekEnd
import pl.usagi.edziennik.domain.time.clampedSchoolWeekStart
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.persistence.changes.LocalIdentityStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.LocalTimetableReader
import pl.usagi.edziennik.persistence.changes.SnapshotStore
import pl.usagi.edziennik.persistence.changes.SnapshotSyncManager
import pl.usagi.edziennik.persistence.changes.SnapshotWrite
import pl.usagi.edziennik.persistence.changes.SyncCompleteness
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

class TodayTimetableContentReaderTest {
    private val monday = LocalDate.of(2026, 10, 12)
    private val zone = ZoneId.of("Europe/Warsaw")
    private val clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), zone)
    private val identityStore = MemoryIdentityStore()
    private val identity = LocalSnapshotIdentity(identityStore).apply { ensureAccountId(); selectStudent(17) }
    private val store = MemorySnapshotStore()
    private val contentReader = reader()

    @Test fun midnightReadsNextDayInsteadOfCapturedPreviousDate() = runTest {
        saveWeek(monday, listOf(lesson(monday, "Poniedzialek"), lesson(monday.plusDays(1), "Wtorek")))
        val before = monday.atTime(23, 59).atZone(zone).toInstant()
        assertEquals("Poniedzialek", contentReader.read(before, zone).lessons.single().subject)
        val after = contentReader.read(before.plusSeconds(60), zone)
        assertEquals("Wtorek", after.lessons.single().subject)
        assertEquals("13 października 2026", after.dateLabel)
    }

    @Test fun nextWeekReadsItsOwnScope() = runTest {
        saveWeek(monday, listOf(lesson(monday, "Poprzedni tydzien")))
        val nextMonday = monday.plusWeeks(1)
        saveWeek(nextMonday, listOf(lesson(nextMonday, "Nowy tydzien")))
        val result = contentReader.read(atNoon(nextMonday), zone)
        assertEquals("Nowy tydzien", result.lessons.single().subject)
        assertEquals("2026-10-19/2026-10-23", store.lastReadScope!!.range)
    }

    @Test fun nextWeekWithoutBaselineNeverDisplaysOldWeek() = runTest {
        saveWeek(monday, listOf(lesson(monday, "Stary plan")))
        val result = contentReader.read(atNoon(monday.plusWeeks(1)), zone)
        assertEquals("19 października 2026", result.dateLabel)
        assertEquals("Plan nie został jeszcze zsynchronizowany.", result.message)
        assertTrue(result.lessons.isEmpty())
        assertNull(result.lastCompleteLabel)
    }

    @Test fun weekendUsesBaselineAndShowsEmptyDay() = runTest {
        saveWeek(monday, listOf(lesson(monday.plusDays(4), "Piatek")))
        for (date in listOf(monday.plusDays(5), monday.plusDays(6))) {
            val result = contentReader.read(atNoon(date), zone)
            assertEquals("Dzisiaj nie ma lekcji.", result.message)
            assertTrue(result.lessons.isEmpty())
            assertEquals("12 paź, 12:00", result.lastCompleteLabel)
        }
    }

    @Test fun completeEmptyWeekIsNotMissingData() = runTest {
        saveWeek(monday, emptyList())
        assertEquals("Dzisiaj nie ma lekcji.", contentReader.read(atNoon(monday), zone).message)
    }

    @Test fun changingZoneChangesDateAndSelectedLessonsAtSameInstant() = runTest {
        saveWeek(monday, listOf(lesson(monday, "Poniedzialek"), lesson(monday.plusDays(1), "Wtorek")))
        val instant = Instant.parse("2026-10-13T00:30:00Z")
        assertEquals("Wtorek", contentReader.read(instant, zone).lessons.single().subject)
        val west = contentReader.read(instant, ZoneId.of("America/Los_Angeles"))
        assertEquals("Poniedzialek", west.lessons.single().subject)
        assertEquals("12 października 2026", west.dateLabel)
    }

    @Test fun defaultZoneIsEvaluatedForEveryRead() = runTest {
        saveWeek(monday, listOf(lesson(monday, "Poniedzialek"), lesson(monday.plusDays(1), "Wtorek")))
        val previousZone = TimeZone.getDefault()
        val instant = Instant.parse("2026-10-13T00:30:00Z")
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"))
            assertEquals("Wtorek", contentReader.read(instant).lessons.single().subject)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals("Poniedzialek", contentReader.read(instant).lessons.single().subject)
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }

    @Test fun recreatedReaderUsesPersistedScopeAndCurrentDayWithoutHttp() = runTest {
        saveWeek(monday, listOf(lesson(monday, "Poniedzialek"), lesson(monday.plusDays(1), "Wtorek")))
        contentReader.read(atNoon(monday), zone)
        val restored = reader(LocalSnapshotIdentity(identityStore))
        val result = restored.read(atNoon(monday.plusDays(1)), zone)
        assertEquals("Wtorek", result.lessons.single().subject)
        assertEquals(identity.currentAccountId(), store.lastReadScope!!.accountId)
        assertEquals(identity.selectedStudentScopeId(), store.lastReadScope!!.studentScopeId)
    }

    @Test fun failedSyncDoesNotChangePlanOrLastCompleteTime() = runTest {
        saveWeek(monday, listOf(lesson(monday, "Matematyka")))
        val before = contentReader.read(atNoon(monday), zone)
        SnapshotSyncManager(store, Json, identity, clock).sync(scope(monday), SyncCompleteness.Failed)
        assertEquals(before, contentReader.read(atNoon(monday), zone))
    }

    private fun reader(localIdentity: LocalSnapshotIdentity = identity) =
        TodayTimetableContentReader(LocalTimetableReader(store, localIdentity, Json, clock))

    private fun atNoon(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant()

    private fun scope(date: LocalDate): SnapshotScope {
        val week = WeekRange.fromDate(date)
        val year = currentSchoolYearRange(date)
        return SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
            SnapshotArea.TIMETABLE, "${week.clampedSchoolWeekStart(year)}/${week.clampedSchoolWeekEnd(year)}")
    }

    private suspend fun saveWeek(date: LocalDate, entries: List<TimetableEntry>) {
        SnapshotSyncManager(store, Json, identity, clock).saveCompleteTimetable(scope(date), entries)
    }

    private fun lesson(date: LocalDate, subject: String) = TimetableEntry(date, null, LocalTime.of(9, 0),
        LocalTime.of(9, 45), subject, null, room = "215", changes = emptyList())

    private class MemoryIdentityStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }

    private class MemorySnapshotStore : SnapshotStore {
        private val payloads = mutableMapOf<String, String>()
        var lastReadScope: SnapshotScope? = null
        override suspend fun read(scope: SnapshotScope): String? {
            lastReadScope = scope
            return payloads[scope.storageKey]
        }
        override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> {
            val write = transform(payloads[scope.storageKey])
            write.payload?.let { payloads[scope.storageKey] = it }
            return write.events
        }
        override suspend fun deleteAccount(accountId: LocalAccountId) {
            payloads.keys.removeAll { it.startsWith("${accountId.value}|") }
        }
    }
}
