package pl.usagi.edziennik.ui.timetable

import pl.usagi.edziennik.network.timetable.TimetableFetchException
import pl.usagi.edziennik.repository.TimetableRepositoryException

fun Throwable.toTimetableErrorMessage(): String =
    when (this) {
        is TimetableRepositoryException.NoActiveStudent ->
            "Wybierz ucznia, aby zobaczyć plan zajęć."

        is TimetableFetchException.Network ->
            "Nie udało się pobrać planu. Sprawdź połączenie z internetem."

        is TimetableFetchException.Http ->
            "Serwer UONET+ zwrócił błąd podczas pobierania planu."

        is TimetableFetchException.UnexpectedResponse ->
            "Odpowiedź planu zajęć ma nieoczekiwany format."

        else ->
            "Nie udało się pobrać planu zajęć."
    }
