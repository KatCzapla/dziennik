package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContextDto(
    @SerialName("uczniowie")
    val students: List<StudentDto> = emptyList(),
)

@Serializable
data class StudentDto(
    @SerialName("key")
    val key: String,
    @SerialName("idDziennik")
    val diaryId: Int,
    @SerialName("uczen")
    val name: String,
    @SerialName("oddzial")
    val className: String,
    @SerialName("jednostka")
    val schoolName: String,
)
