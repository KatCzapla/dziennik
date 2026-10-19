package pl.usagi.edziennik.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grade
import pl.usagi.edziennik.domain.GradeColumn
import pl.usagi.edziennik.domain.SubjectGrades
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.components.EmptyState
import pl.usagi.edziennik.ui.components.ErrorState
import pl.usagi.edziennik.ui.navigation.GradeNavigationTarget
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import pl.usagi.edziennik.util.uppercaseLocalized

@Composable
fun GradesRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubject: (Int) -> Unit,
    onOpenTarget: (GradeNavigationTarget) -> Unit,
    viewModel: GradesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.grades, state.isLoading) {
        viewModel.consumeNavigationTarget()?.let(onOpenTarget)
    }

    GradesScreen(
        state = state,
        onBack = onBack,
        onPeriodSelected = viewModel::selectPeriod,
        onRetry = viewModel::retry,
        onOpenSettings = onOpenSettings,
        onOpenSubject = onOpenSubject,
    )
}

@Composable
fun GradesSubjectRoute(
    subjectPosition: Int,
    onBack: () -> Unit,
    viewModel: GradesViewModel,
    targetColumnId: Int? = null,
    targetGradeIndex: Int? = null,
) {
    val state by viewModel.uiState.collectAsState()
    GradesSubjectScreen(
        subject = viewModel.subjectByPosition(subjectPosition),
        showAverageAndPoints = state.grades?.settings?.showAverageAndPoints == true,
        onBack = onBack,
        targetColumnId = targetColumnId,
        targetGradeIndex = targetGradeIndex,
    )
}

@Composable
private fun GradesScreen(
    state: GradesUiState,
    onBack: () -> Unit,
    onPeriodSelected: (ClassificationPeriod) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubject: (Int) -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EDziennikTopBar(
                onOpenSettings = onOpenSettings,
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
        ) {
            item {
                Column(
                    modifier = Modifier.padding(),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
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
                val visibleSubjects = grades.subjects.filter {
                    it.hasPresentableGrades(grades.settings.showAverageAndPoints)
                }
                item {
                    RecentGradesSection(recentGrades = grades.recentGrades())
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Przedmioty".uppercaseLocalized(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                itemsIndexed(
                    items = visibleSubjects,
                    key = { _, subject -> subject.position },
                ) { index, subject ->
                    SubjectGradesRow(
                        subject = subject,
                        onClick = { onOpenSubject(subject.position) },
                    )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(
            text = "Ostatnie oceny".uppercaseLocalized(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
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
                            )
                        )
                    },
                )
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
            .clickable(onClick = onClick),
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

        Spacer(modifier = Modifier.height(spacing.md))
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
        ) {
            if (grade.changedSinceLastLogin || title.isNotEmpty())
                Row() {
                    if (grade.changedSinceLastLogin) {
                        Box(
                            modifier = Modifier
                                .padding(spacing.xs)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                    }

                    if (title.isNotEmpty())
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                }

            if (subtitle.isNotEmpty())
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
    targetColumnId: Int?,
    targetGradeIndex: Int?,
) {
    val spacing = EDziennikTheme.spacing
    val listState = rememberLazyListState()
    val grades = subject?.gradesByDate().orEmpty()
    var targetShown by rememberSaveable(targetColumnId, targetGradeIndex) { mutableStateOf(false) }
    LaunchedEffect(subject?.position, grades, targetColumnId, targetGradeIndex) {
        val hasTarget = grades.any { (column, grade) ->
            column.id == targetColumnId && grade.index == targetGradeIndex
        }
        if (hasTarget && !targetShown) listState.scrollToItem(2)
    }

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
            state = listState,
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
                    text = "Oceny".uppercaseLocalized(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (grades.isEmpty()) {
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
                item {
                    SubjectGradesSection(
                        grades = grades,
                        includeGradeWeight = subject.includeGradeWeight,
                        targetColumnId = targetColumnId,
                        targetGradeIndex = targetGradeIndex.takeIf { !targetShown },
                        onTargetShown = { targetShown = true },
                    )
                }
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
private fun SubjectGradesSection(
    grades: List<Pair<GradeColumn, IndexedValue<Grade>>>,
    includeGradeWeight: Boolean,
    targetColumnId: Int?,
    targetGradeIndex: Int?,
    onTargetShown: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {

        grades.forEachIndexed { index, (column, indexedGrade) ->
            val requester = remember { BringIntoViewRequester() }
            LaunchedEffect(targetColumnId, targetGradeIndex) {
                if (targetColumnId == column.id && targetGradeIndex == indexedGrade.index) {
                    withFrameNanos { }
                    requester.bringIntoView()
                    onTargetShown()
                }
            }
            GradeDetailsRow(
                name = column.name,
                category = column.category,
                grade = indexedGrade.value,
                includeGradeWeight = includeGradeWeight,
                modifier = Modifier.bringIntoViewRequester(requester),
            )
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
            .width(40.dp)
            .height(IntrinsicSize.Min)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GradeDetailsRow(
    name: String,
    category: String,
    grade: Grade,
    includeGradeWeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = modifier
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
                    style = MaterialTheme.typography.titleMedium,
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
                    style = MaterialTheme.typography.bodyLarge,
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
