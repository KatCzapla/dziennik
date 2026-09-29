package pl.usagi.edziennik.mapper

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pl.usagi.edziennik.domain.BankAccountPayment
import pl.usagi.edziennik.domain.MonthlyPayment
import pl.usagi.edziennik.domain.MonthlyPaymentDetails
import pl.usagi.edziennik.domain.MonthlyPaymentEntry
import pl.usagi.edziennik.domain.PaymentDetailItem
import pl.usagi.edziennik.domain.PaymentInformation
import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.time.parseApiLocalDate
import pl.usagi.edziennik.domain.time.parseApiLocalDateTime
import pl.usagi.edziennik.dto.BankAccountPaymentDto
import pl.usagi.edziennik.dto.MonthlyPaymentDto
import pl.usagi.edziennik.dto.PaymentsDto
import java.math.BigDecimal
import java.time.YearMonth

fun PaymentsDto.toDomain(): Payments = Payments(
    bankAccounts = bankAccounts.orEmpty().map { it.toDomain() },
    monthlyPayments = monthlyPayments.orEmpty().map { it.toDomain() },
)

fun BankAccountPaymentDto.toDomain(): BankAccountPayment = BankAccountPayment(
    accountNumber = accountNumber,
    paymentTitle = paymentTitle,
    operationName = operationName,
    amountDue = amountDue.toPaymentAmount(),
    information = information?.let { info ->
        PaymentInformation(
            synchronizedAt = info.synchronizedAt?.let(::parseApiLocalDateTime),
            details = info.details.orEmpty().map { PaymentDetailItem(it.name, it.value.toPaymentAmount()) },
        )
    },
    paymentStatus = paymentStatus,
    status = status,
    hidePayButton = hidePayButton,
)

fun MonthlyPaymentDto.toDomain(): MonthlyPayment = MonthlyPayment(
    month = month?.let { YearMonth.from(parseApiLocalDate(it)) },
    paymentDeadline = paymentDeadline?.let(::parseApiLocalDate),
    food = food.toPaymentAmount(),
    stay = stay.toPaymentAmount(),
    correction = correction.toPaymentAmount(),
    charge = charge.toPaymentAmount(),
    details = details.orEmpty().map { group ->
        MonthlyPaymentDetails(
            name = group.name,
            information = group.information.orEmpty().map { entry ->
                MonthlyPaymentEntry(
                    date = entry.date?.let(::parseApiLocalDate),
                    value = entry.value.toPaymentAmount(),
                    info = entry.info,
                    info2 = entry.info2,
                )
            },
        )
    },
)

private fun JsonElement?.toPaymentAmount(): BigDecimal? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> contentOrNull?.let(::BigDecimal)
    else -> error("Payment amount must be a number or null.")
}
