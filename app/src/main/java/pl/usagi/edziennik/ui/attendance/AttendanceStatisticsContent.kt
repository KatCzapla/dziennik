package pl.usagi.edziennik.ui.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import pl.usagi.edziennik.domain.AttendanceCategoryStatistics
import pl.usagi.edziennik.ui.components.EmptyState
import pl.usagi.edziennik.ui.components.ErrorState
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AttendanceStatisticsContent(
    state: AttendanceUiState,
    onSubjectSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = EDziennikTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedSubject = state.subjects.firstOrNull { it.id == state.selectedSubjectId }
    val statistics = state.statistics

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (state.activeStudent == null) {
            EmptyState(
                title = "Wybierz ucznia, aby zobaczyć statystyki",
                message = "",
            )
            return@Column
        }

        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                enabled = state.subjects.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = selectedSubject?.name ?: "Wszystkie",
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Wybierz przedmiot")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                state.subjects.forEach { subject ->
                    DropdownMenuItem(
                        text = { Text(subject.name) },
                        onClick = {
                            menuExpanded = false
                            onSubjectSelected(subject.id)
                        },
                    )
                }
            }
        }

        when {
            state.isStatisticsLoading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
            }
            state.statisticsErrorMessage != null -> ErrorState(
                title = "Nie udało się pobrać statystyk",
                message = state.statisticsErrorMessage,
                actionLabel = "Spróbuj ponownie",
                onAction = onRetry,
            )
            statistics == null -> EmptyState(
                title = "Brak statystyk frekwencji",
                message = "",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            "Frekwencja ogółem",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            statistics.summary?.let(::formatPercentage) ?: "Brak danych",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (statistics.categories.isEmpty()) {
                    item {
                        Text(
                            "Brak danych dla kategorii frekwencji.",
                            modifier = Modifier.padding(top = spacing.md),
                        )
                    }
                } else {
                    item {
                        Text(
                            "Kategorie",
                            modifier = Modifier.padding(top = spacing.md),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    items(statistics.categories) { category ->
                        CategoryTotalRow(category)
                    }

                    val months = statistics.categories
                        .flatMap { category -> category.months.map { it.month } }
                        .distinct()
                    if (months.isNotEmpty()) {
                        item {
                            Text(
                                "Miesiące",
                                modifier = Modifier.padding(top = spacing.lg),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(months) { month ->
                            DetailGroup(
                                title = monthName(month),
                                rows = statistics.categories.flatMap { category ->
                                    category.months.filter { it.month == month }.map { value ->
                                        category.status.label() to value.value.toString()
                                    }
                                },
                            )
                        }
                    }

                    val periodCount = statistics.categories.maxOfOrNull { it.periods.size } ?: 0
                    if (periodCount > 0) {
                        item {
                            Text(
                                "Okresy",
                                modifier = Modifier.padding(top = spacing.lg),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(periodCount) { index ->
                            DetailGroup(
                                title = "Okres ${index + 1}",
                                rows = statistics.categories.mapNotNull { category ->
                                    category.periods.getOrNull(index)?.let { value ->
                                        category.status.label() to value.toString()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTotalRow(category: AttendanceCategoryStatistics) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            category.status.label(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            category.total?.toString() ?: "—",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun DetailGroup(title: String, rows: List<Pair<String, String>>) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = Modifier.padding(top = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rows.forEach { (label, value) ->
            StatisticsValueRow(label, value)
        }
    }
}

@Composable
private fun StatisticsValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val polishLocale = Locale.forLanguageTag("pl-PL")

private fun monthName(month: Int): String =
    if (month in 1..12) {
        Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, polishLocale)
            .replaceFirstChar { it.titlecase(polishLocale) }
    } else {
        "Miesiąc $month"
    }

private fun formatPercentage(value: Double): String =
    "${DecimalFormat("0.##", DecimalFormatSymbols(polishLocale)).format(value)}%"
