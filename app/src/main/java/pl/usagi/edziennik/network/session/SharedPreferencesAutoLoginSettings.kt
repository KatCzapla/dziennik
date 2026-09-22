package pl.usagi.edziennik.network.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesAutoLoginSettings @Inject constructor(
    @ApplicationContext context: Context,
) : AutoLoginSettings {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun isAutoLoginEnabled(): Boolean = withContext(Dispatchers.IO) {
        preferences.getBoolean(AUTO_LOGIN_ENABLED_KEY, DEFAULT_AUTO_LOGIN_ENABLED)
    }

    override suspend fun setAutoLoginEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putBoolean(AUTO_LOGIN_ENABLED_KEY, enabled)
            .apply()
    }

    override suspend fun getAuthenticationProvider(): AuthenticationProvider? = withContext(Dispatchers.IO) {
        preferences.getString(AUTHENTICATION_PROVIDER_KEY, null)
            ?.let { stored -> AuthenticationProvider.entries.firstOrNull { it.name == stored } }
    }

    override suspend fun setAuthenticationProvider(provider: AuthenticationProvider?) = withContext(Dispatchers.IO) {
        preferences.edit().apply {
            if (provider == null) remove(AUTHENTICATION_PROVIDER_KEY)
            else putString(AUTHENTICATION_PROVIDER_KEY, provider.name)
        }.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "auto_login_settings"
        const val AUTO_LOGIN_ENABLED_KEY = "auto_login_enabled"
        const val AUTHENTICATION_PROVIDER_KEY = "authentication_provider"
        const val DEFAULT_AUTO_LOGIN_ENABLED = false
    }
}
