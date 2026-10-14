package pl.usagi.edziennik.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import pl.usagi.edziennik.R
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.ui.theme.EDziennikTheme

@Composable
fun StudentSwitcher(
    activeStudent: Student?,
    students: List<Student>,
    onStudentSelected: (Student) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = EDziennikTheme.spacing
    val canSwitch = activeStudent != null && students.size > 1

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .widthIn(max = 248.dp)
                .defaultMinSize(minHeight = 48.dp)
                .clickable(enabled = canSwitch) { expanded = true }
                .padding(horizontal = spacing.xs, vertical = spacing.xs),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = activeStudent?.name ?: stringResource(R.string.student_switcher_fallback),
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canSwitch) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.student_switcher_change),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 248.dp),
        ) {
            students.forEach { student ->
                val selected = student == activeStudent
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = student.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = student.detailsLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.student_switcher_current),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onStudentSelected(student)
                    },
                )
            }
        }
    }
}

private fun Student.detailsLabel(): String =
    listOf(className, schoolName)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
