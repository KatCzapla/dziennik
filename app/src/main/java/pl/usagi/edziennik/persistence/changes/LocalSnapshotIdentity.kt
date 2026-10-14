package pl.usagi.edziennik.persistence.changes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.LocalStudentScopeId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface LocalIdentityStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun clear()
}

class SharedPreferencesLocalIdentityStore @Inject constructor(
    @ApplicationContext context: Context,
) : LocalIdentityStore {
    private val preferences = context.getSharedPreferences("snapshot_identity", Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)
    override fun put(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit())
    }
    override fun clear() {
        check(preferences.edit().clear().commit())
    }
}

@Singleton
class LocalSnapshotIdentity @Inject constructor(private val store: LocalIdentityStore) {
    @Synchronized
    fun currentAccountId(): LocalAccountId? = store.get(ACCOUNT_ID)?.let(::LocalAccountId)

    @Synchronized
    fun ensureAccountId(): LocalAccountId = currentAccountId() ?: LocalAccountId(UUID.randomUUID().toString()).also {
        store.put(ACCOUNT_ID, it.value)
    }

    @Synchronized
    fun replaceAccountId(): LocalAccountId {
        store.clear()
        return ensureAccountId()
    }

    @Synchronized
    fun studentScopeId(diaryId: Int): LocalStudentScopeId {
        require(currentAccountId() != null) { "No local account is active" }
        val key = "student:$diaryId"
        return store.get(key)?.let(::LocalStudentScopeId) ?: LocalStudentScopeId(UUID.randomUUID().toString()).also {
            store.put(key, it.value)
        }
    }

    @Synchronized
    fun selectedStudentScopeId(): LocalStudentScopeId? =
        store.get(SELECTED_STUDENT)?.let(::LocalStudentScopeId)

    @Synchronized
    fun selectStudent(diaryId: Int) {
        store.put(SELECTED_STUDENT, studentScopeId(diaryId).value)
    }

    @Synchronized
    fun clearAccount(): LocalAccountId? = currentAccountId().also { store.clear() }

    // Linearize notify() with logout: no publication can begin after identity invalidation.
    @Synchronized
    fun ifCurrentAccount(accountId: LocalAccountId, action: () -> Boolean): Boolean =
        currentAccountId() == accountId && action()

    private companion object {
        const val ACCOUNT_ID = "account_id"
        const val SELECTED_STUDENT = "selected_student"
    }
}
