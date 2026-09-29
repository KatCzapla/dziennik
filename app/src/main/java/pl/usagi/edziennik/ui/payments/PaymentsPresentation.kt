package pl.usagi.edziennik.ui.payments

import pl.usagi.edziennik.domain.BankAccountPayment
import pl.usagi.edziennik.domain.MonthlyPayment
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

fun BigDecimal.formatPln(): String = NumberFormat.getNumberInstance(paymentLocale).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
    roundingMode = RoundingMode.HALF_UP
}.format(this) + " zł"

fun BankAccountPayment.operationAmountLabel(): String? =
    listOfNotNull(operationName?.takeIf { it.isNotBlank() }, amountDue?.formatPln())
        .joinToString(" ").takeIf { it.isNotBlank() }

fun String.formatNrb(): String {
    val digits = replace(" ", "")
    if (digits.length != 26 || digits.any { it !in '0'..'9' }) return this
    return digits.take(2) + " " + digits.drop(2).chunked(4).joinToString(" ")
}

fun MonthlyPayment.chargeLines(): List<Pair<String, String>> = listOfNotNull(
    food?.let { "Wyżywienie" to it.formatPln() },
    stay?.let { "Pobyt" to it.formatPln() },
    correction?.let { "Korekta" to it.formatPln() },
    charge?.let { "Naliczenie" to it.formatPln() },
)

private val paymentLocale = Locale.forLanguageTag("pl-PL")
val paymentDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", paymentLocale)
val paymentMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", paymentLocale)
val paymentSyncFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", paymentLocale)
