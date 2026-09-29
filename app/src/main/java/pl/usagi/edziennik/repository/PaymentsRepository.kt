package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.Student

interface PaymentsRepository {
    suspend fun getPayments(student: Student): Payments
}
