package pl.usagi.edziennik.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.MainActivity
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.persistence.changes.LocalTask
import pl.usagi.edziennik.persistence.changes.LocalTasksResult
import pl.usagi.edziennik.persistence.changes.LocalTaskWeek
import pl.usagi.edziennik.persistence.changes.TaskRangeCoverage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UpcomingTasksPresentationTest {
    private val date = LocalDate.of(2026, 10, 12)
    private val zone = ZoneId.of("Europe/Warsaw")
    private val timestamp = Instant.parse("2026-10-12T08:00:00Z")

    @Test fun sortsChronologicallyIncludingTodayAndExcludesOutsideWindow() {
        val rows = present(result(listOf(task(date.plusDays(2), id = 2), task(date.minusDays(1)),
            task(date.plusDays(14), id = 3), task(date)))).items
        assertEquals(listOf(date, date.plusDays(2)), rows.map { it.target.date })
    }

    @Test fun testTypeUsesConfirmedLabel() { assertEquals("Sprawdzian", present(result(listOf(task(type = 1)))).items.single().typeLabel) }
    @Test fun quizTypeUsesConfirmedLabel() { assertEquals("Kartkówka", present(result(listOf(task(type = 2)))).items.single().typeLabel) }
    @Test fun homeworkTypeUsesConfirmedLabel() { assertEquals("Zadanie domowe", present(result(listOf(task(type = 4)))).items.single().typeLabel) }
    @Test fun unknownTypeIsShownWithoutTechnicalCodeOrGuessedMeaning() {
        assertEquals("Inne", present(result(listOf(task(type = 999)))).items.single().typeLabel)
    }

    @Test fun missingOrBlankSubjectUsesExistingFallback() {
        for (subject in listOf(null, " \n ")) {
            assertEquals("Bez przedmiotu", present(result(listOf(task().copy(subject = subject)))).items.single().subject)
        }
    }

    @Test fun missingDescriptionRemainsAbsent() {
        assertNull(present(result(listOf(task().copy(description = null)))).items.single().description)
        assertNull(shortWidgetTaskDescription(" \n\t "))
    }

    @Test fun descriptionsNormalizeWhitespaceIncludingNonbreakingSpaces() {
        assertEquals("Rozdzial 1 i 2", shortWidgetTaskDescription(" \nRozdzial\t1\u00a0i\n2 "))
    }

    @Test fun descriptionsAreLimitedToOneHundredCharactersAndEllipsis() {
        assertEquals("a".repeat(100) + "...", shortWidgetTaskDescription("a".repeat(200)))
        assertEquals("a".repeat(100), shortWidgetTaskDescription("a".repeat(100)))
    }

    @Test fun fullNonemptyResultDoesNotShowPartialWarning() {
        val shown = present(result(listOf(task())))
        assertNull(shown.message)
        assertEquals("Zsynchronizowano 12 paź, 10:00", shown.lastCompleteLabel)
        assertEquals("12 paź - 25 paź", shown.rangeLabel)
    }

    @Test fun fullEmptyResultShowsConfirmedEmptyWindow() {
        assertEquals("Brak sprawdzianów i zadań w najbliższych 14 dniach.", present(result()).message)
    }

    @Test fun partialResultAlwaysWarnsAndQualifiesKnownTimestamp() {
        val shown = present(result(listOf(task()), TaskRangeCoverage.PARTIAL))
        assertEquals("Niepełne dane dla najbliższych 14 dni.", shown.message)
        assertEquals("Zapis części danych: 12 paź, 10:00", shown.lastCompleteLabel)
    }

    @Test fun emptyPartialResultDoesNotClaimNoUpcomingTasks() {
        assertEquals("Niepełne dane. Brak zadań w dostępnych zapisach.", present(result(coverage = TaskRangeCoverage.PARTIAL)).message)
    }

    @Test fun noBaselineShowsNeutralMessageAndNoTimestamp() {
        val shown = present(result(coverage = TaskRangeCoverage.NO_BASELINE))
        assertEquals("Sprawdziany i zadania nie zostały jeszcze zsynchronizowane.", shown.message)
        assertNull(shown.lastCompleteLabel)
        assertTrue(shown.items.isEmpty())
    }

    @Test fun missingCompleteTimeIsNotInvented() {
        assertNull(present(result().copy(weeks = listOf(week(null)))).lastCompleteLabel)
        assertNull(present(result(coverage = TaskRangeCoverage.PARTIAL).copy(weeks = listOf(week(null)))).lastCompleteLabel)
    }

    @Test fun fullCoverageWithOneUnknownWeekTimeDoesNotClaimKnownWindowUpdate() {
        assertNull(present(result().copy(weeks = listOf(week(timestamp), week(null)))).lastCompleteLabel)
    }

    @Test fun oldestCompleteTimeConservativelyRepresentsFullRange() {
        val shown = present(result().copy(weeks = listOf(week(timestamp.plusSeconds(3600)), week(timestamp))))
        assertEquals("Zsynchronizowano 12 paź, 10:00", shown.lastCompleteLabel)
    }

    @Test fun taskNavigationUsesStableKeyAndDate() {
        val target = present(result(listOf(task(type = 2, id = 42)))).items.single().target
        assertEquals("schoolTasks?taskId=42&taskType=2&taskDate=2026-10-12", target.route())
        assertTrue(MainActivity.isAllowedRoute(target.route()))
    }

    @Test fun moduleAndUnknownTaskRoutesAreAcceptedWithoutRelaxingOtherRoutes() {
        assertTrue(MainActivity.isAllowedRoute("schoolTasks"))
        assertTrue(MainActivity.isAllowedRoute(present(result(listOf(task(type = -1)))).items.single().target.route()))
        assertFalse(MainActivity.isAllowedRoute("schoolTasks?taskId=bad"))
    }

    @Test fun taskCapacityIsBoundedAndAccountsForMessagesAndFontSize() {
        assertEquals(4, visibleTaskCount(1000f, 1f, false, false))
        assertEquals(0, visibleTaskCount(100f, 1f, true, true))
        assertEquals(2, visibleTaskCount(280f, 1f, false, false))
        assertTrue(visibleTaskCount(300f, 1f, false, false) > visibleTaskCount(300f, 1f, true, true))
        assertTrue(visibleTaskCount(400f, 1f, false, false) > visibleTaskCount(400f, 1.5f, false, false))
    }

    @Test fun yearCrossingRangeShowsBothYears() {
        val shown = present(result().copy(from = LocalDate.of(2026, 12, 28), to = LocalDate.of(2027, 1, 10)))
        assertEquals("28 gru 2026 - 10 sty 2027", shown.rangeLabel)
    }

    private fun present(result: LocalTasksResult) = presentUpcomingTasks(result, zone)
    private fun week(time: Instant?) = LocalTaskWeek(date, date.plusDays(6), TaskRangeCoverage.FULL, time)
    private fun result(items: List<LocalTask> = emptyList(), coverage: TaskRangeCoverage = TaskRangeCoverage.FULL) =
        LocalTasksResult(date, date.plusDays(13), items, coverage,
            listOf(LocalTaskWeek(date, date.plusDays(6), coverage, timestamp)))
    private fun task(day: LocalDate = date, type: Int = 4, id: Int = 1) =
        LocalTask(day, TaskKey(type, id), SchoolTaskType.fromRawValue(type), "Matematyka", "Opis")
}
