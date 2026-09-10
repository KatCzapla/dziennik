package pl.katcz.edziennik.domain

data class Student(
    val key: String,
    val diaryId: Int,
    val name: String,
    val className: String,
    val schoolName: String,
)
