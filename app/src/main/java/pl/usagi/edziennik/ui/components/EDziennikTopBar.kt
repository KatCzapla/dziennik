package pl.usagi.edziennik.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.ui.theme.EDziennikTheme

@Composable
fun EDziennikTopBar(
    activeStudent: Student?,
    students: List<Student>,
    onStudentSelected: (Student) -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wróć",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        StudentSwitcher(
            activeStudent = activeStudent,
            students = students,
            onStudentSelected = onStudentSelected,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
