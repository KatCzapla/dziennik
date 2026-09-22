package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TimetableEntryDto(
    @SerialName("data")
    val date: String,
    @SerialName("godzinaOd")
    val timeFrom: String,
    @SerialName("godzinaDo")
    val timeTo: String,
    @SerialName("prowadzacy")
    val teacher: String? = null,
    @SerialName("prowadzacyWspomagajacy1")
    val supportingTeacher1: String? = null,
    @SerialName("prowadzacyWspomagajacy2")
    val supportingTeacher2: String? = null,
    @SerialName("przedmiot")
    val subject: String,
    @SerialName("sala")
    val room: String? = null,
    @SerialName("zmiany")
    val changes: List<TimetableChangeDto> = emptyList(),
    @SerialName("adnotacja")
    val annotation: Int? = null,
    @SerialName("dodatkowe")
    val additional: Boolean? = null,
    @SerialName("zrealizowane")
    val completed: Boolean? = null,
    @SerialName("idJednostkaSkladowa")
    val organizationalUnitId: Int? = null,
)

@Serializable
data class TimetableChangeDto(
    @SerialName("zmiana")
    val changeType: Int,
    @SerialName("typProwadzacego")
    val teacherType: Int? = null,
    @SerialName("dzien")
    val day: String? = null,
    @SerialName("nrLekcji")
    val lessonNumber: Int? = null,
    @SerialName("godzinaOd")
    val timeFrom: String? = null,
    @SerialName("godzinaDo")
    val timeTo: String? = null,
    @SerialName("grupa")
    val group: String? = null,
    @SerialName("zajecia")
    val subject: String? = null,
    @SerialName("sala")
    val room: String? = null,
    @SerialName("prowadzacy")
    val teacher: String? = null,
    @SerialName("informacjeNieobecnosc")
    val absenceInformation: String? = null,
)
