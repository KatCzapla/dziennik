package pl.usagi.edziennik.network.messages

import okhttp3.HttpUrl

data class MessagesSessionContext(
    val appUrl: HttpUrl,
    val apiBaseUrl: HttpUrl,
    val appGuid: String,
    val requestVerificationToken: String,
)
