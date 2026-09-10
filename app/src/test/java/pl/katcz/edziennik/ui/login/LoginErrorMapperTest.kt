package pl.katcz.edziennik.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.katcz.edziennik.network.session.AuthenticationException
import java.io.IOException

class LoginErrorMapperTest {
    @Test
    fun mapsInvalidCredentialsToReadableMessage() {
        val message = AuthenticationException.InvalidCredentials().toLoginErrorMessage()

        assertEquals("Nieprawidłowy login lub hasło.", message)
    }

    @Test
    fun mapsNetworkFailureToReadableMessage() {
        val message = AuthenticationException.Network(IOException()).toLoginErrorMessage()

        assertEquals("Nie udało się połączyć z UONET+. Sprawdź połączenie z internetem.", message)
    }
}
