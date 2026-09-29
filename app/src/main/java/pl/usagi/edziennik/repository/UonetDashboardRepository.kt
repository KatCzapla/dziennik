package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.network.dashboard.UonetDashboardDataSource
import javax.inject.Inject

class UonetDashboardRepository @Inject constructor(
    private val dataSource: UonetDashboardDataSource,
) : DashboardRepository {
    override suspend fun getInformation(student: Student): List<DashboardInformation> =
        dataSource.getInformation(student.key).map { it.toDomain() }
}
