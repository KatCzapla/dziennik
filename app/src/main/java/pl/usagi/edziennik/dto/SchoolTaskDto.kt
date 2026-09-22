package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SchoolTaskDto(
    @SerialName("typ")
    val type: Int,
    @SerialName("przedmiotNazwa")
    val subject: String? = null,
    @SerialName("data")
    val date: String,
    @SerialName("hasAttachment")
    val hasAttachment: Boolean = false,
    @SerialName("id")
    val id: Int,
)

@Serializable
data class HomeworkDetailsDto(
    @SerialName("typ")
    val type: Int,
    @SerialName("data")
    val date: String,
    @SerialName("terminOdpowiedzi")
    val responseDeadline: String? = null,
    @SerialName("przedmiotNazwa")
    val subject: String? = null,
    @SerialName("nauczycielImieNazwisko")
    val teacher: String? = null,
    @SerialName("opis")
    val description: String? = null,
    @SerialName("id")
    val id: Int,
)

@Serializable
data class TestDetailsDto(
    @SerialName("typ")
    val type: Int,
    @SerialName("data")
    val date: String,
    @SerialName("przedmiotNazwa")
    val subject: String? = null,
    @SerialName("nauczycielImieNazwisko")
    val teacher: String? = null,
    @SerialName("opis")
    val description: String? = null,
    @SerialName("id")
    val id: Int,
)
