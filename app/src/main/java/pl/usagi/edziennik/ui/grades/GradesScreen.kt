package pl.usagi.edziennik.ui.grades

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.GradeColumn
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.SubjectGrades
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.components.EmptyState
import pl.usagi.edziennik.ui.components.ErrorState
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import java.math.BigDecimal
import kotlin.text.isNotBlank

@Composable
fun GradesRoute(
    onBack: () -> Unit,
    onOpenSubject: (Int) -> Unit,
    viewModel: GradesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    GradesScreen(
        state = state,
        onBack = onBack,
        onPeriodSelected = viewModel::selectPeriod,
        onRetry = viewModel::retry,
        onStudentSelected = viewModel::selectStudent,
        onOpenSubject = onOpenSubject,
    )
}

@Composable
fun GradesSubjectRoute(
    subjectPosition: Int,
    onBack: () -> Unit,
    viewModel: GradesViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    GradesSubjectScreen(
        subject = viewModel.subjectByPosition(subjectPosition),
        showAverageAndPoints = state.grades?.settings?.showAverageAndPoints == true,
        onBack = onBack,
    )
}

@Composable
private fun GradesScreen(
    state: GradesUiState,
    onBack: () -> Unit,
    onPeriodSelected: (ClassificationPeriod) -> Unit,
    onRetry: () -> Unit,
    onStudentSelected: (Student) -> Unit,
    onOpenSubject: (Int) -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EDziennikTopBar(
                activeStudent = state.activeStudent,
                students = state.students,
                onStudentSelected = onStudentSelected,
                showBack = true,
                onBack = onBack,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Oceny",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Box(
                                modifier = Modifier.width(180.dp),
                            ) {
                                PeriodSelector(
                                    periods = state.periods,
                                    selectedPeriod = state.selectedPeriod,
                                    enabled = !state.isLoadingPeriods,
                                    onPeriodSelected = onPeriodSelected,
                                )
                            }
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                item {
                    ErrorState(
                        title = "Oceny chwilowo niedostępne",
                        message = state.errorMessage,
                        actionLabel = "Spróbuj ponownie",
                        onAction = onRetry,
                    )
                }
            }

            if (state.grades != null) {
                val grades = state.grades
                item {
                    RecentGradesSection(recentGrades = grades.recentGrades())
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(
                            text = "Przedmioty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                itemsIndexed(
                    items = grades.subjects,
                    key = { _, subject -> subject.position },
                ) { index, subject ->
                    SubjectGradesRow(
                        subject = subject,
                        onClick = { onOpenSubject(subject.position) },
                    )

                    if (index != grades.subjects.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(spacing.md))
                }
            } else if (!state.isLoading && state.errorMessage == null) {
                item {
                    EmptyState(
                        title = "Brak ocen",
                        message = "Wybierz ucznia i okres klasyfikacyjny.",
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    periods: List<ClassificationPeriod>,
    selectedPeriod: ClassificationPeriod?,
    enabled: Boolean,
    onPeriodSelected: (ClassificationPeriod) -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        periods.forEach { period ->
            val selected = period.id == selectedPeriod?.id

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable(
                        enabled = enabled && !selected,
                        onClick = { onPeriodSelected(period) },
                    )
                    .padding(
                        horizontal = spacing.md,
                        vertical = spacing.xs,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = period.shortLabel(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                    ),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecentGradesSection(
    recentGrades: List<RecentGrade>,
) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = "Ostatnie oceny",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (recentGrades.isEmpty()) {
            Text(
                text = "Brak ocen w tym okresie",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recentGrades.forEachIndexed { index, recentGrade ->
                GradePreviewRow(
                    grade = recentGrade.grade,
                    title = recentGrade.subjectName,
                    subtitle = buildString {
                        append(recentGrade.columnName)
                        append(" · ")
                        append(
                            recentGrade.grade.date.format(
                                shortGradeDateFormatter,
                            ),
                        )
                    },
                )

                if (index != recentGrades.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectGradesRow(
    subject: SubjectGrades,
    onClick: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    val gradeCount = subject.gradeCount()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = subject.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = gradeCount.gradeCountLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        subject.latestGrades(limit = 3).forEach { grade ->
            GradePreviewRow(
                grade = grade,
                title = grade.columnName,
                subtitle = grade.date.format(shortGradeDateFormatter),
            )
        }
    }
}

@Composable
private fun GradePreviewRow(
    grade: Grade,
    title: String,
    subtitle: String,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeValueBadge(
            value = grade.value,
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                ),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (grade.changedSinceLastLogin) {
                    Box(
                        modifier = Modifier
                            .padding(spacing.xs)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GradesSubjectScreen(
    subject: SubjectGrades?,
    showAverageAndPoints: Boolean,
    onBack: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(48.dp)
                    .statusBarsPadding()
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wróć",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { paddingValues ->

        if (subject == null) {
            EmptyState(
                title = "Nie znaleziono przedmiotu",
                message = "",
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = spacing.lg),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                SubjectDetailsHeader(
                    subject = subject,
                    showAverageAndPoints = showAverageAndPoints,
                )
            }

            item {
                Text(
                    text = "Oceny",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (subject.columns.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = "Brak ocen",
                            modifier = Modifier.padding(spacing.md),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = subject.columns,
                    key = { column -> column.id },
                ) { column ->
                    GradeColumnSection(
                        column = column,
                        includeGradeWeight = subject.includeGradeWeight,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(spacing.lg))
            }
        }
    }
}

@Composable
private fun SubjectDetailsHeader(
    subject: SubjectGrades,
    showAverageAndPoints: Boolean,
) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = subject.subjectName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = subject.gradeCount().gradeCountLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (subject.teachers.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = if (subject.teachers.size == 1) {
                        "Nauczyciel"
                    } else {
                        "Nauczyciele"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = subject.teachers.joinToString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        TermGrades(subject)

        ExamGrades(subject)

        if (showAverageAndPoints) {
            AverageAndPoints(subject)
        }
    }
}

@Composable
private fun TermGrades(
    subject: SubjectGrades,
) {
    val spacing = EDziennikTheme.spacing

    val grades = listOfNotNull(
        subject.proposedTermGrade?.let {
            "Proponowana" to it
        },
        subject.termGrade?.let {
            "Okresowa" to it
        },
    )

    if (grades.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        grades.forEach { (label, value) ->
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ExamGrades(subject: SubjectGrades) {
    val values = listOfNotNull(
        subject.practicalExamGrade?.let { "Forma praktyczna: $it" },
        subject.oralExamGrade?.let { "Forma ustna: $it" },
        subject.proposedExamGrade?.let { "Ocena proponowana egzaminu: $it" },
        subject.finalExamGrade?.let { "Ocena łączna egzaminu: $it" },
    )
    if (values.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Egzaminy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        values.forEach { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AverageAndPoints(subject: SubjectGrades) {
    val values = listOfNotNull(
        subject.average?.let { "Średnia: ${it.formatGradeNumber()}" },
        subject.pointsSum
            ?.takeIf { it.isNotBlank() }
            ?.let { "Punkty: $it" },
        subject.averageAllTerms?.let { "Średnia wszystkie okresy: ${it.formatGradeNumber()}" },
        subject.pointsSumAllTerms
            ?.takeIf { it.isNotBlank() }
            ?.let { "Punkty wszystkie okresy: $it" },
    )
    if (values.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GradeColumnSection(
    column: GradeColumn,
    includeGradeWeight: Boolean,
) {
    val spacing = EDziennikTheme.spacing
    val grades = column.grades.sortedByDescending { it.date }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {

        if (grades.isEmpty()) {
            Text(
                text = "Brak ocen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            grades.forEachIndexed { index, grade ->
                GradeDetailsRow(
                    name = column.name,
                    category = column.category,
                    grade = grade,
                    includeGradeWeight = includeGradeWeight,
                )

                if (index != grades.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeValueBadge(
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun GradeDetailsRow(
    name: String,
    category: String,
    grade: Grade,
    includeGradeWeight: Boolean,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeValueBadge(
            value = grade.value,
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                ),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (grade.changedSinceLastLogin) {
                    Box(
                        modifier = Modifier
                            .padding(spacing.xs)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        lineHeight = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val firstLine = buildList {
                category
                    .takeIf { it.isNotBlank() && it != name }
                    ?.let(::add)

                if (includeGradeWeight) {
                    grade.weight?.let {
                        add("waga ${it.formatGradeNumber()}")
                    }
                }
            }

            val secondLine = buildList {
                add(grade.date.format(gradeDateFormatter))

                grade.teacher
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }

            if (firstLine.isNotEmpty()) {
                Text(
                    text = firstLine.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = secondLine.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Int.gradeCountLabel(): String =
    when (this) {
        0 -> "Brak ocen"
        1 -> "1 ocena"
        in 2..4 -> "$this oceny"
        else -> "$this ocen"
    }
