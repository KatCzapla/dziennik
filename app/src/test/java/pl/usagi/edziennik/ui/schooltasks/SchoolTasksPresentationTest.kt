package pl.usagi.edziennik.ui.schooltasks

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import java.time.LocalDate
import pl.usagi.edziennik.ui.navigation.SchoolTaskNavigationTarget

class SchoolTasksPresentationTest {
    @Test
    fun targetIndexMatchesIdTypeAndDateIncludingDayHeaders() {
        val monday = LocalDate.of(2026, 9, 14)
        val tuesday = monday.plusDays(1)
        val groups = listOf(
            schoolTask(monday, SchoolTaskType.Quiz, 1),
            schoolTask(tuesday, SchoolTaskType.Homework, 1),
            schoolTask(tuesday, SchoolTaskType.Quiz, 1),
        ).groupBySchoolDay()

        assertEquals(4, groups.targetItemIndex(SchoolTaskNavigationTarget(1, 4, tuesday)))
        assertEquals(3, groups.targetItemIndex(SchoolTaskNavigationTarget(1, 2, tuesday)))
        assertEquals(null, groups.targetItemIndex(SchoolTaskNavigationTarget(999, 4, tuesday)))
        assertEquals(null, groups.targetItemIndex(SchoolTaskNavigationTarget(1, 4, monday)))
    }

    @Test
    fun tasksAreGroupedByDayAndSortedForPresentation() {
        val monday = LocalDate.of(2026, 9, 14)
        val tuesday = LocalDate.of(2026, 9, 15)
        val groups = listOf(
            schoolTask(date = tuesday, type = SchoolTaskType.Homework, id = 3),
            schoolTask(date = monday, type = SchoolTaskType.Unknown(999), id = 2),
            schoolTask(date = monday, type = SchoolTaskType.Quiz, id = 1),
        ).groupBySchoolDay()

        assertEquals(listOf(monday, tuesday), groups.map { it.date })
        assertEquals(
            listOf(SchoolTaskType.Quiz, SchoolTaskType.Unknown(999)),
            groups.first().tasks.map { it.type },
        )
    }

    @Test
    fun unknownTypeHasNeutralUserLabel() {
        assertEquals("Inne", SchoolTaskType.Unknown(999).typeLabel())
    }

    @Test
    fun blankOptionalFieldsDoNotCreatePresentationLines() {
        val task = schoolTask(
            date = LocalDate.of(2026, 9, 14),
            type = SchoolTaskType.Homework,
            id = 1,
        ).copy(
            details = SchoolTaskDetails(
                description = "",
                teacher = " ",
                responseDeadline = null,
            ),
        )

        assertEquals(emptyList<SchoolTaskDetailLine>(), task.presentationDetailLines())
    }

    @Test
    fun taskWithDetailsExposesLinesShownUnderSubject() {
        val task = schoolTask(
            date = LocalDate.of(2026, 9, 14),
            type = SchoolTaskType.Homework,
            id = 1,
        ).copy(
            details = SchoolTaskDetails(
                description = "Przeczytać rozdział 4",
                teacher = "Anna Nowak",
                responseDeadline = LocalDate.of(2026, 9, 16),
            ),
        )

        assertEquals(
            listOf(
                SchoolTaskDetailLine("Opis", "Przeczytać rozdział 4"),
                SchoolTaskDetailLine("Termin", "16 września"),
            ),
            task.presentationDetailLines(),
        )
    }

    private fun schoolTask(
        date: LocalDate,
        type: SchoolTaskType,
        id: Int,
    ): SchoolTask =
        SchoolTask(
            type = type,
            rawType = when (type) {
                SchoolTaskType.Homework -> 4
                SchoolTaskType.Quiz -> 2
                is SchoolTaskType.Unknown -> type.rawValue
            },
            date = date,
            subject = "Matematyka",
            hasAttachment = false,
            id = id,
        )
}
