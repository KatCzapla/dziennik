package pl.usagi.edziennik.ui.messages

import pl.usagi.edziennik.repository.MessagesRepositoryException

fun Throwable.toMessagesErrorMessage(): String =
    when (this) {
        is MessagesRepositoryException.MessagesUnavailable -> "Nie udało się pobrać wiadomości."
        is MessagesRepositoryException.UnexpectedResponse -> "Odpowiedź modułu wiadomości ma nieoczekiwany format."
        else -> "Nie udało się pobrać wiadomości."
    }
