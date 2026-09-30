package pl.usagi.edziennik.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.MainActivity
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.changes.AttendanceKey
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.domain.changes.TimetableSlot
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ChangeNotificationsTest {
    private val date = LocalDate.of(2026, 9, 30)
    private val slot = TimetableSlot(date, LocalTime.of(8, 0), LocalTime.of(8, 45))

    @Test fun mapsEveryEventToItsModule() {
        val cases = listOf(
            ChangeEvent.NewMessage(1, "Temat", LocalDateTime.of(2026, 9, 30, 8, 0)) to "messages",
            ChangeEvent.NewGrade(1, 2, "Matematyka", 3, "5", date) to "grades",
            ChangeEvent.EditedGrade(1, 2, "Matematyka", 3, "4", "5", date) to "grades",
            ChangeEvent.LessonCancelled(slot, "Matematyka") to "timetable",
            ChangeEvent.LessonSubstituted(slot, "Matematyka") to "timetable",
            ChangeEvent.LessonUpdated(slot, "Matematyka") to "timetable",
            ChangeEvent.AttendanceChanged(AttendanceKey(date, 1, null), "Matematyka", AttendanceStatus.Absent, true) to "attendance",
            ChangeEvent.TaskChanged(TaskKey(2, 42), date, "Matematyka", true) to
                "schoolTasks?taskId=42&taskType=2&taskDate=2026-09-30",
            ChangeEvent.NewInformation(date, "Zebranie") to "home",
            ChangeEvent.PaymentChanged("Nadpłata", BigDecimal("6.00")) to "payments",
        )
        cases.forEach { (event, route) ->
            assertEquals(route, event.notificationSpec().route)
            assertTrue(event.notificationSpec().title.isNotBlank())
            assertTrue(MainActivity.isAllowedRoute(route))
        }
    }

    @Test fun doesNotExposeTechnicalIdsInNotificationText() {
        val spec = ChangeEvent.TaskChanged(TaskKey(2, 99999), date, "Historia", false).notificationSpec()
        assertFalse(spec.title.contains("99999"))
        assertFalse(spec.text.contains("99999"))
    }

    @Test fun rejectsUntrustedNavigationRoute() {
        assertFalse(MainActivity.isAllowedRoute("grades/subject/1?gradeIndex=2"))
        assertFalse(MainActivity.isAllowedRoute("schoolTasks?taskId=bad"))
    }
}
