package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PaymentsDto(
    @SerialName("kontaBankowe") val bankAccounts: List<BankAccountPaymentDto>? = null,
    @SerialName("oplaty") val monthlyPayments: List<MonthlyPaymentDto>? = null,
)

@Serializable
data class BankAccountPaymentDto(
    @SerialName("numerKonta") val accountNumber: String? = null,
    @SerialName("tytulPlatnosci") val paymentTitle: String? = null,
    @SerialName("nazwaOperacji") val operationName: String? = null,
    @SerialName("kwotaDoZaplaty") val amountDue: JsonElement? = null,
    @SerialName("informacje") val information: PaymentInformationDto? = null,
    @SerialName("statusPlatnosci") val paymentStatus: Int? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("ukryjPrzyciskZaplac") val hidePayButton: Boolean? = null,
)

@Serializable
data class PaymentInformationDto(
    @SerialName("dataSynchronizacji") val synchronizedAt: String? = null,
    @SerialName("szczegoly") val details: List<PaymentDetailItemDto>? = null,
)

@Serializable
data class PaymentDetailItemDto(
    @SerialName("nazwa") val name: String? = null,
    @SerialName("wartosc") val value: JsonElement? = null,
)

@Serializable
data class MonthlyPaymentDto(
    @SerialName("miesiac") val month: String? = null,
    @SerialName("terminPlatnosci") val paymentDeadline: String? = null,
    @SerialName("wyzywienie") val food: JsonElement? = null,
    @SerialName("pobyt") val stay: JsonElement? = null,
    @SerialName("korekta") val correction: JsonElement? = null,
    @SerialName("naliczenie") val charge: JsonElement? = null,
    @SerialName("szczegoly") val details: List<MonthlyPaymentDetailsDto>? = null,
)

@Serializable
data class MonthlyPaymentDetailsDto(
    @SerialName("nazwa") val name: String? = null,
    @SerialName("informacje") val information: List<MonthlyPaymentEntryDto>? = null,
)

@Serializable
data class MonthlyPaymentEntryDto(
    @SerialName("data") val date: String? = null,
    @SerialName("wartosc") val value: JsonElement? = null,
    @SerialName("info") val info: String? = null,
    @SerialName("info2") val info2: String? = null,
)
