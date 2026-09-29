package pl.usagi.edziennik.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class Payments(
    val bankAccounts: List<BankAccountPayment> = emptyList(),
    val monthlyPayments: List<MonthlyPayment> = emptyList(),
)

data class BankAccountPayment(
    val accountNumber: String?,
    val paymentTitle: String?,
    val operationName: String?,
    val amountDue: BigDecimal?,
    val information: PaymentInformation?,
    val paymentStatus: Int?,
    val status: Int?,
    val hidePayButton: Boolean?,
)

data class PaymentInformation(
    val synchronizedAt: LocalDateTime?,
    val details: List<PaymentDetailItem>,
)

data class PaymentDetailItem(val name: String?, val value: BigDecimal?)

data class MonthlyPayment(
    val month: YearMonth?,
    val paymentDeadline: LocalDate?,
    val food: BigDecimal?,
    val stay: BigDecimal?,
    val correction: BigDecimal?,
    val charge: BigDecimal?,
    val details: List<MonthlyPaymentDetails>,
)

data class MonthlyPaymentDetails(val name: String?, val information: List<MonthlyPaymentEntry>)

data class MonthlyPaymentEntry(
    val date: LocalDate?,
    val value: BigDecimal?,
    val info: String?,
    val info2: String?,
)
