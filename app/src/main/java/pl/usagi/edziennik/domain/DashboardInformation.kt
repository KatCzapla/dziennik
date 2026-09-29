package pl.usagi.edziennik.domain

import java.time.LocalDate

data class DashboardInformation(
    val subject: String,
    val content: String,
    val date: LocalDate,
)
