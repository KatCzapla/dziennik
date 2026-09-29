package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.network.payments.UonetPaymentsDataSource
import javax.inject.Inject

class UonetPaymentsRepository @Inject constructor(private val dataSource: UonetPaymentsDataSource) : PaymentsRepository {
    override suspend fun getPayments(student: Student): Payments = dataSource.getPayments(student.key).toDomain()
}
