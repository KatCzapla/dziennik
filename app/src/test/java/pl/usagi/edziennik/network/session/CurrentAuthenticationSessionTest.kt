package pl.usagi.edziennik.network.session

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pl.usagi.edziennik.network.NetworkModule

class CurrentAuthenticationSessionTest {
    @Test
    fun eduVulcanUsesAuthenticatedCookieJarAndRoutesUonetRequests() {
        val projectCookies = RecordingCookieJar()
        val eduCookies = RecordingCookieJar()
        val eduClient = OkHttpClient.Builder().cookieJar(eduCookies).build()
        val session = CurrentAuthenticationSession(projectCookies)
        val uonetUrl = "https://uczen.edu.lublin.eu/lublin/api/Context".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("session")
            .value("opaque")
            .hostOnlyDomain("uczen.edu.lublin.eu")
            .build()
        eduCookies.saveFromResponse(uonetUrl, listOf(cookie))

        session.activate(AutoDetectLoginResult.Success(AuthenticationProvider.EDUVULCAN, eduClient))
        val request = Request.Builder()
            .url("https://projekt-uczen.edu.lublin.eu/lublin/api/Context")
            .build()
        val routed = session.route(request)

        assertEquals(uonetUrl, routed.url)
        assertEquals(listOf(cookie), session.loadForRequest(routed.url))
        assertEquals(AuthenticationProvider.EDUVULCAN, session.provider)
        assertEquals(emptyList<Cookie>(), projectCookies.loadForRequest(routed.url))
    }

    @Test
    fun projectUonetAndClearUseOriginalHostAndCookieJar() {
        val projectCookies = RecordingCookieJar()
        val session = CurrentAuthenticationSession(projectCookies)
        val request = Request.Builder()
            .url("https://projekt-uczen.edu.lublin.eu/lublin/api/Context")
            .build()
        val cookie = Cookie.Builder()
            .name("session")
            .value("project")
            .hostOnlyDomain("projekt-uczen.edu.lublin.eu")
            .build()
        projectCookies.saveFromResponse(request.url, listOf(cookie))

        session.activate(AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET))
        assertEquals(request.url, session.route(request).url)
        assertEquals(listOf(cookie), session.loadForRequest(request.url))

        session.clear()
        assertNull(session.provider)
        assertEquals(request.url, session.route(request).url)
        assertEquals(listOf(cookie), session.loadForRequest(request.url))
    }

    @Test
    fun sharedApplicationClientUsesActiveSessionAndRoutesContext() {
        val session = CurrentAuthenticationSession(RecordingCookieJar())
        val eduClient = OkHttpClient.Builder().cookieJar(RecordingCookieJar()).build()
        session.activate(AutoDetectLoginResult.Success(AuthenticationProvider.EDUVULCAN, eduClient))
        val sharedClient = NetworkModule.provideOkHttpClient(session)
        var observedRequest: Request? = null
        val scriptedClient = sharedClient.newBuilder()
            .addInterceptor { chain ->
                observedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        scriptedClient.newCall(
            Request.Builder().url("https://projekt-uczen.edu.lublin.eu/lublin/api/Context").build(),
        ).execute().close()

        assertSame(session, sharedClient.cookieJar)
        assertEquals("uczen.edu.lublin.eu", observedRequest?.url?.host)
    }

    @Test
    fun userDataIsClearedAndStaleFetchCannotRestoreIt() {
        val session = CurrentAuthenticationSession(RecordingCookieJar())
        val generation = session.generation()
        val user = LoggedInUser("Anna Kowalska", LoggedInUserType.PARENT)

        session.setLoggedInUserIfCurrent(generation, user)
        assertEquals(user, session.loggedInUser.value)

        session.clear()
        session.setLoggedInUserIfCurrent(generation, user)
        assertNull(session.loggedInUser.value)
    }

    @Test
    fun restoredEduVulcanRoutesThroughPersistedCookies() {
        val persistedCookies = RecordingCookieJar()
        val session = CurrentAuthenticationSession(persistedCookies)
        val url = "https://uczen.edu.lublin.eu/lublin/api/Context".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("session")
            .value("opaque")
            .hostOnlyDomain("uczen.edu.lublin.eu")
            .build()
        persistedCookies.saveFromResponse(url, listOf(cookie))

        session.restore(AuthenticationProvider.EDUVULCAN)
        val routed = session.route(Request.Builder()
            .url("https://projekt-uczen.edu.lublin.eu/lublin/api/Context")
            .build())

        assertEquals(url, routed.url)
        assertEquals(listOf(cookie), session.loadForRequest(routed.url))
        assertEquals(AuthenticationProvider.EDUVULCAN, session.provider)
    }

    @Test fun providerMetadataAndUnrelatedCookiesAreNotSessionMaterial() {
        val jar = RecordingCookieJar()
        val session = CurrentAuthenticationSession(jar)
        session.restore(AuthenticationProvider.PROJECT_UONET)
        assertEquals(false, session.hasSessionCookies(AuthenticationProvider.PROJECT_UONET))
        val unrelated = "https://unrelated.test/".toHttpUrl()
        jar.saveFromResponse(unrelated, listOf(Cookie.Builder().name("fixture").value("fixture")
            .hostOnlyDomain(unrelated.host).build()))
        assertEquals(false, session.hasSessionCookies(AuthenticationProvider.PROJECT_UONET))
    }

    @Test fun sessionMaterialMustMatchProviderPathAndExpiry() {
        val jar = RecordingCookieJar()
        val session = CurrentAuthenticationSession(jar)
        val project = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context".toHttpUrl()
        val edu = "https://uczen.edu.lublin.eu/lublin/api/Context".toHttpUrl()
        jar.saveFromResponse(project, listOf(Cookie.Builder().name("fixture").value("fixture")
            .hostOnlyDomain(project.host).build()))
        jar.saveFromResponse(edu, listOf(
            Cookie.Builder().name("expired").value("fixture").hostOnlyDomain(edu.host).expiresAt(1).build(),
            Cookie.Builder().name("other-path").value("fixture").hostOnlyDomain(edu.host).path("/other").build(),
        ))
        assertEquals(true, session.hasSessionCookies(AuthenticationProvider.PROJECT_UONET))
        assertEquals(false, session.hasSessionCookies(AuthenticationProvider.EDUVULCAN))
        jar.saveFromResponse(edu, listOf(Cookie.Builder().name("fixture").value("fixture")
            .hostOnlyDomain(edu.host).build()))
        assertEquals(true, session.hasSessionCookies(AuthenticationProvider.EDUVULCAN))
    }

    private class RecordingCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies += cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
    }
}
