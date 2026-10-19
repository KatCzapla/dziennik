package pl.usagi.edziennik.network.session

import android.app.Application
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pl.usagi.edziennik.network.NetworkModule
import pl.usagi.edziennik.network.cookie.PersistentCookieJar
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import pl.usagi.edziennik.network.messages.MessagesSessionStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ManualProjectCookieIsolationTest {
    @Test fun firstGetHasNoOldCookiesAndRedirectsAndFederationShareTheCandidate() = runBlocking {
        Harness().use { h ->
            assertTrue(h.login().isSuccess)
            val requests = h.requests.toList()
            assertEquals("/lublin/", requests.first().path)
            assertNull(requests.first().cookie)
            assertEquals("trial=first", requests.first { it.path == "/entry" }.cookie)
            assertEquals("trial=first", requests.first { it.path == "/Account/Login" }.cookie)
            assertTrue(requests.filter { it.path == "/lublin/api/Context" }.all { it.cookie == "trial=verified" })
        }
    }

    @Test fun persistenceAndActiveSessionAreReplacedOnlyAfterAllVerificationSucceeds() = runBlocking {
        Harness().use { h ->
            assertTrue(h.login {
                assertEquals(listOf("old=previous"), h.persisted())
                assertEquals(AuthenticationProvider.PROJECT_UONET, h.current.provider)
            }.isSuccess)
            assertEquals(listOf("trial=verified"), h.persisted())
            assertEquals(AuthenticationProvider.PROJECT_UONET, h.current.provider)
        }
    }

    @Test fun formOrContextFailurePreservesOldCookiesAndActiveSession() = runBlocking {
        for (badForm in listOf(false, true)) Harness(badForm = badForm, invalidContext = !badForm).use { h ->
            assertTrue(h.login().isFailure)
            assertEquals(listOf("old=previous"), h.persisted())
            assertEquals(AuthenticationProvider.PROJECT_UONET, h.current.provider)
        }
    }

    @Test fun failedFinalUiVerificationDoesNotCommitPreviouslyVerifiedCandidate() = runBlocking {
        Harness(invalidSecondContext = true).use { h ->
            assertTrue(h.login().isFailure)
            assertEquals(listOf("old=previous"), h.persisted())
            assertEquals(AuthenticationProvider.PROJECT_UONET, h.current.provider)
        }
    }

    @Test fun cancellationDiscardsCandidateWithoutClearingPreviousCookies() = runBlocking {
        Harness(blockContext = true).use { h ->
            val login = async(Dispatchers.IO) { h.login() }
            assertTrue(h.contextStarted.await(5, TimeUnit.SECONDS))
            login.cancel()
            h.releaseContext.countDown()
            login.join()
            assertTrue(login.isCancelled)
            assertEquals(listOf("old=previous"), h.persisted())
            assertEquals(AuthenticationProvider.PROJECT_UONET, h.current.provider)
        }
    }

    @Test fun logoutDuringLoginCannotCommitOrRestoreTheCandidate() = runBlocking {
        Harness(blockContext = true).use { h ->
            val login = async(Dispatchers.IO) { h.login() }
            assertTrue(h.contextStarted.await(5, TimeUnit.SECONDS))
            h.current.invalidateAuthentication()
            h.releaseContext.countDown()
            login.join()
            assertTrue(login.isCancelled)
            assertNull(h.current.provider)
            assertTrue(h.persisted().isEmpty())
        }
    }

    @Test fun workerWaitsForUiLoginAndThenUsesTheCommittedCookies() = runBlocking {
        Harness(blockContext = true).use { h ->
            val login = async(Dispatchers.IO) { h.login() }
            assertTrue(h.contextStarted.await(5, TimeUnit.SECONDS))
            val workerAttempted = CompletableDeferred<Unit>()
            val workerEntered = CompletableDeferred<Unit>()
            val worker = async(Dispatchers.IO) {
                workerAttempted.complete(Unit)
                h.current.withAuthentication {
                    workerEntered.complete(Unit)
                    h.manager.checkSession()
                }
            }
            workerAttempted.await()
            assertFalse(workerEntered.isCompleted)
            h.releaseContext.countDown()
            assertTrue(login.await().isSuccess)
            assertEquals(SessionCheck.VALID, worker.await())
            assertEquals("trial=verified", h.requests.last().cookie)
        }
    }

    @Test fun retainedEduSessionCannotRouteProjectCandidateToTheWrongProvider() = runBlocking {
        Harness().use { h ->
            h.current.restore(AuthenticationProvider.EDUVULCAN)
            assertTrue(h.login {
                assertEquals(AuthenticationProvider.EDUVULCAN, h.current.provider)
                assertEquals(listOf("old=previous"), h.persisted())
            }.isSuccess)
            assertEquals("projekt-uczen.edu.lublin.eu", h.requests.first().remoteHost)
            assertEquals(AuthenticationProvider.PROJECT_UONET, h.current.provider)
        }
    }

    @Test fun repeatedBeginLoginDoesNotDiscardCookiesAlreadyCollectedByCandidate() = runBlocking {
        Harness().use { h ->
            assertTrue(h.login {
                h.current.beginLogin()
                h.current.prepareProjectLoginCandidate()
                assertEquals(listOf("trial"), h.current.loadForRequest(h.localUrl).map { it.name })
            }.isSuccess)
            assertEquals(listOf("trial=verified"), h.persisted())
        }
    }

    @Test fun cookieCommitFailureLeavesOldPersistentAndActiveSessionIntact() = runBlocking {
        Harness(failCommit = true).use { h ->
            assertTrue(runCatching { h.login() }.isFailure)
            assertEquals(listOf("old=previous"), h.persisted())
            assertEquals(AuthenticationProvider.PROJECT_UONET, h.current.provider)
        }
    }

    @Test fun lateResponseFromOldSessionCannotOverwriteCommittedCandidateCookies() = runBlocking {
        Harness().use { h ->
            val old = async(Dispatchers.IO) {
                h.client.newCall(Request.Builder().url("https://projekt-uczen.edu.lublin.eu/late").build())
                    .execute().use { it.body.string() }
            }
            assertTrue(h.lateStarted.await(5, TimeUnit.SECONDS))
            assertTrue(h.login().isSuccess)
            h.releaseLate.countDown()
            old.await()
            assertEquals(listOf("trial=verified"), h.persisted())
        }
    }

    private data class SeenRequest(val path: String, val cookie: String?, val remoteHost: String)

    private class Harness(
        private val badForm: Boolean = false,
        private val invalidContext: Boolean = false,
        private val invalidSecondContext: Boolean = false,
        private val blockContext: Boolean = false,
        failCommit: Boolean = false,
    ) : AutoCloseable {
        private val app = RuntimeEnvironment.getApplication()
        private val jar = PersistentCookieJar(app, Json)
        val current = CurrentAuthenticationSession(jar, jar::clear) { cookies ->
            check(!failCommit) { "Fixture commit failure" }
            jar.replaceCookies(cookies)
        }
        val requests = ConcurrentLinkedQueue<SeenRequest>()
        val contextStarted = CountDownLatch(1)
        val releaseContext = CountDownLatch(if (blockContext) 1 else 0)
        val lateStarted = CountDownLatch(1)
        val releaseLate = CountDownLatch(1)
        private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable).apply { isDaemon = true } }
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private var contextCount = 0
        val localUrl = "http://127.0.0.1:${server.address.port}/".toHttpUrl()
        private val transport = OkHttpClient.Builder().cookieJar(current).build()
        val client = NetworkModule.provideOkHttpClient(current).newBuilder().addInterceptor { chain ->
            // Replace transport only. The real manager, parser, federation and CookieJar still run.
            val remote = chain.request()
            val local = localUrl.newBuilder().encodedPath(remote.url.encodedPath).encodedQuery(remote.url.encodedQuery).build()
            transport.newCall(remote.newBuilder().url(local).header("X-Fixture-Host", remote.url.host).build())
                .execute().newBuilder().request(remote).build()
        }.build()
        private val parser = UonetHtmlFormParser()
        val manager = UonetSessionManager(client, jar, current, parser, WsFederationNavigator(client, parser), MessagesSessionStore(), Json)

        init {
            jar.replaceCookies(listOf(Cookie.Builder().name("old").value("previous").hostOnlyDomain(localUrl.host).path("/").build()))
            current.restore(AuthenticationProvider.PROJECT_UONET)
            server.executor = executor
            server.createContext("/") { exchange ->
                val path = exchange.requestURI.path
                requests += SeenRequest(path, exchange.requestHeaders.getFirst("Cookie"), exchange.requestHeaders.getFirst("X-Fixture-Host"))
                val context = path == "/lublin/api/Context"
                if (context) {
                    contextCount++
                    contextStarted.countDown()
                    check(releaseContext.await(10, TimeUnit.SECONDS))
                }
                if (path == "/late") {
                    lateStarted.countDown()
                    check(releaseLate.await(10, TimeUnit.SECONDS))
                    exchange.responseHeaders.add("Set-Cookie", "trial=late; Path=/")
                }
                val first = path == "/lublin/" && exchange.requestMethod == "GET"
                if (first) {
                    exchange.responseHeaders.add("Set-Cookie", "trial=first; Path=/; HttpOnly")
                    exchange.responseHeaders.add("Location", "/entry")
                }
                if (path == "/Account/Login") exchange.responseHeaders.add("Set-Cookie", "trial=verified; Path=/; HttpOnly")
                val valid = context && !invalidContext && !(invalidSecondContext && contextCount == 2)
                val body = when {
                    valid -> "{\"uczniowie\":[]}"
                    path == "/entry" && !badForm -> "<form action='https://logowanie.edu.lublin.eu/Account/Login'><input name='login'><input type='password' name='password'></form>"
                    path == "/Account/Login" -> "<form action='https://projekt-uczen.edu.lublin.eu/lublin/'><input type='hidden' name='wresult' value='fixture'></form>"
                    else -> "<html>fixture</html>"
                }.toByteArray()
                exchange.responseHeaders.add("Content-Type", if (valid) "application/json" else "text/html")
                exchange.sendResponseHeaders(if (first) 302 else 200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        suspend fun login(beforeCommit: () -> Unit = {}): Result<Unit> = current.withManualLogin {
            current.beginLogin()
            val result = manager.login("fixture", "fixture")
            if (result.isFailure) return@withManualLogin result
            beforeCommit()
            if (manager.checkSession() != SessionCheck.VALID) return@withManualLogin Result.failure(AuthenticationException.SessionNotActive())
            current.activate(AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET))
            Result.success(Unit)
        }

        fun persisted(): List<String> = PersistentCookieJar(app, Json).loadForRequest(localUrl).map { "${it.name}=${it.value}" }

        override fun close() {
            releaseContext.countDown()
            releaseLate.countDown()
            server.stop(0)
            executor.shutdownNow()
            transport.connectionPool.evictAll()
        }
    }
}
