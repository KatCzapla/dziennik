package pl.usagi.edziennik.ui.navigation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NavigationTargetsTest {
    @Test
    fun homeworkAndTestTargetsKeepIdsTypesAndDatesInRoutes() {
        val date = LocalDate.of(2026, 9, 18)
        assertEquals("schoolTasks?taskId=72&taskType=4&taskDate=2026-09-18", SchoolTaskNavigationTarget(72, 4, date).route())
        assertEquals("schoolTasks?taskId=72&taskType=2&taskDate=2026-09-18", SchoolTaskNavigationTarget(72, 2, date).route())
        val handle = SavedStateHandle(mapOf("taskId" to "72", "taskType" to "4", "taskDate" to date.toString()))
        assertEquals(SchoolTaskNavigationTarget(72, 4, date), SchoolTaskNavigationTarget.from(handle))
    }

    @Test
    fun gradeTargetUsesNumericLocationNotDisplayedText() {
        val target = GradeNavigationTarget(123, 7, 81, 2)
        assertEquals("grades?periodId=123&subjectPosition=7&columnId=81&gradeIndex=2", target.route())
        assertEquals("grades/subject/7?columnId=81&gradeIndex=2", target.subjectRoute())
        assertEquals(target, GradeNavigationTarget.from(SavedStateHandle(mapOf(
            "periodId" to "123", "subjectPosition" to "7", "columnId" to "81", "gradeIndex" to "2",
        ))))
    }

    @Test
    fun ordinaryEntryWithoutArgumentsHasNoTarget() {
        assertNull(GradeNavigationTarget.from(SavedStateHandle()))
        assertNull(SchoolTaskNavigationTarget.from(SavedStateHandle()))
    }

    @Test
    fun incompleteAndInvalidArgumentsDoNotSelectAnElement() {
        assertNull(GradeNavigationTarget.from(SavedStateHandle(mapOf("periodId" to "123"))))
        assertNull(SchoolTaskNavigationTarget.from(SavedStateHandle(mapOf(
            "taskId" to "1", "taskType" to "4", "taskDate" to "not-a-date",
        ))))
    }
}
