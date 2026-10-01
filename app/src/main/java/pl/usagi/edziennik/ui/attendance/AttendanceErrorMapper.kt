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

fun Throwable.toExcuseSubmitErrorMessage(): String = when (this) {
    is AttendanceFetchException.Network -> "Nie udało się wysłać usprawiedliwienia. Sprawdź połączenie."
    is AttendanceFetchException.Http -> "Serwer nie przyjął usprawiedliwienia. Spróbuj ponownie."
    else -> "Nie udało się wysłać usprawiedliwienia. Spróbuj ponownie."
}
