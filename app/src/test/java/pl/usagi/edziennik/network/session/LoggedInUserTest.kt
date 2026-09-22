package pl.usagi.edziennik.network.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.usagi.edziennik.domain.Mailbox

class LoggedInUserTest {
    @Test
    fun parentMailboxProvidesFullAccountOwnerName() {
        assertEquals(
            LoggedInUser("Anna Maria Kowalska", LoggedInUserType.PARENT),
            listOf(mailbox("Anna Maria Kowalska - R - Jan Kowalski - (ABC)"))
                .toLoggedInUser(),
        )
    }

    @Test
    fun studentMailboxProvidesFullAccountOwnerName() {
        assertEquals(
            LoggedInUser("Jan Kowalski", LoggedInUserType.STUDENT),
            listOf(mailbox("Jan Kowalski - U - (ABC)"))
                .toLoggedInUser(),
        )
    }

    @Test
    fun multipleChildrenOfTheSameParentAreUnambiguous() {
        assertEquals(
            LoggedInUser("Anna Kowalska", LoggedInUserType.PARENT),
            listOf(
                mailbox("Anna Kowalska - R - Jan Kowalski - (ABC)"),
                mailbox("Anna Kowalska - R - Ewa Kowalska - (DEF)"),
            ).toLoggedInUser(),
        )
    }

    @Test
    fun unrecognizedOrAmbiguousNamesDoNotProduceUser() {
        assertNull(listOf(mailbox("Anna Kowalska - X - (ABC)")).toLoggedInUser())
        assertNull(listOf(mailbox("Anna Kowalska - R - (ABC)")).toLoggedInUser())
        assertNull(listOf(mailbox("Anna Kowalska - R -   - (ABC)")).toLoggedInUser())
        assertNull(listOf(mailbox("Anna Kowalska - R - Jan Kowalski - (ABC)"), mailbox("invalid"))
            .toLoggedInUser())
        assertNull(listOf(mailbox("Anna Kowalska - R - Jan Kowalski - (ABC)"),
            mailbox("Maria Kowalska - R - Ewa Kowalska - (ABC)"))
            .toLoggedInUser())
    }

    @Test
    fun absentMailboxDataDoesNotProduceUser() {
        assertNull(emptyList<Mailbox>().toLoggedInUser())
        assertNull(listOf(mailbox(" ")).toLoggedInUser())
    }

    private fun mailbox(name: String) = Mailbox("key", name, 0)
}
