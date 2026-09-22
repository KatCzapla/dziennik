package pl.usagi.edziennik.ui.schooltasks

import pl.usagi.edziennik.network.schooltasks.SchoolTasksFetchException
import pl.usagi.edziennik.repository.SchoolTasksRepositoryException

fun Throwable.toSchoolTasksErrorMessage(): String =
    when (this) {
        is SchoolTasksRepositoryException.NoActiveStudent ->
            "Wybierz ucznia, aby zobaczyć sprawdziany i zadania."

        is SchoolTasksFetchException.Network ->
            "Nie udało się pobrać sprawdzianów i zadań. Sprawdź połączenie z internetem."

        is SchoolTasksFetchException.Http ->
            "Serwer UONET+ zwrócił błąd podczas pobierania sprawdzianów i zadań."

        is SchoolTasksFetchException.UnexpectedResponse ->
            "Odpowiedź sprawdzianów i zadań ma nieoczekiwany format."

        else -> "Nie udało się pobrać sprawdzianów i zadań."
    }
