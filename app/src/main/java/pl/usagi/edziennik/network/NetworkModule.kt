package pl.usagi.edziennik.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import pl.usagi.edziennik.network.credentials.AndroidKeystoreCredentialStore
import pl.usagi.edziennik.network.credentials.CredentialStore
import pl.usagi.edziennik.network.cookie.PersistentCookieJar
import pl.usagi.edziennik.network.eduvulcan.EduVulcanLogin
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import pl.usagi.edziennik.network.session.AutoDetectLogin
import pl.usagi.edziennik.network.session.AutoLoginSettings
import pl.usagi.edziennik.network.session.CurrentAuthenticationSession
import pl.usagi.edziennik.network.session.SessionManager
import pl.usagi.edziennik.network.session.SharedPreferencesAutoLoginSettings
import pl.usagi.edziennik.network.session.UonetSessionManager
import pl.usagi.edziennik.network.messages.MessagesSessionStore
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.system(
        ZoneId.of("Europe/Warsaw")
    )

    @Provides
    @Singleton
    fun provideUonetHtmlFormParser(): UonetHtmlFormParser = UonetHtmlFormParser()

    @Provides
    @Singleton
    fun providePersistentCookieJar(
        @ApplicationContext context: Context,
        json: Json,
    ): PersistentCookieJar = PersistentCookieJar(context, json)

    @Provides
    @Singleton
    fun provideCurrentAuthenticationSession(
        cookieJar: PersistentCookieJar,
        messagesSessionStore: MessagesSessionStore,
    ): CurrentAuthenticationSession = CurrentAuthenticationSession(
        cookieJar,
        clearStoredSession = {
            cookieJar.clear()
            messagesSessionStore.clear()
        },
        replaceProjectCookies = cookieJar::replaceCookies,
    )

    @Provides
    fun provideEduVulcanLogin(
        okHttpClient: OkHttpClient,
        json: Json,
        cookieJar: PersistentCookieJar,
        currentSession: CurrentAuthenticationSession,
    ): EduVulcanLogin = EduVulcanLogin(okHttpClient, json, cookieJar, currentSession)

    @Provides
    fun provideAutoDetectLogin(
        eduVulcanLogin: EduVulcanLogin,
        projectUonetSessionManager: UonetSessionManager,
    ): AutoDetectLogin = AutoDetectLogin(eduVulcanLogin, projectUonetSessionManager)

    @Provides
    @Singleton
    fun provideCredentialStore(
        credentialStore: AndroidKeystoreCredentialStore,
    ): CredentialStore = credentialStore

    @Provides
    @Singleton
    fun provideSessionManager(
        sessionManager: UonetSessionManager,
    ): SessionManager = sessionManager

    @Provides
    @Singleton
    fun provideAutoLoginSettings(
        settings: SharedPreferencesAutoLoginSettings,
    ): AutoLoginSettings = settings

    @Provides
    @Singleton
    fun provideOkHttpClient(currentSession: CurrentAuthenticationSession): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(currentSession)
            .addInterceptor { chain ->
                currentSession.withRequestSession {
                    val request = currentSession.route(chain.request())
                        .newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                    chain.proceed(request)
                }
            }
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/152.0.0.0 Safari/537.36"
}
