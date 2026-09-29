package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DashboardInformationDto(
    @SerialName("temat") val subject: String,
    @SerialName("tresc") val content: String,
    @SerialName("data") val date: String,
)
