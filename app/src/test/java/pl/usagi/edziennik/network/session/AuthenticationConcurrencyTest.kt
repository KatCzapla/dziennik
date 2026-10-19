package pl.usagi.edziennik.network.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class AuthenticationConcurrencyTest {
    private val url = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context".toHttpUrl()

    @Test fun twoLoginsAndNestedVerificationAreSerialized() = runTest {
        val session = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = launch {
            session.withAuthentication {
                session.beginLogin()
                order += "first-start"
                started.complete(Unit)
                finish.await()
                session.withAuthentication { order += "nested-check" }
                session.activate(AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET))
                order += "first-end"
            }
        }
        started.await()
        val second = launch {
            session.withAuthentication {
                session.beginLogin()
                order += "second-start"
                session.activate(AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET))
            }
        }
        yield()
        assertEquals(listOf("first-start"), order)
        finish.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("first-start", "nested-check", "first-end", "second-start"), order)
    }

    @Test fun logoutCancelsRunningAndQueuedAuthenticationEvenIfLoginIgnoresCancellation() = runTest {
        val jar = TestCookies()
        val session = CurrentAuthenticationSession(jar, jar::clear)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var activated = false
        var queuedRan = false
        val login = launch {
            session.withAuthentication {
                session.beginLogin()
                session.withRequestSession { session.saveFromResponse(url, listOf(cookie("partial"))) }
                started.complete(Unit)
                withContext(NonCancellable) { finish.await() }
                session.commitAuthenticationState { jar.saveFromResponse(url, listOf(cookie("late"))) }
                session.activate(AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET))
                activated = true
            }
        }
        started.await()
        val queued = launch { session.withAuthentication { queuedRan = true; session.restore(AuthenticationProvider.PROJECT_UONET) } }
        yield()
        session.invalidateAuthentication()
        assertNull(session.provider)
        assertTrue(jar.loadForRequest(url).isEmpty())
        finish.complete(Unit)
        login.join()
        queued.join()
        assertTrue(login.isCancelled)
        assertTrue(queued.isCancelled)
        assertFalse(activated)
        assertFalse(queuedRan)
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test fun cancellationDiscardsPartialLoginCookiesAndReleasesLock() = runTest {
        val jar = TestCookies()
        val session = CurrentAuthenticationSession(jar, jar::clear)
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val login = launch {
            session.withAuthentication {
                session.beginLogin()
                session.withRequestSession { session.saveFromResponse(url, listOf(cookie("partial"))) }
                started.complete(Unit)
                never.await()
            }
        }
        started.await()
        login.cancel()
        login.join()
        assertNull(session.provider)
        assertTrue(jar.loadForRequest(url).isEmpty())
        session.withAuthentication {
            session.beginLogin()
            session.activate(AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET))
        }
        assertEquals(AuthenticationProvider.PROJECT_UONET, session.provider)
    }

    @Test fun expiredVerificationFinishesBeforeNewLoginAndCannotClearItsCookies() = runTest {
        val jar = TestCookies()
        val session = CurrentAuthenticationSession(jar, jar::clear)
        val verifying = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val oldCheck = launch {
            session.withAuthentication {
                verifying.complete(Unit)
                response.await()
                session.clearCookies(jar::clear)
            }
        }
        verifying.await()
        val newLogin = async {
            session.withAuthentication {
                session.beginLogin()
                session.withRequestSession { session.saveFromResponse(url, listOf(cookie("new"))) }
                session.activate(AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET))
            }
        }
        yield()
        assertFalse(newLogin.isCompleted)
        response.complete(Unit)
        oldCheck.join()
        newLogin.await()
        assertEquals("new", jar.loadForRequest(url).single().value)
        assertEquals(AuthenticationProvider.PROJECT_UONET, session.provider)
    }

    @Test fun ordinaryRequestsDoNotWaitForAuthenticationButCannotWriteIntoLogin() = runTest {
        val jar = TestCookies()
        val session = CurrentAuthenticationSession(jar, jar::clear)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val login = launch {
            session.withAuthentication {
                session.beginLogin()
                started.complete(Unit)
                finish.await()
            }
        }
        started.await()
        val request = async {
            session.withRequestSession { session.saveFromResponse(url, listOf(cookie("unrelated"))); 42 }
        }
        assertEquals(42, request.await())
        assertTrue(jar.loadForRequest(url).isEmpty())
        finish.complete(Unit)
        login.join()
    }

    @Test fun cancellingVerificationDoesNotClearAnExistingSession() = runTest {
        val jar = TestCookies()
        jar.saveFromResponse(url, listOf(cookie("existing")))
        val session = CurrentAuthenticationSession(jar, jar::clear)
        session.restore(AuthenticationProvider.PROJECT_UONET)
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val verification = launch {
            session.withAuthentication {
                started.complete(Unit)
                never.await()
            }
        }
        started.await()
        verification.cancel()
        verification.join()
        assertEquals(AuthenticationProvider.PROJECT_UONET, session.provider)
        assertEquals("existing", jar.loadForRequest(url).single().value)
    }

    @Test fun ordinaryRequestAfterLogoutCannotPopulateAuthenticationCookies() {
        val jar = TestCookies()
        val session = CurrentAuthenticationSession(jar, jar::clear)
        session.invalidateAuthentication()
        session.withRequestSession { session.saveFromResponse(url, listOf(cookie("unexpected"))) }
        assertNull(session.provider)
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    private fun cookie(value: String): Cookie = Cookie.Builder().name("session").value(value)
        .hostOnlyDomain(url.host).build()

    private class TestCookies : CookieJar {
        private var cookies = emptyList<Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this.cookies = cookies }
        @Synchronized override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
        @Synchronized fun clear() { cookies = emptyList() }
    }
}
