package pl.katcz.edziennik.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveStudentManagerTest {
    private val firstStudent = Student(
        key = "first-key",
        diaryId = 1,
        name = "Jan Kowalski",
        className = "4A",
        schoolName = "Szkola",
    )

    private val secondStudent = Student(
        key = "second-key",
        diaryId = 2,
        name = "Anna Nowak",
        className = "2B",
        schoolName = "Szkola",
    )

    @Test
    fun singleStudentIsSelectedAutomatically() {
        val manager = ActiveStudentManager()

        manager.initialize(listOf(firstStudent))

        assertEquals(firstStudent, manager.activeStudent.value)
    }

    @Test
    fun multipleStudentsAreNotSelectedAutomatically() {
        val manager = ActiveStudentManager()

        manager.initialize(listOf(firstStudent, secondStudent))

        assertNull(manager.activeStudent.value)
    }

    @Test
    fun activeStudentCanBeChangedManually() {
        val manager = ActiveStudentManager()

        manager.initialize(listOf(firstStudent))
        manager.setActiveStudent(secondStudent)

        assertEquals(secondStudent, manager.activeStudent.value)
    }

    @Test
    fun clearRemovesActiveStudentOnLogout() {
        val manager = ActiveStudentManager()

        manager.initialize(listOf(firstStudent))
        manager.clear()

        assertNull(manager.activeStudent.value)
    }
}
