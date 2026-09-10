package pl.katcz.edziennik.ui.login

import pl.katcz.edziennik.domain.Student

data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val isCheckingSession: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingStudents: Boolean = false,
    val isSessionValid: Boolean = false,
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = login.isNotBlank() && password.isNotBlank() && !isLoading && !isCheckingSession
}
