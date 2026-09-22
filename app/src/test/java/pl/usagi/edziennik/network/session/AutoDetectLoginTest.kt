package pl.usagi.edziennik.network.session

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pl.usagi.edziennik.network.eduvulcan.EduVulcanLoginResult

class AutoDetectLoginTest {
    @Test
    fun eduVulcanSuccessDoesNotCallProjectUonet() = runTest {
        val sessionClient = OkHttpClient()
        var projectCalls = 0
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.Authenticated(sessionClient) },
            loginProjectUonet = { _, _ ->
                projectCalls++
                Result.success(Unit)
            },
        )

        val result = login.authenticate("alice", "secret") as AutoDetectLoginResult.Success

        assertEquals(AuthenticationProvider.EDUVULCAN, result.provider)
        assertSame(sessionClient, result.eduVulcanClient)
        assertEquals(0, projectCalls)
    }

    @Test
    fun eduVulcanRejectionFallsBackToSuccessfulProjectUonet() = runTest {
        var projectCalls = 0
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.InvalidCredentials },
            loginProjectUonet = { username, password ->
                assertEquals("alice", username)
                assertEquals("secret", password)
                projectCalls++
                Result.success(Unit)
            },
        )

        val result = login.authenticate("alice", "secret") as AutoDetectLoginResult.Success

        assertEquals(AuthenticationProvider.PROJECT_UONET, result.provider)
        assertNull(result.eduVulcanClient)
        assertEquals(1, projectCalls)
    }

    @Test
    fun bothProvidersRejectCredentials() = runTest {
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.InvalidCredentials },
            loginProjectUonet = { _, _ -> Result.failure(AuthenticationException.InvalidCredentials()) },
        )

        assertEquals(AutoDetectLoginResult.InvalidCredentials, login.authenticate("alice", "secret"))
    }

    @Test
    fun eduVulcanTechnicalErrorDoesNotCallProjectUonet() = runTest {
        var projectCalls = 0
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.TechnicalError },
            loginProjectUonet = { _, _ ->
                projectCalls++
                Result.success(Unit)
            },
        )

        assertEquals(AutoDetectLoginResult.TechnicalError, login.authenticate("alice", "secret"))
        assertEquals(0, projectCalls)
    }

    @Test
    fun projectUonetTechnicalFailureDoesNotBecomeInvalidCredentials() = runTest {
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.InvalidCredentials },
            loginProjectUonet = { _, _ -> Result.failure(AuthenticationException.SessionNotActive()) },
        )

        assertEquals(AutoDetectLoginResult.TechnicalError, login.authenticate("alice", "secret"))
    }

    @Test
    fun rememberedProjectTechnicalFailureDoesNotCallEduVulcan() = runTest {
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> error("Detection must not run") },
            loginProjectUonet = { _, _ -> Result.failure(AuthenticationException.SessionNotActive()) },
        )

        assertEquals(
            AutoDetectLoginResult.TechnicalError,
            login.authenticateWithProvider(AuthenticationProvider.PROJECT_UONET, "alice", "secret"),
        )
    }
}
