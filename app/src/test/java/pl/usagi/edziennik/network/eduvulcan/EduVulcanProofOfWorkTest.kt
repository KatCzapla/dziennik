package pl.usagi.edziennik.network.eduvulcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EduVulcanProofOfWorkTest {
    @Test
    fun solvesChainedRoundsUsingUnsignedSha256Prefix() {
        // Expected nonces come from auth_autodetect_test.py's find_pow_nonce.
        assertEquals("25;16", EduVulcanProofOfWork.solve("test", 0x10000000L, 2))
    }

    @Test
    fun rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            EduVulcanProofOfWork.solve("test", 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EduVulcanProofOfWork.solve("test", 0x10000000L, 0)
        }
    }
}
