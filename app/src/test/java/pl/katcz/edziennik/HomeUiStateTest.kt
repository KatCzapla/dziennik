package pl.katcz.edziennik

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.katcz.edziennik.ui.home.HomeUiState

class HomeUiStateTest {
    @Test
    fun defaultStateDescribesSkeletonStage() {
        val state = HomeUiState()

        assertEquals("eDziennik", state.title)
        assertEquals("Szkielet aplikacji jest gotowy do Etapu 3.", state.description)
    }
}
