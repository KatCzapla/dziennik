package pl.usagi.edziennik.network.session

interface AutoLoginSettings {
    suspend fun isAutoLoginEnabled(): Boolean

    suspend fun setAutoLoginEnabled(enabled: Boolean)

    suspend fun getAuthenticationProvider(): AuthenticationProvider?

    suspend fun setAuthenticationProvider(provider: AuthenticationProvider?)
}
