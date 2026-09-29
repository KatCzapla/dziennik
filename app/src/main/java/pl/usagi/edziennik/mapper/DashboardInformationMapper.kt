package pl.usagi.edziennik.mapper

import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.time.parseApiLocalDate
import pl.usagi.edziennik.dto.DashboardInformationDto

fun DashboardInformationDto.toDomain(): DashboardInformation = DashboardInformation(
    subject = subject,
    content = content,
    date = parseApiLocalDate(date),
)
