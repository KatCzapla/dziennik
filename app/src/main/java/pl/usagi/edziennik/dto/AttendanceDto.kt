package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AttendanceResponseDto(
    @SerialName("oddzialy")
    val classEntries: List<AttendanceEntryDto> = emptyList(),
)

@Serializable
data class AttendanceEntryDto(
    @SerialName("idPoraLekcji")
    val lessonTimeId: Int? = null,
    @SerialName("idLekcjaOddzial")
    val classLessonId: Int? = null,
    @SerialName("numerLekcji")
    val lessonNumber: Int? = null,
    @SerialName("kategoriaFrekwencji")
    val attendanceCategory: Int? = null,
    @SerialName("data")
    val date: String? = null,
    @SerialName("godzinaOd")
    val timeFrom: String? = null,
    @SerialName("godzinaDo")
    val timeTo: String? = null,
    @SerialName("opisZajec")
    val subject: String? = null,
    @SerialName("nauczyciel")
    val teacher: String? = null,
    @SerialName("dataOd")
    val dateFrom: String? = null,
    @SerialName("dataDo")
    val dateTo: String? = null,
)
