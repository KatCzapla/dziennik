package pl.usagi.edziennik.network.credentials

data class StoredCredentials(
    val username: String,
    val password: String,
)

interface CredentialStore {
    suspend fun save(
        username: String,
        password: String,
    )

    suspend fun load(): StoredCredentials?

    suspend fun clear()

    suspend fun hasCredentials(): Boolean
}
