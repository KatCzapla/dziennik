package pl.usagi.edziennik.mapper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.dto.PaymentsDto
import pl.usagi.edziennik.ui.payments.chargeLines
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class PaymentsMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapsOverpaymentWithoutChangingSignOrInferringStatus() {
        val account = map("""{"kontaBankowe":[{
            "numerKonta":"11112222333344445555666677",
            "tytulPlatnosci":"Opłata za wrzesień",
            "nazwaOperacji":"Nadpłata","kwotaDoZaplaty":6.00,
            "statusPlatnosci":2201,"status":3,"ukryjPrzyciskZaplac":true,
            "platnoscGlobalKey":"unused-key"
        }],"oplaty":[]}""").bankAccounts.single()

        assertEquals("11112222333344445555666677", account.accountNumber)
        assertEquals("Opłata za wrzesień", account.paymentTitle)
        assertEquals("Nadpłata", account.operationName)
        assertEquals(BigDecimal("6.00"), account.amountDue)
        assertEquals(2201, account.paymentStatus)
        assertEquals(3, account.status)
        assertEquals(true, account.hidePayButton)
    }

    @Test
    fun mapsSynchronizationAndAccountDetails() {
        val information = map("""{"kontaBankowe":[{"informacje":{
            "dataSynchronizacji":"2026-09-18T12:34:00+02:00",
            "szczegoly":[{"nazwa":"Saldo","wartosc":"6.00"},{"nazwa":"Wpłaty","wartosc":100}]
        }}]}""").bankAccounts.single().information!!

        assertEquals(LocalDateTime.of(2026, 9, 18, 12, 34), information.synchronizedAt)
        assertEquals(listOf("Saldo", "Wpłaty"), information.details.map { it.name })
        assertEquals(BigDecimal("6.00"), information.details.first().value)
        assertEquals(BigDecimal("100"), information.details.last().value)
    }

    @Test
    fun mapsMonthWithFoodAndPaymentDeadline() {
        val month = map("""{"oplaty":[{
            "miesiac":"2026-09-01T00:00:00+02:00",
            "terminPlatnosci":"2026-09-15T00:00:00+02:00",
            "wyzywienie":120.00,"pobyt":null,"korekta":-6.00,"naliczenie":114.00
        }]}""").monthlyPayments.single()

        assertEquals(YearMonth.of(2026, 9), month.month)
        assertEquals(LocalDate.of(2026, 9, 15), month.paymentDeadline)
        assertEquals(BigDecimal("120.00"), month.food)
        assertNull(month.stay)
        assertEquals(BigDecimal("-6.00"), month.correction)
        assertEquals(BigDecimal("114.00"), month.charge)
    }

    @Test
    fun monthWithAllNullChargesHasNoChargeLines() {
        val month = map("""{"oplaty":[{
            "miesiac":"2026-09-01T00:00:00+02:00","terminPlatnosci":null,
            "wyzywienie":null,"pobyt":null,"korekta":null,"naliczenie":null,"szczegoly":null
        }]}""").monthlyPayments.single()

        assertTrue(month.chargeLines().isEmpty())
        assertNull(month.paymentDeadline)
        assertTrue(month.details.isEmpty())
    }

    @Test
    fun mapsMonthlyDetailGroupsAndAllInformationFields() {
        val groups = map("""{"oplaty":[{"szczegoly":[
            {"nazwa":"Wyżywienie","informacje":[{
                "data":"2026-09-07T00:00:00+02:00","wartosc":12.50,
                "info":"Obiad","info2":"Dodatkowa informacja"
            }]},
            {"nazwa":"Korekty","informacje":[{"data":null,"wartosc":null,"info":null,"info2":null}]}
        ]}]}""").monthlyPayments.single().details

        assertEquals(listOf("Wyżywienie", "Korekty"), groups.map { it.name })
        val entry = groups.first().information.single()
        assertEquals(LocalDate.of(2026, 9, 7), entry.date)
        assertEquals(BigDecimal("12.50"), entry.value)
        assertEquals("Obiad", entry.info)
        assertEquals("Dodatkowa informacja", entry.info2)
        assertNull(groups.last().information.single().value)
    }

    @Test
    fun nullableAccountFieldsAndUnknownStatusesArePreserved() {
        val account = map("""{"kontaBankowe":[{
            "numerKonta":null,"tytulPlatnosci":null,"nazwaOperacji":null,
            "kwotaDoZaplaty":null,"informacje":null,"status":999,"statusPlatnosci":888
        }]}""").bankAccounts.single()
        assertNull(account.accountNumber)
        assertNull(account.paymentTitle)
        assertNull(account.operationName)
        assertNull(account.amountDue)
        assertNull(account.information)
        assertEquals(999, account.status)
        assertEquals(888, account.paymentStatus)
    }

    @Test
    fun emptyBankAccountsAndEmptyMonthsStayEmpty() {
        assertEquals(Payments(), map("""{"kontaBankowe":[],"oplaty":[]}"""))
        assertEquals(Payments(), map("""{"kontaBankowe":null,"oplaty":null}"""))
    }

    private fun map(payload: String): Payments = json.decodeFromString<PaymentsDto>(payload).toDomain()
}
