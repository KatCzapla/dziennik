package pl.usagi.edziennik.persistence.changes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedStudentIdentityTest {
    @Test fun selectionUsesStableLocalScopeAndIsClearedWithAccount() {
        val store = MemoryStore()
        val identity = LocalSnapshotIdentity(store)
        identity.ensureAccountId()
        val first = identity.studentScopeId(101)
        val second = identity.studentScopeId(202)
        identity.selectStudent(202)
        assertEquals(second, LocalSnapshotIdentity(store).selectedStudentScopeId())
        assertNotEquals(first, identity.selectedStudentScopeId())
        identity.clearAccount()
        assertNull(identity.selectedStudentScopeId())
    }

    private class MemoryStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }
}
