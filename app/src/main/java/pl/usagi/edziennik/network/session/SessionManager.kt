package pl.usagi.edziennik.network.session

enum class SessionCheck { VALID, EXPIRED, TRANSPORT_ERROR, SERVER_ERROR, INVALID_RESPONSE }

interface SessionManager {
    suspend fun login(
        username: String,
        password: String,
    ): Result<Unit>

    suspend fun isSessionValid(): Boolean

    suspend fun checkSession(): SessionCheck =
        if (isSessionValid()) SessionCheck.VALID else SessionCheck.EXPIRED

    suspend fun validatePersistedSession(): Boolean

    suspend fun clearSession()
}
