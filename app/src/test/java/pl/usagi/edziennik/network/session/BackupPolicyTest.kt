package pl.usagi.edziennik.network.session

import android.app.Application
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import pl.usagi.edziennik.R
import pl.usagi.edziennik.network.cookie.PersistentCookieJar
import pl.usagi.edziennik.network.credentials.AndroidKeystoreCredentialStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.SharedPreferencesLocalIdentityStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BackupPolicyTest {
    private val context = RuntimeEnvironment.getApplication()
    private val sensitiveFiles = setOf(
        "sharedpref" to "uonet_cookie_jar.xml",
        "sharedpref" to "auto_login_settings.xml",
        "sharedpref" to "snapshot_identity.xml",
        "database" to "student_snapshots.db",
        "database" to "student_snapshots.db-wal",
        "database" to "student_snapshots.db-shm",
        "database" to "student_snapshots.db-journal",
    )

    @Test fun legacyBackupExcludesSessionAndStudentDataWithoutDisablingOtherBackup() {
        assertTrue(exclusions(R.xml.backup_rules).containsAll(sensitiveFiles))
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test fun cloudBackupExcludesSessionAndStudentData() {
        assertTrue(exclusions(R.xml.data_extraction_rules, "cloud-backup").containsAll(sensitiveFiles))
    }

    @Test fun deviceTransferExcludesSessionAndStudentData() {
        assertTrue(exclusions(R.xml.data_extraction_rules, "device-transfer").containsAll(sensitiveFiles))
    }

    @Test fun cleanInstallHasNoAccountProviderCookiesOrCredentials() = runBlocking {
        val identity = LocalSnapshotIdentity(SharedPreferencesLocalIdentityStore(context))
        val settings = SharedPreferencesAutoLoginSettings(context)
        val session = CurrentAuthenticationSession(PersistentCookieJar(context, Json))
        assertNull(identity.currentAccountId())
        assertNull(settings.getAuthenticationProvider())
        assertFalse(settings.isAutoLoginEnabled())
        assertFalse(session.hasSessionCookies(AuthenticationProvider.PROJECT_UONET))
        assertFalse(session.hasSessionCookies(AuthenticationProvider.EDUVULCAN))
        assertFalse(AndroidKeystoreCredentialStore(context, Json).hasCredentials())
    }

    @Test fun restoredPreferencesDoNotProvidePrivateCredentialsOrCookies() = runBlocking {
        val identity = LocalSnapshotIdentity(SharedPreferencesLocalIdentityStore(context))
        identity.ensureAccountId()
        val settings = SharedPreferencesAutoLoginSettings(context)
        settings.setAutoLoginEnabled(true)
        settings.setAuthenticationProvider(AuthenticationProvider.PROJECT_UONET)
        val session = CurrentAuthenticationSession(PersistentCookieJar(context, Json))
        assertNotNull(identity.currentAccountId())
        assertTrue(settings.isAutoLoginEnabled())
        assertNotNull(settings.getAuthenticationProvider())
        assertFalse(AndroidKeystoreCredentialStore(context, Json).hasCredentials())
        assertFalse(session.hasSessionCookies(AuthenticationProvider.PROJECT_UONET))
    }

    private fun exclusions(resource: Int, section: String? = null): Set<Pair<String, String>> {
        val result = mutableSetOf<Pair<String, String>>()
        val parser = context.resources.getXml(resource)
        try {
            var inside = section == null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.name == section) inside = true
                    if (inside && parser.name == "exclude") {
                        result += parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path")
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == section) inside = false
                parser.next()
            }
        } finally {
            parser.close()
        }
        return result
    }
}
