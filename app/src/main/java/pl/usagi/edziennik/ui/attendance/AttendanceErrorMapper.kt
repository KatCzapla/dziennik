package pl.usagi.edziennik.ui.attendance

import pl.usagi.edziennik.network.attendance.AttendanceFetchException
import pl.usagi.edziennik.repository.AttendanceRepositoryException

fun Throwable.toAttendanceErrorMessage(): String =
    when (this) {
        is AttendanceRepositoryException.NoActiveStudent ->
            "Wybierz ucznia, aby zobaczyć frekwencję."

        is AttendanceFetchException.Network ->
            "Nie udało się pobrać frekwencji. Sprawdź połączenie z internetem."

        is AttendanceFetchException.Http ->
            "Serwer UONET+ zwrócił błąd podczas pobierania frekwencji."

        is AttendanceFetchException.UnexpectedResponse ->
            "Odpowiedź frekwencji ma nieoczekiwany format."

        else ->
            "Nie udało się pobrać frekwencji."
    }
