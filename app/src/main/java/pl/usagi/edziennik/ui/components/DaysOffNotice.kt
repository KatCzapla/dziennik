package pl.usagi.edziennik.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.usagi.edziennik.R
import pl.usagi.edziennik.domain.SchoolDayOff
import pl.usagi.edziennik.ui.theme.EDziennikTheme

@Composable
fun DaysOffNotice(daysOff: List<SchoolDayOff>, isLoading: Boolean, failed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm)) {
        if (daysOff.isNotEmpty()) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.sm),
            ) {
                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs)) {
                    Text(
                        text = stringResource(R.string.school_day_off_heading),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    daysOff.forEach { day ->
                        Text(
                            text = day.name ?: stringResource(R.string.school_day_off),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (isLoading || failed) {
            Text(
                text = stringResource(if (failed) R.string.days_off_unavailable else R.string.days_off_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
