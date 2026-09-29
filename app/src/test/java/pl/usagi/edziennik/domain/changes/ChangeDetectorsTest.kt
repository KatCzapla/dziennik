package pl.usagi.edziennik.domain.changes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.BankAccountPayment
import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.GradeColumn
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.GradesSettings
import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.SubjectGrades
import pl.usagi.edziennik.domain.TimetableChange
import pl.usagi.edziennik.domain.TimetableChangeKind
import pl.usagi.edziennik.domain.TimetableEntry
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class ChangeDetectorsTest {
    private val day = LocalDate.of(2026, 9, 29)

    @Test fun identicalMessagesAndReorderingDoNotNotify() {
        val messages = listOf(message(1), message(2))
        assertTrue(detectMessages(messages.messageSnapshots(), messages.reversed()).isEmpty())
    }

    @Test fun newMessageNotifies() {
        assertEquals(listOf(ChangeEvent.NewMessage(2, "Temat 2", day.atStartOfDay())),
            detectMessages(listOf(message(1)).messageSnapshots(), listOf(message(2), message(1))))
    }

    @Test fun newGradeNotifies() {
        val old = grades(grade("4"))
        val events = detectGrades(old.gradeSnapshots(3), grades(grade("4"), grade("5")), 3)
        assertEquals(1, events.size)
        assertTrue(events.single() is ChangeEvent.NewGrade)
    }

    @Test fun repeatedIdenticalGradesUseMultiplicity() {
        val old = grades(grade("4"), grade("4"))
        assertTrue(detectGrades(old.gradeSnapshots(3), grades(grade("4"), grade("4")), 3).isEmpty())
        assertEquals(1, detectGrades(old.gradeSnapshots(3), grades(grade("4"), grade("4"), grade("4")), 3).size)
    }

    @Test fun unambiguousFlaggedGradeEditNotifies() {
        val events = detectGrades(grades(grade("4")).gradeSnapshots(3), grades(grade("5", changed = true)), 3)
        assertTrue(events.single() is ChangeEvent.EditedGrade)
    }

    @Test fun flagAloneDoesNotNotify() {
        assertTrue(detectGrades(grades(grade("4")).gradeSnapshots(3), grades(grade("4", changed = true)), 3).isEmpty())
    }

    @Test fun ambiguousGradeChangeDoesNotNotify() {
        val old = grades(grade("4"), grade("4"))
        assertTrue(detectGrades(old.gradeSnapshots(3), grades(grade("4"), grade("5", changed = true)), 3).isEmpty())
        assertTrue(detectGrades(grades(grade("4")).gradeSnapshots(3), grades(grade("5")), 3).isEmpty())
    }

    @Test fun gradeReorderingDoesNotNotify() {
        val first = grade("4")
        val second = grade("5")
        assertTrue(detectGrades(grades(first, second).gradeSnapshots(3), grades(second, first), 3).isEmpty())
    }

    @Test fun cancellationNotifies() {
        val event = detectTimetable(listOf(lesson()).timetableSnapshots(), listOf(lesson(cancelledChange()))).single()
        assertTrue(event is ChangeEvent.LessonCancelled)
    }

    @Test fun substitutionNotifies() {
        val event = detectTimetable(listOf(lesson()).timetableSnapshots(), listOf(lesson(substitutionChange()))).single()
        assertTrue(event is ChangeEvent.LessonSubstituted)
    }

    @Test fun knownTeacherChangeNotifies() {
        val event = detectTimetable(listOf(lesson()).timetableSnapshots(), listOf(lesson().copy(teacher = "Nowy"))).single()
        assertTrue(event is ChangeEvent.LessonUpdated)
    }

    @Test fun unknownChangeCodeAndReorderingDoNotNotify() {
        val unknown = change(999, TimetableChangeKind.Unknown(999))
        assertTrue(detectTimetable(listOf(lesson()).timetableSnapshots(), listOf(lesson(unknown))).isEmpty())
        val old = listOf(lesson(), lesson().copy(startTime = LocalTime.of(10, 0)))
        assertTrue(detectTimetable(old.timetableSnapshots(), old.reversed()).isEmpty())
    }

    @Test fun ambiguousTimetableSlotDoesNotNotify() {
        val old = listOf(lesson(), lesson().copy(subject = "Inny"))
        assertTrue(detectTimetable(old.timetableSnapshots(), listOf(lesson(cancelledChange()))).isEmpty())
    }

    @Test fun newAbsenceAndLatenessNotify() {
        assertEquals(1, detectAttendance(emptyList(), listOf(attendance(AttendanceStatus.Absent))).size)
        assertEquals(1, detectAttendance(emptyList(), listOf(attendance(AttendanceStatus.Late))).size)
    }

    @Test fun presenceNeverNotifiesButStatusIsInCurrentSnapshot() {
        val old = listOf(attendance(AttendanceStatus.Absent)).attendanceSnapshots()
        val current = listOf(attendance(AttendanceStatus.Present))
        assertTrue(detectAttendance(emptyList(), current).isEmpty())
        assertTrue(detectAttendance(old, current).isEmpty())
        assertEquals(AttendanceStatus.Present, current.attendanceSnapshots().single().status)
    }

    @Test fun changedAbsenceStatusNotifies() {
        val old = listOf(attendance(AttendanceStatus.Absent)).attendanceSnapshots()
        assertEquals(1, detectAttendance(old, listOf(attendance(AttendanceStatus.ExcusedAbsence))).size)
    }

    @Test fun newAndChangedTaskNotify() {
        val original = task()
        assertTrue(detectTasks(emptyList(), listOf(original)).single() is ChangeEvent.TaskChanged)
        assertEquals(1, detectTasks(listOf(original).taskSnapshots(), listOf(original.copy(date = day.plusDays(1)))).size)
    }

    @Test fun missingTaskDetailsDoNotLookLikeDeletion() {
        val old = listOf(task().copy(details = SchoolTaskDetails("Opis", null, null))).taskSnapshots()
        assertTrue(detectTasks(old, listOf(task())).isEmpty())
    }

    @Test fun newUniqueInformationNotifiesButDuplicateDoesNot() {
        val info = DashboardInformation("Temat", "Treść", day)
        assertEquals(1, detectInformation(emptyList(), listOf(info)).size)
        assertTrue(detectInformation(emptyList(), listOf(info, info)).isEmpty())
        assertTrue(detectInformation(listOf(info).informationSnapshots(), listOf(info.copy(content = "Nowa"))).isEmpty())
    }

    @Test fun operationNameChangeNotifies() {
        val old = payments("Nadpłata", "6.00")
        assertEquals(1, detectPayments(old.toSnapshot(), payments("Do zapłaty", "6.00")).size)
    }

    @Test fun amountChangeNotifies() {
        val old = payments("Nadpłata", "6.00")
        assertEquals(1, detectPayments(old.toSnapshot(), payments("Nadpłata", "7.00")).size)
    }

    @Test fun amountScaleIsNotAChangeAndEventHasNoAccountNumber() {
        val old = payments("Nadpłata", "6.0").copy(
            bankAccounts = listOf(account("Nadpłata", "6.0").copy(accountNumber = "123456789")),
        )
        assertTrue(detectPayments(old.toSnapshot(), payments("Nadpłata", "6.00")).isEmpty())
        val event = detectPayments(old.toSnapshot(), payments("Inna", "6.00")).single()
        assertTrue(event.toString().contains("Inna"))
        assertTrue(!event.toString().contains("123456789"))
    }

    @Test fun otherPaymentDataAndAccountOrderDoNotNotify() {
        val old = payments("Nadpłata", "6.00")
        val changed = old.copy(bankAccounts = listOf(old.bankAccounts.single().copy(paymentTitle = "Inny", status = 999)))
        assertTrue(detectPayments(old.toSnapshot(), changed).isEmpty())
        assertTrue(detectPayments(PaymentsSnapshot(listOf(PaymentAccountSnapshot("A", BigDecimal.ONE),
            PaymentAccountSnapshot("B", BigDecimal.TEN))), Payments(bankAccounts = listOf(
            account("B", "10"), account("A", "1")))).isEmpty())
    }

    private fun message(id: Int) = ReceivedMessage(id, "session-key", "Nadawca", "Temat $id", day.atStartOfDay(), true, false)

    private fun grade(value: String, changed: Boolean = false) = Grade(
        value, day, 17, "Kartkówka", "Kolumna", BigDecimal.ONE, null, null, changed, null,
    )

    private fun grades(vararg entries: Grade) = Grades(
        subjects = listOf(SubjectGrades(
            subjectName = "Matematyka", position = 1, includeGradeWeight = true, teachers = emptyList(),
            columns = listOf(GradeColumn(17, "Kartkówka", "Kolumna", entries.toList())),
            practicalExamGrade = null, oralExamGrade = null, proposedExamGrade = null, finalExamGrade = null,
            pointsSum = null, pointsSumAllTerms = null, average = null, averageAllTerms = null,
            proposedTermGrade = null, proposedTermPoints = null, termGrade = null, termPoints = null,
            gradesSummary = null,
        )),
        settings = GradesSettings(false, false, false, false),
    )

    private fun lesson(vararg changes: TimetableChange) = TimetableEntry(
        day, null, LocalTime.of(9, 0), LocalTime.of(9, 45), "Matematyka", "Nauczyciel", null, null, "106", changes.toList(),
    )

    private fun change(type: Int, kind: TimetableChangeKind) = TimetableChange(
        type, kind, null, null, null, null, null, null, null, null, null, null,
    )

    private fun cancelledChange() = change(4, TimetableChangeKind.Cancelled)
    private fun substitutionChange() = change(7, TimetableChangeKind.Substitution).copy(teacher = "Zastępca")

    private fun attendance(status: AttendanceStatus) = AttendanceEntry(
        4, 8, 2, status, day, LocalTime.of(9, 0), LocalTime.of(9, 45), "Matematyka", null,
    )

    private fun task() = SchoolTask(SchoolTaskType.Homework, 4, day, "Matematyka", false, 18)

    private fun account(operation: String, amount: String) = BankAccountPayment(
        null, null, operation, BigDecimal(amount), null, null, null, null,
    )

    private fun payments(operation: String, amount: String) = Payments(bankAccounts = listOf(account(operation, amount)))
}
