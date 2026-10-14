package pl.usagi.edziennik.background

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.changes.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class OutboxEventSerializationTest {
    @Test fun allChangeEventsRoundTripWithNotificationTextAndTargetUnchanged() {
        val date = LocalDate.of(2026, 10, 12)
        val slot = TimetableSlot(date, LocalTime.of(8, 0), LocalTime.of(8, 45))
        val events = listOf(
            ChangeEvent.NewMessage(1, "Temat", date.atStartOfDay(), "Nadawca"),
            ChangeEvent.NewGrade(1, 2, "Matematyka", 3, "5", date),
            ChangeEvent.EditedGrade(1, 2, "Matematyka", 3, "4", "5", date),
            ChangeEvent.LessonCancelled(slot, "Matematyka"),
            ChangeEvent.LessonSubstituted(slot, "Matematyka", "Nauczyciel"),
            ChangeEvent.LessonUpdated(slot, "Matematyka", "Zmiana sali: 106"),
            ChangeEvent.AttendanceChanged(AttendanceKey(date, 1, null), null, AttendanceStatus.Absent, true, slot.start),
            ChangeEvent.AttendanceChanged(AttendanceKey(date, 1, 2), "Matematyka", AttendanceStatus.Unknown(999), false),
            ChangeEvent.TaskChanged(TaskKey(4, 7), date, "Matematyka", true, "Opis"),
            ChangeEvent.NewInformation(date, "Informacja"),
            ChangeEvent.PaymentChanged("Nadpłata", BigDecimal("6.00")),
            ChangeEvent.PaymentChanged(null, null),
        )
        events.forEach { event ->
            val restored = Json.decodeFromString<ChangeEvent>(Json.encodeToString(event))
            assertEquals(event, restored)
            assertEquals(event.notificationSpec(), restored.notificationSpec())
        }
    }
}
