package pl.katcz.edziennik.dto

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
    @SerialName("przedmiot")
    val subject: String,
    @SerialName("sala")
    val room: String? = null,
    @SerialName("zmiany")
    val changes: List<TimetableChangeDto> = emptyList(),
)

@Serializable
data class TimetableChangeDto(
    @SerialName("zmiana")
    val changeType: Int,
    @SerialName("nrLekcji")
    val lessonNumber: Int? = null,
    @SerialName("godzinaOd")
    val timeFrom: String? = null,
    @SerialName("godzinaDo")
    val timeTo: String? = null,
    @SerialName("zajecia")
    val subject: String? = null,
    @SerialName("sala")
    val room: String? = null,
    @SerialName("prowadzacy")
    val teacher: String? = null,
    @SerialName("informacjeNieobecnosc")
    val absenceInformation: String? = null,
)
