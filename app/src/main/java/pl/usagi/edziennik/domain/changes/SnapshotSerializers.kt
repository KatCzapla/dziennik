package pl.usagi.edziennik.domain.changes

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pl.usagi.edziennik.domain.AttendanceStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

object AttendanceStatusSerializer : KSerializer<AttendanceStatus> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AttendanceStatus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AttendanceStatus) {
        val code = when (value) {
            AttendanceStatus.Present -> "1"
            AttendanceStatus.Absent -> "2"
            AttendanceStatus.ExcusedAbsence -> "3"
            AttendanceStatus.Late -> "4"
            AttendanceStatus.ExcusedLate -> "5"
            AttendanceStatus.SchoolRelatedAbsence -> "6"
            AttendanceStatus.Released -> "7"
            is AttendanceStatus.Unknown -> "unknown:${value.rawValue ?: "null"}"
        }
        encoder.encodeString(code)
    }

    override fun deserialize(decoder: Decoder): AttendanceStatus = when (val code = decoder.decodeString()) {
        "1" -> AttendanceStatus.Present
        "2" -> AttendanceStatus.Absent
        "3" -> AttendanceStatus.ExcusedAbsence
        "4" -> AttendanceStatus.Late
        "5" -> AttendanceStatus.ExcusedLate
        "6" -> AttendanceStatus.SchoolRelatedAbsence
        "7" -> AttendanceStatus.Released
        else -> {
            require(code.startsWith("unknown:")) { "Unexpected attendance status in snapshot" }
            AttendanceStatus.Unknown(code.removePrefix("unknown:").takeUnless { it == "null" }?.toInt())
        }
    }
}
