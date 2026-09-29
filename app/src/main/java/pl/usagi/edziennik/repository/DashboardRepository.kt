package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.Student

interface DashboardRepository {
    suspend fun getInformation(student: Student): List<DashboardInformation>
}
