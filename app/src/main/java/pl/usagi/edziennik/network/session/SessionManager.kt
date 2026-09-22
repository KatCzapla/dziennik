package pl.usagi.edziennik.network.session

interface SessionManager {
    suspend fun login(
        username: String,
        password: String,
    ): Result<Unit>

    suspend fun isSessionValid(): Boolean

    suspend fun validatePersistedSession(): Boolean

    suspend fun clearSession()
}
