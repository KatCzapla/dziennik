package pl.usagi.edziennik.network.session

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import pl.usagi.edziennik.network.eduvulcan.EduVulcanLogin
import pl.usagi.edziennik.network.eduvulcan.EduVulcanLoginResult

enum class AuthenticationProvider {
    EDUVULCAN,
    PROJECT_UONET,
}

sealed interface AutoDetectLoginResult {
    data class Success(
        val provider: AuthenticationProvider,
        val eduVulcanClient: OkHttpClient? = null,
    ) : AutoDetectLoginResult

    data object InvalidCredentials : AutoDetectLoginResult
    data object TechnicalError : AutoDetectLoginResult
}

class AutoDetectLogin internal constructor(
    private val authenticateEduVulcan: suspend (String, String) -> EduVulcanLoginResult,
    private val loginProjectUonet: suspend (String, String) -> Result<Unit>,
) {
    constructor(eduVulcanLogin: EduVulcanLogin, projectUonetSessionManager: UonetSessionManager) : this(
        eduVulcanLogin::authenticate,
        projectUonetSessionManager::login,
    )

    suspend fun authenticate(username: String, password: String): AutoDetectLoginResult =
        try {
            when (val eduResult = authenticateEduVulcan(username, password)) {
                is EduVulcanLoginResult.Authenticated -> AutoDetectLoginResult.Success(
                    provider = AuthenticationProvider.EDUVULCAN,
                    eduVulcanClient = eduResult.client,
                )
                EduVulcanLoginResult.InvalidCredentials -> {
                    val projectResult = loginProjectUonet(username, password)
                    when {
                        projectResult.isSuccess -> AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET)
                        projectResult.exceptionOrNull() is AuthenticationException.InvalidCredentials ->
                            AutoDetectLoginResult.InvalidCredentials
                        else -> AutoDetectLoginResult.TechnicalError
                    }
                }
                EduVulcanLoginResult.Accepted,
                EduVulcanLoginResult.TechnicalError -> AutoDetectLoginResult.TechnicalError
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            AutoDetectLoginResult.TechnicalError
        }

    suspend fun authenticateWithProvider(
        provider: AuthenticationProvider,
        username: String,
        password: String,
    ): AutoDetectLoginResult = try {
        when (provider) {
            AuthenticationProvider.EDUVULCAN -> when (val result = authenticateEduVulcan(username, password)) {
                is EduVulcanLoginResult.Authenticated ->
                    AutoDetectLoginResult.Success(provider, result.client)
                EduVulcanLoginResult.InvalidCredentials -> AutoDetectLoginResult.InvalidCredentials
                EduVulcanLoginResult.Accepted,
                EduVulcanLoginResult.TechnicalError -> AutoDetectLoginResult.TechnicalError
            }
            AuthenticationProvider.PROJECT_UONET -> {
                val result = loginProjectUonet(username, password)
                when {
                    result.isSuccess -> AutoDetectLoginResult.Success(provider)
                    result.exceptionOrNull() is AuthenticationException.InvalidCredentials ->
                        AutoDetectLoginResult.InvalidCredentials
                    else -> AutoDetectLoginResult.TechnicalError
                }
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        AutoDetectLoginResult.TechnicalError
    }
}
