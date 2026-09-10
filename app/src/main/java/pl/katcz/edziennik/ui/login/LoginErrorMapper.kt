package pl.katcz.edziennik.ui.login

import pl.katcz.edziennik.network.session.AuthenticationException

fun Throwable.toLoginErrorMessage(): String =
    when (this) {
        is AuthenticationException.InvalidCredentials ->
            "Nieprawidłowy login lub hasło."

        is AuthenticationException.Network ->
            "Nie udało się połączyć z UONET+. Sprawdź połączenie z internetem."

        is AuthenticationException.LoginFormNotFound,
        is AuthenticationException.WsFederationFormNotFound,
        is AuthenticationException.WsFederationStepLimitExceeded,
        is AuthenticationException.MainApplicationAuthenticationFailed ->
            "Proces logowania UONET+ nie zakończył się poprawnie."

        is AuthenticationException.SessionNotActive ->
            "Logowanie zakończone, ale sesja UONET+ nie została potwierdzona."

        is AuthenticationException.Http ->
            "Serwer UONET+ zwrócił błąd. Spróbuj ponownie później."

        else ->
            "Nie udało się zalogować. Spróbuj ponownie."
    }
