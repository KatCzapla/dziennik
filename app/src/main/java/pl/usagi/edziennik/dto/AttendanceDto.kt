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

@Serializable
data class AttendanceSubjectDto(
    @SerialName("nazwa") val name: String,
    @SerialName("id") val id: Int,
)

@Serializable
data class AttendanceStatisticsDto(
    @SerialName("podsumowanie") val summary: Double? = null,
    @SerialName("statystyki") val statistics: List<AttendanceCategoryStatisticsDto> = emptyList(),
)

@Serializable
data class AttendanceCategoryStatisticsDto(
    @SerialName("kategoriaFrekwencji") val attendanceCategory: Int? = null,
    @SerialName("miesiace") val months: List<AttendanceMonthStatisticsDto> = emptyList(),
    @SerialName("okresy") val periods: List<Int> = emptyList(),
    @SerialName("razem") val total: Int? = null,
)

@Serializable
data class AttendanceMonthStatisticsDto(
    @SerialName("miesiac") val month: Int,
    @SerialName("wartosc") val value: Int,
)

@Serializable
data class AttendanceExcusesResponseDto(
    @SerialName("usprawiedliwieniaAktywne")
    val active: Boolean? = null,
    @SerialName("usprawiedliwienia")
    val excuses: List<AttendanceExcuseDto> = emptyList(),
)

@Serializable
data class SubmitAttendanceExcuseDto(
    @SerialName("key") val studentKey: String,
    @SerialName("tresc") val content: String,
    @SerialName("usprawiedliwienia") val excuses: List<SubmitAttendanceExcuseLessonDto>,
)

@Serializable
data class SubmitAttendanceExcuseLessonDto(
    @SerialName("dzien") val day: String,
    @SerialName("idPoraLekcji") val lessonTimeId: Int,
)

@Serializable
data class AttendanceExcuseDto(
    @SerialName("status") val status: Int,
    @SerialName("dzien") val day: String? = null,
    @SerialName("idPoraLekcji") val lessonTimeId: Int? = null,
)
