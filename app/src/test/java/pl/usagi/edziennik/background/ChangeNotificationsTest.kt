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

    @Test fun messageAndGradeTextsFollowContract() {
        val cases = listOf(
            Triple(ChangeEvent.NewMessage(1, "Wywiadówka", date.atStartOfDay(), "Anna Nowak"),
                "Nowa wiadomość od Anna Nowak", "Wywiadówka"),
            Triple(ChangeEvent.NewGrade(1, 2, "Matematyka", 3, "5", date),
                "Nowa ocena", "5, Matematyka"),
            Triple(ChangeEvent.EditedGrade(1, 2, "Matematyka", 3, "4", "5", date),
                "Edycja oceny", "5, Matematyka"),
        )
        cases.forEach { (event, title, body) ->
            assertEquals(title, event.notificationSpec().title)
            assertEquals(body, event.notificationSpec().text)
        }
    }

    @Test fun lessonAndAttendanceTextsUsePolishDateAndTime() {
        val cases = listOf(
            Triple(ChangeEvent.LessonCancelled(slot, "Matematyka"), "Odwołana lekcja 30 wrz 2026", "Matematyka"),
            Triple(ChangeEvent.LessonSubstituted(slot, "Matematyka", "Jan Kowalski"),
                "Zastępstwo 30 wrz 2026", "Matematyka, Jan Kowalski"),
            Triple(ChangeEvent.LessonUpdated(slot, "Matematyka", "Zmiana sali: 107"),
                "Zmiana planu 30 wrz 2026", "Matematyka, Zmiana sali: 107"),
            Triple(ChangeEvent.AttendanceChanged(AttendanceKey(date, 1, null), "Matematyka",
                AttendanceStatus.Late, true, LocalTime.of(9, 5)), "Frekwencja", "Spóźnienie, 09:05 Matematyka"),
        )
        cases.forEach { (event, title, body) ->
            assertEquals(title, event.notificationSpec().title)
            assertEquals(body, event.notificationSpec().text)
        }
    }

    @Test fun everyKnownTaskTypeHasNewAndChangedText() {
        val types = listOf(
            1 to Pair("Sprawdzian", "Zmiana sprawdzianu"),
            2 to Pair("Kartkówka", "Zmiana kartkówki"),
            4 to Pair("Zadanie domowe", "Zmiana zadania domowego"),
            999 to Pair("Sprawdziany i zadania", "Sprawdziany i zadania"),
        )
        types.forEach { (rawType, titles) ->
            listOf(true to titles.first, false to titles.second).forEach { (isNew, prefix) ->
                val spec = ChangeEvent.TaskChanged(TaskKey(rawType, 42), date, "Historia", isNew,
                    "Rozdział 3").notificationSpec()
                assertEquals("$prefix 30 wrz 2026", spec.title)
                assertEquals("Historia, Rozdział 3", spec.text)
            }
        }
    }

    @Test fun informationAndPaymentTextsFollowContract() {
        assertEquals(NotificationSpec("Nowa informacja", "Zebranie", "home"),
            ChangeEvent.NewInformation(date, "Zebranie").notificationSpec())
        val payment = ChangeEvent.PaymentChanged("Nadpłata", BigDecimal("6.00")).notificationSpec()
        assertEquals("Zmiana w opłatach", payment.title)
        assertEquals("Nadpłata: 6,00 zł", payment.text)
    }

    @Test fun missingOptionalFieldsLeaveNoDanglingPunctuation() {
        assertEquals("Nowa wiadomość", ChangeEvent.NewMessage(1, "Temat", date.atStartOfDay(), null)
            .notificationSpec().title)
        assertEquals("Zastępstwo 30 wrz 2026", ChangeEvent.LessonSubstituted(slot, "", null)
            .notificationSpec().title)
        assertEquals("Matematyka", ChangeEvent.LessonSubstituted(slot, "Matematyka", null)
            .notificationSpec().text)
        assertEquals("Matematyka", ChangeEvent.LessonUpdated(slot, "Matematyka", null)
            .notificationSpec().text)
        assertEquals("5", ChangeEvent.NewGrade(1, 2, "", 3, "5", date).notificationSpec().text)
        assertEquals("Nieobecność", ChangeEvent.AttendanceChanged(AttendanceKey(date, 1, null), null,
            AttendanceStatus.Absent, true).notificationSpec().text)
        assertEquals("09:05", ChangeEvent.AttendanceChanged(AttendanceKey(date, 1, null), null,
            AttendanceStatus.Absent, true, LocalTime.of(9, 5)).notificationSpec().text.substringAfter(", "))
        assertEquals("Historia", ChangeEvent.TaskChanged(TaskKey(4, 42), date, "Historia", true)
            .notificationSpec().text)
        assertEquals("Opis", ChangeEvent.TaskChanged(TaskKey(4, 42), date, null, true, "Opis")
            .notificationSpec().text)
        assertEquals("6,00 zł", ChangeEvent.PaymentChanged(null, BigDecimal("6.00")).notificationSpec().text)
        assertEquals("Nadpłata", ChangeEvent.PaymentChanged("Nadpłata", null).notificationSpec().text)
    }

    @Test fun taskDescriptionWhitespaceIsNormalizedAndLengthIsLimited() {
        val normalized = ChangeEvent.TaskChanged(TaskKey(4, 42), date, "Historia", true,
            "  Pierwsza  linia\n\tDruga   linia  ").notificationSpec()
        assertEquals("Historia, Pierwsza linia Druga linia", normalized.text)
        val long = ChangeEvent.TaskChanged(TaskKey(4, 42), date, null, true, "a".repeat(120)).notificationSpec()
        assertEquals("a".repeat(100) + "...", long.text)
    }

    @Test fun allAttendanceStatusesHaveReadableLabels() {
        val cases = listOf(
            AttendanceStatus.Present to "Obecność",
            AttendanceStatus.Absent to "Nieobecność",
            AttendanceStatus.ExcusedAbsence to "Nieobecność usprawiedliwiona",
            AttendanceStatus.Late to "Spóźnienie",
            AttendanceStatus.ExcusedLate to "Spóźnienie usprawiedliwione",
            AttendanceStatus.SchoolRelatedAbsence to "Nieobecność z przyczyn szkolnych",
            AttendanceStatus.Released to "Zwolnienie",
            AttendanceStatus.Unknown(999) to "Nowy status frekwencji",
        )
        cases.forEach { (status, label) ->
            val spec = ChangeEvent.AttendanceChanged(AttendanceKey(date, 1, null), "Matematyka",
                status, true, LocalTime.of(8, 0)).notificationSpec()
            assertEquals("$label, 08:00 Matematyka", spec.text)
        }
    }

    @Test fun dateUsesPolishShortMonthOnSingleDigitDay() {
        val october = TimetableSlot(LocalDate.of(2026, 10, 1), LocalTime.of(8, 0), LocalTime.of(8, 45))
        assertEquals("Odwołana lekcja 1 paź 2026",
            ChangeEvent.LessonCancelled(october, "Matematyka").notificationSpec().title)
    }
}
