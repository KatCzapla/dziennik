package pl.usagi.edziennik.network.session

import pl.usagi.edziennik.domain.Mailbox

enum class LoggedInUserType { PARENT, STUDENT }

data class LoggedInUser(
    val name: String,
    val type: LoggedInUserType,
)

fun List<Mailbox>.toLoggedInUser(): LoggedInUser? {
    if (isEmpty()) return null
    val users = map { mailbox ->
        val parent = PARENT_MAILBOX.matchEntire(mailbox.name)
        val student = STUDENT_MAILBOX.matchEntire(mailbox.name)
        when {
            parent != null && parent.groupValues.drop(1).all { it.isNotBlank() } ->
                LoggedInUser(parent.groupValues[1].trim(), LoggedInUserType.PARENT)
            student != null && student.groupValues.drop(1).all { it.isNotBlank() } ->
                LoggedInUser(student.groupValues[1].trim(), LoggedInUserType.STUDENT)
            else -> return null
        }
    }
    return users.distinct().singleOrNull()
}

private val PARENT_MAILBOX = Regex("""^(.+?) - R - (.+?) - \(([^()]+)\)$""")
private val STUDENT_MAILBOX = Regex("""^(.+?) - U - \(([^()]+)\)$""")
