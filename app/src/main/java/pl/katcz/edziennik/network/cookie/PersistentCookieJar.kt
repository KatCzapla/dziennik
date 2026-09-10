package pl.katcz.edziennik.network.cookie

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(
    context: Context,
    private val json: Json,
) : CookieJar {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val cookies = mutableListOf<SerializableCookie>()

    init {
        cookies += loadPersistedCookies()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.removeAll { stored ->
            cookies.any { incoming ->
                stored.name == incoming.name &&
                    stored.domain == incoming.domain &&
                    stored.path == incoming.path
            }
        }
        this.cookies += cookies
            .filter { it.expiresAt > now }
            .map { it.toSerializableCookie() }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val beforeCleanup = cookies.size
        cookies.removeAll { it.expiresAt <= now }
        if (cookies.size != beforeCleanup) {
            persist()
        }

        return cookies
            .mapNotNull { it.toCookieOrNull() }
            .filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        preferences.edit().remove(COOKIES_KEY).apply()
    }

    private fun loadPersistedCookies(): List<SerializableCookie> {
        val encodedCookies = preferences.getString(COOKIES_KEY, null) ?: return emptyList()

        return runCatching {
            json.decodeFromString(ListSerializer(SerializableCookie.serializer()), encodedCookies)
                .filter { it.expiresAt > System.currentTimeMillis() }
        }.getOrElse {
            preferences.edit().remove(COOKIES_KEY).apply()
            emptyList()
        }
    }

    private fun persist() {
        preferences.edit()
            .putString(
                COOKIES_KEY,
                json.encodeToString(ListSerializer(SerializableCookie.serializer()), cookies),
            )
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "uonet_cookie_jar"
        const val COOKIES_KEY = "cookies"
    }
}

@Serializable
private data class SerializableCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

private fun Cookie.toSerializableCookie(): SerializableCookie =
    SerializableCookie(
        name = name,
        value = value,
        expiresAt = expiresAt,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly,
    )

private fun SerializableCookie.toCookieOrNull(): Cookie? =
    runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .apply {
                if (hostOnly) {
                    hostOnlyDomain(domain)
                } else {
                    domain(domain)
                }
                path(this@toCookieOrNull.path)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()
