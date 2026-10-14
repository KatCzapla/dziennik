package pl.usagi.edziennik.network.session

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pl.usagi.edziennik.network.eduvulcan.EduVulcanLoginResult

class AutoDetectLoginTest {
    @Test fun savedProjectProviderNetworkErrorIsTransientWithoutDetection() = runTest {
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> error("Must not detect provider") },
            loginProjectUonet = { _, _ -> Result.failure(AuthenticationException.Network(java.io.IOException())) },
        )
        assertEquals(AutoDetectLoginResult.TransientError,
            login.authenticateWithProvider(AuthenticationProvider.PROJECT_UONET, "test", "test"))
    }

    @Test fun savedProjectProviderServerErrorIsTransient() = runTest {
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> error("Must not detect provider") },
            loginProjectUonet = { _, _ -> Result.failure(AuthenticationException.Http(503, "https://example.invalid")) },
        )
        assertEquals(AutoDetectLoginResult.TransientError,
            login.authenticateWithProvider(AuthenticationProvider.PROJECT_UONET, "test", "test"))
    }

    @Test fun savedEduProviderTransientErrorDoesNotFallBackToProject() = runTest {
        val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.TransientError },
            loginProjectUonet = { _, _ -> error("Must not use fallback") },
        )
        assertEquals(AutoDetectLoginResult.TransientError,
            login.authenticateWithProvider(AuthenticationProvider.EDUVULCAN, "test", "test"))
    }

    @Test fun eduLoginTransportAndServerErrorsAreTransientInExistingImplementation() = runTest {
        val networkClient = OkHttpClient.Builder().addInterceptor { throw java.io.IOException() }.build()
        assertEquals(EduVulcanLoginResult.TransientError,
            pl.usagi.edziennik.network.eduvulcan.EduVulcanLogin(networkClient).authenticate("test", "test"))
        val serverClient = OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(503).message("test").body("".toResponseBody()).build()
        }.build()
        assertEquals(EduVulcanLoginResult.TransientError,
            pl.usagi.edziennik.network.eduvulcan.EduVulcanLogin(serverClient).authenticate("test", "test"))
    }
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
