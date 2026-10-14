package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SchoolDayOffDto(
    @SerialName("nazwa") val name: String? = null,
    @SerialName("dataOd") val from: String,
    @SerialName("dataDo") val to: String,
    @SerialName("wszystkieSkladowe") val allUnits: Boolean = false,
    @SerialName("jednostkiSkladowe") val units: List<DayOffUnitDto> = emptyList(),
)

@Serializable
data class DayOffUnitDto(
    @SerialName("id") val id: Int,
    @SerialName("nazwa") val name: String? = null,
)
