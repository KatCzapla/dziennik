package pl.usagi.edziennik.domain.changes

import java.util.UUID

@JvmInline
value class LocalAccountId(val value: String) {
    init { require(UUID.fromString(value).toString() == value) }
}

@JvmInline
value class LocalStudentScopeId(val value: String) {
    init { require(UUID.fromString(value).toString() == value) }
}

enum class SnapshotArea {
    MESSAGES, GRADES, TIMETABLE, ATTENDANCE, TASKS, INFORMATION, PAYMENTS,
}

data class SnapshotScope(
    val accountId: LocalAccountId,
    val studentScopeId: LocalStudentScopeId?,
    val area: SnapshotArea,
    val range: String? = null,
) {
    init {
        require(area == SnapshotArea.MESSAGES || studentScopeId != null)
        require(area !in RANGE_AREAS || !range.isNullOrBlank())
    }

    val storageKey: String
        get() = listOf(accountId.value, studentScopeId?.value.orEmpty(), area.name, range.orEmpty()).joinToString("|")

    private companion object {
        val RANGE_AREAS = setOf(SnapshotArea.GRADES, SnapshotArea.TIMETABLE, SnapshotArea.ATTENDANCE, SnapshotArea.TASKS)
    }
}
