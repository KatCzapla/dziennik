package pl.usagi.edziennik.network.eduvulcan

import java.security.MessageDigest

internal object EduVulcanProofOfWork {
    fun solve(challenge: String, difficulty: Long, rounds: Int): String {
        require(difficulty in 1..0xffffffffL && rounds > 0)

        val solutions = mutableListOf<Int>()
        repeat(rounds) {
            val currentChallenge = challenge + solutions.joinToString(separator = "")
            solutions += findNonce(currentChallenge, difficulty)
        }
        return solutions.joinToString(separator = ";")
    }

    private fun findNonce(challenge: String, difficulty: Long): Int {
        val digest = MessageDigest.getInstance("SHA-256")
        for (nonce in 1 until 1_000_000_000) {
            val hash = digest.digest("$challenge$nonce".toByteArray(Charsets.UTF_8))
            val prefix = ((hash[0].toLong() and 0xff) shl 24) or
                ((hash[1].toLong() and 0xff) shl 16) or
                ((hash[2].toLong() and 0xff) shl 8) or
                (hash[3].toLong() and 0xff)
            if (prefix < difficulty) return nonce
        }
        throw IllegalStateException("Proof-of-Work could not be solved")
    }
}
