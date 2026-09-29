package pl.usagi.edziennik.ui.payments

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.dto.BankAccountPaymentDto
import pl.usagi.edziennik.dto.MonthlyPaymentDto
import pl.usagi.edziennik.mapper.toDomain
import java.math.BigDecimal

class PaymentsPresentationTest {
    @Test
    fun nrbFormattingGroupsTwentySixDigits() {
        assertEquals("25 1234 1234 1234 1234 1234 1234", "25123412341234123412341234".formatNrb())
    }

    @Test
    fun nrbFormattingHandlesExistingSpaces() {
        val formatted = "25 1234 1234 1234 1234 1234 1234"
        assertEquals(formatted, formatted.formatNrb())
        assertEquals(formatted, " 251234 12341234 123412341234 ".formatNrb())
    }

    @Test
    fun nrbFormattingLeavesNonstandardValuesUnchanged() {
        listOf("", "1234 5678", "PL25123412341234123412341234", "2512341234123412341234123X").forEach {
            assertEquals(it, it.formatNrb())
        }
    }

    @Test
    fun confirmedOverpaymentUsesApiOperationAndUnchangedAmount() {
        val account = BankAccountPaymentDto(
            operationName = "Nadpłata", amountDue = JsonPrimitive("6.00"), status = 3, paymentStatus = 2201,
        ).toDomain()
        assertEquals("Nadpłata 6,00 zł", account.operationAmountLabel())
        assertEquals("Nadpłata 6,00 zł", account.copy(status = 999, paymentStatus = 999).operationAmountLabel())
    }

    @Test
    fun plnFormattingKeepsSignAndZeroAndUsesTwoDecimals() {
        assertEquals("120,00 zł", BigDecimal("120").formatPln())
        assertEquals("-6,00 zł", BigDecimal("-6.00").formatPln())
        assertEquals("0,00 zł", BigDecimal.ZERO.formatPln())
    }

    @Test
    fun chargeLinesOmitNullsButKeepZero() {
        assertEquals(emptyList<Pair<String, String>>(), MonthlyPaymentDto().toDomain().chargeLines())
        assertEquals(
            listOf("Wyżywienie" to "120,00 zł", "Korekta" to "0,00 zł"),
            MonthlyPaymentDto(food = JsonPrimitive("120.00"), correction = JsonPrimitive(0)).toDomain().chargeLines(),
        )
    }
}
